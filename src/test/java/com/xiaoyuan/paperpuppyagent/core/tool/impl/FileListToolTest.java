package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.backend.FileItem;
import com.xiaoyuan.paperpuppyagent.grpc.backend.ListProjectFilesResponse;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileListToolTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void executeRoutesMarkdownAndRawFileTools() throws Exception {
        BackendToolGrpcClient backendClient = mock(BackendToolGrpcClient.class);
        FileListTool tool = new FileListTool(om, backendClient);

        when(backendClient.listProjectFiles(eq(7L), eq(13L)))
                .thenReturn(ListProjectFilesResponse.newBuilder()
                        .addFiles(FileItem.newBuilder()
                                .setFileId(3L)
                                .setFileName("notes.md")
                                .setFileType("md")
                                .setDocumentId(13L)
                                .setDocumentType("markdown")
                                .build())
                        .addFiles(FileItem.newBuilder()
                                .setFileId(4L)
                                .setFileName("table.xlsx")
                                .setFileType("xlsx")
                                .build())
                        .addFiles(FileItem.newBuilder()
                                .setFileId(5L)
                                .setFileName("data.json")
                                .setFileType("json")
                                .build())
                        .build());

        JsonNode result = tool.execute(om.createObjectNode(), new ToolExecutionContext(7L, 13L, 21L));

        JsonNode markdownFile = result.path("files").get(0);
        assertTrue(contains(markdownFile.path("composite_tools"), "document_read"));
        assertFalse(contains(markdownFile.path("composite_tools"), "slides_authoring_model_read"));

        JsonNode spreadsheetFile = result.path("files").get(1);
        assertTrue(contains(spreadsheetFile.path("composite_tools"), "file_read_url"));
        assertTrue(contains(spreadsheetFile.path("composite_tools"), "file_content_read"));
        assertFalse(contains(spreadsheetFile.path("composite_tools"), "spreadsheet_workbook_read"));

        JsonNode jsonFile = result.path("files").get(2);
        assertTrue(contains(jsonFile.path("composite_tools"), "json_structure_read"));
        assertTrue(contains(jsonFile.path("composite_tools"), "json_subtree_read"));
        assertTrue(contains(jsonFile.path("composite_tools"), "file_content_read"));
        assertFalse(contains(jsonFile.path("composite_tools"), "document_read"));
    }

    private boolean contains(JsonNode array, String value) {
        if (array == null || !array.isArray()) {
            return false;
        }
        for (JsonNode item : array) {
            if (value.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }
}
