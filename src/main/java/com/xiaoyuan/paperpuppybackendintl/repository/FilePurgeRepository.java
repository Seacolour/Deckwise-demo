package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface FilePurgeRepository {

    Optional<ObjectStorageFile> findOwnedFileForUpdate(Long userId, Long fileId);

    boolean existsProjectHistoryLink(Long projectId, Long fileId);

    FilePurgeUsageSnapshot collectUsageSnapshot(Long fileId);

    long countRetainedDerivedMediaCacheReferences(Long fileId, OffsetDateTime retainedAfter);

    List<FileGcCandidate> listOrphanGarbageCollectionCandidates(
            int limit,
            OffsetDateTime updatedBefore,
            OffsetDateTime retainedDerivedCacheUpdatedAfter);

    List<String> listDerivativeObjectKeys(Long fileId);

    int revokeAccessGrants(Long fileId);

    int softDeleteTextFragments(Long fileId);

    int softDeleteDerivatives(Long fileId);

    int deleteInactiveReferencesForHardDelete(Long fileId);

    boolean hardDeleteFileRecord(Long userId, Long fileId);

    record FilePurgeUsageSnapshot(
            long activeProjectLinks,
            long activeDocuments,
            long activeDocumentVersions,
            long activeDerivedMediaSourceReferences,
            long activeDerivedMediaFileReferences,
            long retainedDerivedMediaCacheReferences) {

        public FilePurgeUsageSnapshot(
                long activeProjectLinks,
                long activeDocuments,
                long activeDocumentVersions,
                long activeDerivedMediaSourceReferences,
                long activeDerivedMediaFileReferences) {
            this(
                    activeProjectLinks,
                    activeDocuments,
                    activeDocumentVersions,
                    activeDerivedMediaSourceReferences,
                    activeDerivedMediaFileReferences,
                    0);
        }

        public boolean hasBlockers() {
            return activeProjectLinks > 0
                    || activeDocuments > 0
                    || activeDocumentVersions > 0
                    || activeDerivedMediaSourceReferences > 0
                    || activeDerivedMediaFileReferences > 0
                    || retainedDerivedMediaCacheReferences > 0;
        }

        public FilePurgeUsageSnapshot withRetainedDerivedMediaCacheReferences(long count) {
            return new FilePurgeUsageSnapshot(
                    activeProjectLinks,
                    activeDocuments,
                    activeDocumentVersions,
                    activeDerivedMediaSourceReferences,
                    activeDerivedMediaFileReferences,
                    count);
        }
    }

    record FileGcCandidate(
            Long ownerUserId,
            Long fileId,
            String sourceType,
            String status) {
    }
}
