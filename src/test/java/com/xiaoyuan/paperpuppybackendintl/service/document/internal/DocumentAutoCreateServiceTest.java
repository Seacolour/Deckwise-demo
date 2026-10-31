package com.xiaoyuan.paperpuppybackendintl.service.document.internal;

import com.xiaoyuan.paperpuppybackendintl.config.openai.OpenAiDocumentSummaryProperties;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Document;
import com.xiaoyuan.paperpuppybackendintl.model.enums.DocumentType;
import com.xiaoyuan.paperpuppybackendintl.repository.DocumentRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.DocumentVersionRepository;
import com.xiaoyuan.paperpuppybackendintl.service.document.summary.DocumentSummaryJobQueue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentAutoCreateServiceTest {

    @Test
    void uploadedMarkdownCreatesEditableDocumentAndInitialVersion() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
        OpenAiDocumentSummaryProperties summary = mock(OpenAiDocumentSummaryProperties.class);
        DocumentAutoCreateService service = new DocumentAutoCreateService(
                documents, versions, mock(DocumentSummaryJobQueue.class), summary);
        when(documents.findByProjectAndCurrentFileId(100L, 61L)).thenReturn(Optional.empty());
        when(summary.enabled()).thenReturn(false);
        when(documents.insert(any(Document.class))).thenAnswer(call -> {
            Document document = call.getArgument(0);
            document.setId(88L);
            return document;
        });

        Long documentId = service.ensureCreatedForUploadedMarkdown(
                7L, 100L, 61L, "notes.md", "# Notes");

        assertEquals(88L, documentId);
        ArgumentCaptor<Document> inserted = ArgumentCaptor.forClass(Document.class);
        verify(documents).insert(inserted.capture());
        assertEquals("notes", inserted.getValue().getTitle());
        assertEquals(DocumentType.MARKDOWN, inserted.getValue().getDocumentType());
        assertEquals("# Notes", inserted.getValue().getDraftContent());
        verify(versions).insertIfAbsent(any());
    }
}
