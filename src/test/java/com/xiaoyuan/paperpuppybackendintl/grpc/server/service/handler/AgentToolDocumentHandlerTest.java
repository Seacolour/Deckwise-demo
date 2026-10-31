package com.xiaoyuan.paperpuppybackendintl.grpc.server.service.handler;

import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.GetDocumentRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.GetDocumentResponse;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.UpdateDocumentDraftRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.document.DocumentDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.document.DocumentDraftUpdateRequest;
import com.xiaoyuan.paperpuppybackendintl.repository.FileTextFragmentRepository;
import com.xiaoyuan.paperpuppybackendintl.service.document.DocumentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolDocumentHandlerTest {

    @Test
    void getDocumentReturnsPublishedContentWhenReadDraftIsFalse() {
        DocumentService documentService = mock(DocumentService.class);
        FileTextFragmentRepository fileTextFragmentRepository = mock(FileTextFragmentRepository.class);
        AgentToolDocumentHandler handler = new AgentToolDocumentHandler(documentService, fileTextFragmentRepository);

        DocumentDTO document = new DocumentDTO();
        document.setId(11L);
        document.setProjectId(22L);
        document.setTitle("Published doc");
        document.setCurrentFileId(33L);
        document.setVersion(7);
        document.setUpdatedAt(OffsetDateTime.parse("2026-04-01T10:15:30+08:00"));
        when(documentService.get(1L, 11L, false)).thenReturn(document);
        when(fileTextFragmentRepository.countByFileId(33L)).thenReturn(2);
        when(fileTextFragmentRepository.listFragmentContents(33L, 2)).thenReturn(List.of("part-1", "part-2"));

        GetDocumentResponse response = handler.getDocument(GetDocumentRequest.newBuilder()
                .setUserId(1L)
                .setProjectId(22L)
                .setDocumentId(11L)
                .setReadDraft(false)
                .build());

        assertEquals("Published doc", response.getTitle());
        assertEquals("part-1\n\npart-2", response.getContent());
        assertEquals(7, response.getVersion());
        verify(fileTextFragmentRepository).countByFileId(33L);
        verify(fileTextFragmentRepository).listFragmentContents(33L, 2);
    }

    @Test
    void updateDocumentDraftUsesExpectedVersionFromRequest() {
        DocumentService documentService = mock(DocumentService.class);
        FileTextFragmentRepository fileTextFragmentRepository = mock(FileTextFragmentRepository.class);
        AgentToolDocumentHandler handler = new AgentToolDocumentHandler(documentService, fileTextFragmentRepository);

        DocumentDTO current = new DocumentDTO();
        current.setId(11L);
        current.setProjectId(22L);
        current.setVersion(8);
        when(documentService.get(1L, 11L, false)).thenReturn(current);

        DocumentDTO updated = new DocumentDTO();
        updated.setId(11L);
        updated.setVersion(6);
        when(documentService.updateDraft(eq(1L), eq(11L), any(DocumentDraftUpdateRequest.class))).thenReturn(updated);

        handler.updateDocumentDraft(UpdateDocumentDraftRequest.newBuilder()
                .setUserId(1L)
                .setProjectId(22L)
                .setDocumentId(11L)
                .setContent("new draft")
                .setExpectedVersion(5)
                .build());

        ArgumentCaptor<DocumentDraftUpdateRequest> requestCaptor =
                ArgumentCaptor.forClass(DocumentDraftUpdateRequest.class);
        verify(documentService).updateDraft(eq(1L), eq(11L), requestCaptor.capture());
        assertEquals(5, requestCaptor.getValue().getVersion());
        assertEquals("new draft", requestCaptor.getValue().getDraftContent());
    }
}
