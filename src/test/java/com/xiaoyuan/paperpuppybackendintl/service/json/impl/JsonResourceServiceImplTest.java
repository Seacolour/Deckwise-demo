package com.xiaoyuan.paperpuppybackendintl.service.json.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.ProjectFileLinkView;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectFileLinkRepository;
import com.xiaoyuan.paperpuppybackendintl.service.file.FileService;
import com.xiaoyuan.paperpuppybackendintl.utils.HashUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonResourceServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Test
    void readResourceLoadsProjectJsonFileFromObjectStorage() throws Exception {
        FileService fileService = mock(FileService.class);
        ProjectFileLinkRepository projectFileLinkRepository = mock(ProjectFileLinkRepository.class);
        R2Client r2Client = mock(R2Client.class);
        JsonResourceServiceImpl service = new JsonResourceServiceImpl(
                objectMapper,
                fileService,
                projectFileLinkRepository,
                r2Client
        );

        ProjectFileLinkView fileView = new ProjectFileLinkView();
        fileView.setFileId(55L);
        fileView.setDisplayName("demo.json");
        fileView.setObjectKey("project/55.json");

        when(fileService.listProjectFiles(7L, 13L)).thenReturn(List.of());
        when(projectFileLinkRepository.listProjectFiles(13L)).thenReturn(List.of(fileView));
        when(r2Client.downloadBytes("project/55.json")).thenReturn("""
                {
                  "cards": [
                    {
                      "title": "Intro"
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8));

        var snapshot = service.readResource(7L, 13L, 55L, 0L);

        String expectedCanonicalJson = objectMapper.writeValueAsString(objectMapper.readTree("""
                {
                  "cards": [
                    {
                      "title": "Intro"
                    }
                  ]
                }
                """));

        assertEquals("file", snapshot.resourceKind());
        assertEquals(55L, snapshot.fileId());
        assertEquals("demo.json", snapshot.sourceName());
        assertEquals(HashUtils.sha256Hex(expectedCanonicalJson.getBytes(StandardCharsets.UTF_8)), snapshot.revision());
        assertFalse(snapshot.writable());
        assertEquals("Intro", snapshot.root().path("cards").get(0).path("title").asText());
    }
}
