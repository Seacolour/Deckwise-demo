package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.Project;


import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProjectRepository {
    Optional<Project> findById(Long id);

    List<Project> listByOwner(Long ownerId, Boolean archived);

    List<Project> listByOwner(Long ownerId, Boolean archived, int offset, int limit, String sortField,
            String sortOrder);

    int countByOwner(Long ownerId, Boolean archived);

    /**
     * 当前用户拥有的项目列表。
     */
    List<Project> listByUser(Long userId, Boolean archived, int offset, int limit, String sortField,
            String sortOrder);

    int countByUser(Long userId, Boolean archived);

    List<Project> listEmptyProjectShellGcCandidates(
            int limit,
            OffsetDateTime updatedBefore,
            long maxCreatedUpdatedDeltaSeconds);

    Project save(Project project);

    /**
     * 仅当当前项目名属于 expectedNames 时才更新为 newName，避免覆盖用户手动命名。
     */
    boolean updateNameIfIn(Long projectId, Long ownerId, Collection<String> expectedNames, String newName);

    boolean softDelete(Long projectId);

    boolean updateLastActiveAt(Long projectId, OffsetDateTime time);
}
