package com.xiaoyuan.paperpuppybackendintl.service.file.impl;

import com.xiaoyuan.paperpuppybackendintl.config.FileGarbageCollectionProperties;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FileGarbageCollectionResult;
import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;
import com.xiaoyuan.paperpuppybackendintl.repository.FilePurgeRepository;
import com.xiaoyuan.paperpuppybackendintl.service.file.FileGarbageCollectionService;
import com.xiaoyuan.paperpuppybackendintl.service.jobs.BackgroundJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileGarbageCollectionServiceImpl implements FileGarbageCollectionService {

    private final FilePurgeRepository filePurgeRepository;
    private final ObjectProvider<BackgroundJobService> backgroundJobServiceProvider;
    private final R2Client r2Client;
    private final FileGarbageCollectionProperties properties;

    @Override
    public void enqueueCandidateAfterCommit(Long ownerUserId, Long fileId, String reason) {
        enqueueCandidateAfterCommit(ownerUserId, fileId, reason, null, false);
    }

    @Override
    public void enqueueProjectDeleteCandidateAfterCommit(Long ownerUserId, Long fileId, Long projectId) {
        String reason = projectId == null || projectId <= 0
                ? "project_delete"
                : "project_delete:" + projectId;
        OffsetDateTime nextRunAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(safeProjectDeleteGraceSeconds());
        enqueueCandidateAfterCommit(ownerUserId, fileId, reason, nextRunAt, true);
    }

    private void enqueueCandidateAfterCommit(Long ownerUserId,
            Long fileId,
            String reason,
            OffsetDateTime nextRunAt,
            boolean extendPending) {
        if (ownerUserId == null || ownerUserId <= 0 || fileId == null || fileId <= 0) {
            return;
        }
        BackgroundJobService backgroundJobService = backgroundJobServiceProvider.getObject();
        FileGcJobPayload payload = new FileGcJobPayload(ownerUserId, fileId, normalizeReason(reason));
        if (nextRunAt == null && !extendPending) {
            backgroundJobService.enqueueAfterCommit(JOB_TYPE, dedupKey(ownerUserId, fileId), payload, ownerUserId);
            return;
        }
        backgroundJobService.enqueueAfterCommit(
                JOB_TYPE,
                dedupKey(ownerUserId, fileId),
                payload,
                ownerUserId,
                nextRunAt,
                extendPending);
    }

    @Override
    public int enqueueOrphanCandidates(int limit, OffsetDateTime updatedBefore) {
        List<FilePurgeRepository.FileGcCandidate> candidates =
                filePurgeRepository.listOrphanGarbageCollectionCandidates(
                        limit,
                        updatedBefore,
                        derivedCacheRetainedAfter());
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }
        int enqueued = 0;
        for (FilePurgeRepository.FileGcCandidate candidate : candidates) {
            if (candidate == null || candidate.ownerUserId() == null || candidate.fileId() == null) {
                continue;
            }
            enqueueCandidateAfterCommit(
                    candidate.ownerUserId(),
                    candidate.fileId(),
                    "orphan_scanner:" + safeValue(candidate.sourceType()) + ":" + safeValue(candidate.status()));
            enqueued++;
        }
        return enqueued;
    }

    @Override
    @Transactional
    public FileGarbageCollectionResult tryGarbageCollect(Long ownerUserId, Long fileId, String reason) {
        if (ownerUserId == null || ownerUserId <= 0 || fileId == null || fileId <= 0) {
            return result(ownerUserId, fileId, reason, "invalid_candidate", false, 0, Map.of());
        }

        ObjectStorageFile file = filePurgeRepository.findOwnedFileForUpdate(ownerUserId, fileId)
                .orElse(null);
        if (file == null) {
            return result(ownerUserId, fileId, reason, "missing", false, 0, Map.of());
        }
        if (Boolean.TRUE.equals(file.getDeleted())) {
            return result(ownerUserId, fileId, reason, "already_deleted", false, 0, Map.of());
        }

        FilePurgeRepository.FilePurgeUsageSnapshot usage = collectUsageSnapshot(fileId);
        Map<String, Long> blockers = blockers(usage);
        if (!blockers.isEmpty()) {
            log.info("[FileGC] skip referenced file: ownerUserId={}, fileId={}, reason={}, blockers={}",
                    ownerUserId,
                    fileId,
                    reason,
                    blockers);
            return result(ownerUserId, fileId, reason, "referenced", false, 0, blockers);
        }

        List<StoredObjectRef> objectRefs = collectPurgeObjectRefs(file, filePurgeRepository.listDerivativeObjectKeys(fileId));
        filePurgeRepository.revokeAccessGrants(fileId);
        filePurgeRepository.softDeleteTextFragments(fileId);
        filePurgeRepository.softDeleteDerivatives(fileId);
        filePurgeRepository.deleteInactiveReferencesForHardDelete(fileId);

        boolean deleted = filePurgeRepository.hardDeleteFileRecord(ownerUserId, fileId);
        if (!deleted) {
            throw new IllegalStateException("File GC hard delete failed, fileId=" + fileId);
        }

        deleteR2ObjectsAfterCommit(objectRefs);
        log.info("[FileGC] purged file: ownerUserId={}, fileId={}, reason={}, objectCount={}",
                ownerUserId,
                fileId,
                reason,
                objectRefs.size());
        return result(ownerUserId, fileId, reason, "purged", true, objectRefs.size(), Map.of());
    }

    private void deleteR2ObjectsAfterCommit(List<StoredObjectRef> objectRefs) {
        if (objectRefs == null || objectRefs.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteR2ObjectsBestEffort(objectRefs);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteR2ObjectsBestEffort(objectRefs);
            }
        });
    }

    private void deleteR2ObjectsBestEffort(List<StoredObjectRef> objectRefs) {
        for (StoredObjectRef ref : objectRefs) {
            if (ref == null || ref.objectKey() == null || ref.objectKey().isBlank()) {
                continue;
            }
            try {
                r2Client.delete(ref.channel(), ref.objectKey());
            } catch (Exception e) {
                log.warn("[FileGC] failed to delete R2 object, channel={}, key={}",
                        ref.channel().value(), ref.objectKey(), e);
            }
        }
    }

    private List<StoredObjectRef> collectPurgeObjectRefs(ObjectStorageFile file, List<String> derivativeKeys) {
        LinkedHashSet<StoredObjectRef> refs = new LinkedHashSet<>();
        if (file != null && file.getObjectKey() != null && !file.getObjectKey().isBlank()) {
            refs.add(new StoredObjectRef(resolveStorageChannel(file), file.getObjectKey().trim()));
        }
        if (derivativeKeys != null) {
            derivativeKeys.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(key -> new StoredObjectRef(StorageChannel.PUBLIC, key))
                    .forEach(refs::add);
        }
        return new ArrayList<>(refs);
    }

    private StorageChannel resolveStorageChannel(ObjectStorageFile file) {
        return file != null && StorageChannel.isPrivate(file.getStorageChannel())
                ? StorageChannel.PRIVATE
                : StorageChannel.PUBLIC;
    }

    private Map<String, Long> blockers(FilePurgeRepository.FilePurgeUsageSnapshot usage) {
        if (usage == null || !usage.hasBlockers()) {
            return Map.of();
        }
        Map<String, Long> blockers = new LinkedHashMap<>();
        addBlocker(blockers, "activeProjectLinks", usage.activeProjectLinks());
        addBlocker(blockers, "activeDocuments", usage.activeDocuments());
        addBlocker(blockers, "activeDocumentVersions", usage.activeDocumentVersions());
        addBlocker(blockers, "activeDerivedMediaSourceReferences", usage.activeDerivedMediaSourceReferences());
        addBlocker(blockers, "activeDerivedMediaFileReferences", usage.activeDerivedMediaFileReferences());
        addBlocker(blockers, "retainedDerivedMediaCacheReferences", usage.retainedDerivedMediaCacheReferences());
        return blockers;
    }

    private void addBlocker(Map<String, Long> blockers, String key, long count) {
        if (count > 0) {
            blockers.put(key, count);
        }
    }

    private FileGarbageCollectionResult result(Long ownerUserId,
            Long fileId,
            String reason,
            String status,
            boolean purged,
            int scheduledObjectDeleteCount,
            Map<String, Long> blockers) {
        return new FileGarbageCollectionResult(
                ownerUserId,
                fileId,
                normalizeReason(reason),
                status,
                purged,
                scheduledObjectDeleteCount,
                blockers == null ? Map.of() : blockers);
    }

    private static String dedupKey(Long ownerUserId, Long fileId) {
        return JOB_TYPE + ":" + ownerUserId + ":" + fileId;
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unspecified";
        }
        return reason.trim();
    }

    private static String safeValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private long safeProjectDeleteGraceSeconds() {
        return Math.max(0, properties == null ? 86_400 : properties.getProjectDeleteGraceSeconds());
    }

    private FilePurgeRepository.FilePurgeUsageSnapshot collectUsageSnapshot(Long fileId) {
        FilePurgeRepository.FilePurgeUsageSnapshot usage = filePurgeRepository.collectUsageSnapshot(fileId);
        long retained = filePurgeRepository.countRetainedDerivedMediaCacheReferences(fileId, derivedCacheRetainedAfter());
        return (usage == null
                ? new FilePurgeRepository.FilePurgeUsageSnapshot(0, 0, 0, 0, 0)
                : usage)
                .withRetainedDerivedMediaCacheReferences(retained);
    }

    private OffsetDateTime derivedCacheRetainedAfter() {
        return OffsetDateTime.now(ZoneOffset.UTC)
                .minusSeconds(safeDerivedCacheRetentionSeconds());
    }

    private long safeDerivedCacheRetentionSeconds() {
        return Math.max(0, properties == null ? 604_800 : properties.getDerivedCacheRetentionSeconds());
    }

    public record FileGcJobPayload(Long ownerUserId, Long fileId, String reason) {
    }

    private record StoredObjectRef(StorageChannel channel, String objectKey) {
    }
}
