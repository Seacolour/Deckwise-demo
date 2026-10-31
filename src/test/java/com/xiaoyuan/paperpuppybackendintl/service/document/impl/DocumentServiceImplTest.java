package com.xiaoyuan.paperpuppybackendintl.service.document.impl;

import com.xiaoyuan.paperpuppybackendintl.model.dto.document.DocumentCreateRequest;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Document;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Project;
import com.xiaoyuan.paperpuppybackendintl.model.enums.DocumentType;
import com.xiaoyuan.paperpuppybackendintl.repository.DocumentRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.DocumentVersionRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectRepository;
import com.xiaoyuan.paperpuppybackendintl.service.file.FileService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceImplTest {

    @Test
    void createDefaultsToMarkdownAndPreservesDraft() {
        DocumentRepository documents = mock(DocumentRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        DocumentServiceImpl service = new DocumentServiceImpl(
                documents, mock(DocumentVersionRepository.class), projects, mock(FileService.class));
        Project project = new Project();
        project.setId(100L);
        project.setOwnerId(7L);
        project.setDeleted(false);
        when(projects.findById(100L)).thenReturn(Optional.of(project));
        when(documents.insert(any(Document.class))).thenAnswer(call -> {
            Document document = call.getArgument(0);
            document.setId(51L);
            return document;
        });

        DocumentCreateRequest request = new DocumentCreateRequest();
        request.setProjectId(100L);
        request.setTitle("Notes");
        request.setDraftContent("# Hello");
        var created = service.create(7L, request);

        assertEquals(51L, created.getId());
        assertEquals(DocumentType.MARKDOWN, created.getDocumentType());
        assertEquals("# Hello", created.getDraftContent());
        verify(documents).insert(any(Document.class));
    }
}
