package com.xiaoyuan.paperpuppybackendintl.service.file;

import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FileGarbageCollectionResult;

import java.time.OffsetDateTime;

public interface FileGarbageCollectionService {

    String JOB_TYPE = "file.gc";

    void enqueueCandidateAfterCommit(Long ownerUserId, Long fileId, String reason);

    void enqueueProjectDeleteCandidateAfterCommit(Long ownerUserId, Long fileId, Long projectId);

    int enqueueOrphanCandidates(int limit, OffsetDateTime updatedBefore);

    FileGarbageCollectionResult tryGarbageCollect(Long ownerUserId, Long fileId, String reason);
}
