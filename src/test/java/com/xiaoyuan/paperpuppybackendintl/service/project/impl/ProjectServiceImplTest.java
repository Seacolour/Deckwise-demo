package com.xiaoyuan.paperpuppybackendintl.service.project.impl;

import com.xiaoyuan.paperpuppybackendintl.model.dto.file.ProjectFileLinkView;
import com.xiaoyuan.paperpuppybackendintl.model.dto.project.ProjectDTO;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Project;
import com.xiaoyuan.paperpuppybackendintl.model.enums.DocumentType;
import com.xiaoyuan.paperpuppybackendintl.repository.DocumentRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.FileAccessGrantRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.PdfDerivedMediaAssetCacheRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectFileLinkRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectRepository;
import com.xiaoyuan.paperpuppybackendintl.service.file.FileGarbageCollectionService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceImplTest {



    @Test
    void deleteProjectDeletesProjectDocumentsFileLinksAndGrants() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectFileLinkRepository projectFileLinkRepository = mock(ProjectFileLinkRepository.class);
        FileAccessGrantRepository fileAccessGrantRepository = mock(FileAccessGrantRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        FileGarbageCollectionService fileGarbageCollectionService = mock(FileGarbageCollectionService.class);
        PdfDerivedMediaAssetCacheRepository pdfDerivedMediaAssetCacheRepository =
                mock(PdfDerivedMediaAssetCacheRepository.class);
        ProjectServiceImpl service = new ProjectServiceImpl(
                projectRepository,
                projectFileLinkRepository,
                fileAccessGrantRepository,
                documentRepository,
                fileGarbageCollectionService,
                pdfDerivedMediaAssetCacheRepository);

        Project project = new Project();
        project.setId(100L);
        project.setOwnerId(7L);
        project.setDeleted(false);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(projectFileLinkRepository.listProjectFiles(100L)).thenReturn(List.of());

        service.deleteProject(7L, 100L);

        verify(documentRepository).softDeleteByProjectId(eq(100L), any(), eq(7L));
        verify(projectFileLinkRepository).softDeleteByProjectId(100L);
        verify(pdfDerivedMediaAssetCacheRepository).softDeleteProjectLinksByProjectId(100L);
        verify(fileAccessGrantRepository).softDeleteByProjectId(7L, 100L);
        verify(documentRepository).hardDeleteByProjectId(100L);
        verify(fileGarbageCollectionService, never()).enqueueProjectDeleteCandidateAfterCommit(any(), any(), any());
        verify(projectRepository).softDelete(100L);
    }

    @Test
    void deleteProjectEnqueuesGcCandidatesAfterSoftUnlink() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectFileLinkRepository projectFileLinkRepository = mock(ProjectFileLinkRepository.class);
        FileAccessGrantRepository fileAccessGrantRepository = mock(FileAccessGrantRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        FileGarbageCollectionService fileGarbageCollectionService = mock(FileGarbageCollectionService.class);
        ProjectServiceImpl service = new ProjectServiceImpl(
                projectRepository,
                projectFileLinkRepository,
                fileAccessGrantRepository,
                documentRepository,
                fileGarbageCollectionService,
                mock(PdfDerivedMediaAssetCacheRepository.class));

        Project project = new Project();
        project.setId(100L);
        project.setOwnerId(7L);
        project.setDeleted(false);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        ProjectFileLinkView upload = linkedFile(201L, "bytes", "bytes-key");
        ProjectFileLinkView generated = linkedFile(202L, "generated", "generated-key");
        ProjectFileLinkView derived = linkedFile(203L, "derived", "derived-key");
        ProjectFileLinkView publish = linkedFile(204L, "publish", "publish-key");
        ProjectFileLinkView copy = linkedFile(205L, "copy", "copy-key");
        when(projectFileLinkRepository.listProjectFiles(100L)).thenReturn(List.of(upload, generated, derived, publish, copy));

        service.deleteProject(7L, 100L);

        verify(fileGarbageCollectionService, times(1))
                .enqueueProjectDeleteCandidateAfterCommit(7L, 201L, 100L);
        verify(fileGarbageCollectionService, times(1))
                .enqueueProjectDeleteCandidateAfterCommit(7L, 202L, 100L);
        verify(fileGarbageCollectionService, times(1))
                .enqueueProjectDeleteCandidateAfterCommit(7L, 203L, 100L);
        verify(fileGarbageCollectionService, times(1))
                .enqueueProjectDeleteCandidateAfterCommit(7L, 204L, 100L);
        verify(fileGarbageCollectionService, times(1))
                .enqueueProjectDeleteCandidateAfterCommit(7L, 205L, 100L);
        verify(documentRepository).hardDeleteByProjectId(100L);
    }

    private static ProjectFileLinkView linkedFile(Long fileId, String sourceType, String cacheKey) {
        ProjectFileLinkView view = new ProjectFileLinkView();
        view.setFileId(fileId);
        view.setSourceType(sourceType);
        view.setCacheKey(cacheKey);
        return view;
    }

    private static ProjectServiceImpl newService(ProjectRepository projectRepository) {
        return new ProjectServiceImpl(
                projectRepository,
                mock(ProjectFileLinkRepository.class),
                mock(FileAccessGrantRepository.class),
                mock(DocumentRepository.class),
                mock(FileGarbageCollectionService.class),
                mock(PdfDerivedMediaAssetCacheRepository.class));
    }
}
