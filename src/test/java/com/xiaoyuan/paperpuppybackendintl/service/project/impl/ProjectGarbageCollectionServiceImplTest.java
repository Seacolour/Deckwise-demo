package com.xiaoyuan.paperpuppybackendintl.service.project.impl;

import com.xiaoyuan.paperpuppybackendintl.model.entity.Project;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectRepository;
import com.xiaoyuan.paperpuppybackendintl.service.project.ProjectService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectGarbageCollectionServiceImplTest {



    @Test
    void collectEmptyProjectShellsDeletesCandidatesViaProjectService() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectService projectService = mock(ProjectService.class);
        ProjectGarbageCollectionServiceImpl service = new ProjectGarbageCollectionServiceImpl(
                projectRepository,
                projectService);
        OffsetDateTime cutoff = OffsetDateTime.parse("2026-07-06T00:00:00Z");
        Project first = project(100L, 7L);
        Project second = project(101L, 8L);
        when(projectRepository.listEmptyProjectShellGcCandidates(
                50,
                cutoff,
                300)).thenReturn(List.of(first, second));

        int deleted = service.collectEmptyProjectShells(50, cutoff, 300);

        assertEquals(2, deleted);
        verify(projectService).deleteProject(7L, 100L);
        verify(projectService).deleteProject(8L, 101L);
    }

    @Test
    void collectEmptyProjectShellsContinuesWhenOneDeleteFails() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectService projectService = mock(ProjectService.class);
        ProjectGarbageCollectionServiceImpl service = new ProjectGarbageCollectionServiceImpl(
                projectRepository,
                projectService);
        OffsetDateTime cutoff = OffsetDateTime.parse("2026-07-06T00:00:00Z");
        Project first = project(100L, 7L);
        Project second = project(101L, 8L);
        when(projectRepository.listEmptyProjectShellGcCandidates(
                50,
                cutoff,
                300)).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("boom")).when(projectService).deleteProject(7L, 100L);

        int deleted = service.collectEmptyProjectShells(50, cutoff, 300);

        assertEquals(1, deleted);
        verify(projectService).deleteProject(7L, 100L);
        verify(projectService).deleteProject(8L, 101L);
    }

    @Test
    void collectEmptyProjectShellsSkipsInvalidRequest() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectService projectService = mock(ProjectService.class);
        ProjectGarbageCollectionServiceImpl service = new ProjectGarbageCollectionServiceImpl(
                projectRepository,
                projectService);

        int deleted = service.collectEmptyProjectShells(0, null, 300);

        assertEquals(0, deleted);
        verifyNoInteractions(projectRepository, projectService);
    }

    private static Project project(Long projectId, Long ownerId) {
        Project project = new Project();
        project.setId(projectId);
        project.setOwnerId(ownerId);
        project.setName("New Project");
        project.setDeleted(false);
        return project;
    }
}
