package com.xiaoyuan.paperpuppybackendintl.service.project.impl;

import com.xiaoyuan.paperpuppybackendintl.model.entity.Project;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectRepository;
import com.xiaoyuan.paperpuppybackendintl.service.project.ProjectGarbageCollectionService;
import com.xiaoyuan.paperpuppybackendintl.service.project.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectGarbageCollectionServiceImpl implements ProjectGarbageCollectionService {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;

    @Override
    public int collectEmptyProjectShells(
            int limit,
            OffsetDateTime updatedBefore,
            long maxCreatedUpdatedDeltaSeconds) {
        if (limit <= 0 || updatedBefore == null) {
            return 0;
        }

        List<Project> candidates = projectRepository.listEmptyProjectShellGcCandidates(
                limit,
                updatedBefore,
                maxCreatedUpdatedDeltaSeconds);
        return deleteCandidates(candidates, "empty project shell");
    }

    private int deleteCandidates(List<Project> candidates, String candidateType) {
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }

        int deleted = 0;
        for (Project candidate : candidates) {
            if (candidate == null || candidate.getId() == null || candidate.getOwnerId() == null) {
                continue;
            }
            try {
                projectService.deleteProject(candidate.getOwnerId(), candidate.getId());
                deleted++;
                log.info("[ProjectGC] deleted {}: projectId={}, ownerId={}",
                        candidateType,
                        candidate.getId(),
                        candidate.getOwnerId());
            } catch (Exception e) {
                log.warn("[ProjectGC] failed to delete {} candidate project: projectId={}, ownerId={}, err={}",
                        candidateType,
                        candidate.getId(),
                        candidate.getOwnerId(),
                        e.toString());
                log.debug("[ProjectGC] failed {} candidate project delete stacktrace: projectId={}, ownerId={}",
                        candidateType,
                        candidate.getId(),
                        candidate.getOwnerId(),
                        e);
            }
        }
        return deleted;
    }
}
