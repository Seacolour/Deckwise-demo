package com.xiaoyuan.paperpuppybackendintl.service.project;

import java.time.OffsetDateTime;

public interface ProjectGarbageCollectionService {

    int collectEmptyProjectShells(
            int limit,
            OffsetDateTime updatedBefore,
            long maxCreatedUpdatedDeltaSeconds);
}
