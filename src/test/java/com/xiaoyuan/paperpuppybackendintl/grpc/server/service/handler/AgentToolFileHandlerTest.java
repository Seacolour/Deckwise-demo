package com.xiaoyuan.paperpuppybackendintl.grpc.server.service.handler;

import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.ListProjectFilesRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.ProjectFileLinkView;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.ProjectFileMappingDTO;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Document;
import com.xiaoyuan.paperpuppybackendintl.model.enums.DocumentType;
import com.xiaoyuan.paperpuppybackendintl.repository.ObjectStorageFileRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectFileLinkRepository;
import com.xiaoyuan.paperpuppybackendintl.service.document.DocumentService;
import com.xiaoyuan.paperpuppybackendintl.service.file.FileService;
import com.xiaoyuan.paperpuppybackendintl.service.storage.ObjectAccessUrlService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolFileHandlerTest {

    @Test
    void listProjectFilesUsesFileIdToJoinViewMetadata() {
        FileService fileService = mock(FileService.class);
        DocumentService documentService = mock(DocumentService.class);
        ProjectFileLinkRepository projectFileLinkRepository = mock(ProjectFileLinkRepository.class);
        ObjectStorageFileRepository objectStorageFileRepository = mock(ObjectStorageFileRepository.class);
        ObjectAccessUrlService objectAccessUrlService = mock(ObjectAccessUrlService.class);
        AgentToolFileHandler handler = new AgentToolFileHandler(
                fileService,
                documentService,
                projectFileLinkRepository,
                objectStorageFileRepository,
                objectAccessUrlService);

        ProjectFileMappingDTO dto = new ProjectFileMappingDTO();
        dto.setId(123L);
        dto.setProjectId(10L);
        dto.setFileName("report.md");
        dto.setAddedAt(OffsetDateTime.parse("2026-03-25T10:15:30+08:00"));

        ProjectFileLinkView view = new ProjectFileLinkView();
        view.setLinkId(999L);
        view.setProjectId(10L);
        view.setFileId(123L);
        view.setFileExt("md");
        view.setSizeBytes(2048L);

        Document document = new Document();
        document.setId(77L);
        document.setCurrentFileId(123L);
        document.setDocumentType(DocumentType.MARKDOWN);
        document.setDraftContent("# Report");
        document.setSummary("summary");
        document.setVersion(5);
        document.setSummaryVersion(2);

        when(fileService.listProjectFiles(1L, 10L)).thenReturn(List.of(dto));
        when(projectFileLinkRepository.listProjectFiles(10L)).thenReturn(List.of(view));
        when(documentService.listByProjectAndCurrentFileIds(10L, List.of(123L))).thenReturn(List.of(document));

        var response = handler.listProjectFiles(ListProjectFilesRequest.newBuilder()
                .setUserId(1L)
                .setProjectId(10L)
                .build());

        assertEquals(1, response.getFilesCount());
        var item = response.getFiles(0);
        assertEquals(123L, item.getFileId());
        assertEquals("md", item.getFileType());
        assertEquals(2048L, item.getSizeBytes());
        assertEquals("summary", item.getSummary());
        assertEquals(5, item.getDocumentVersion());
        assertEquals(2, item.getSummaryVersion());
        assertEquals("# Report".length(), item.getEstimatedTextChars());
    }
}
