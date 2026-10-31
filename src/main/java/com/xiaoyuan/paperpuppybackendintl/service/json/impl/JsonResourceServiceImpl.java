package com.xiaoyuan.paperpuppybackendintl.service.json.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.ProjectFileLinkView;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectFileLinkRepository;
import com.xiaoyuan.paperpuppybackendintl.service.file.FileService;
import com.xiaoyuan.paperpuppybackendintl.service.json.JsonResourceService;
import com.xiaoyuan.paperpuppybackendintl.utils.HashUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class JsonResourceServiceImpl implements JsonResourceService {

    private final ObjectMapper objectMapper;
    private final FileService fileService;
    private final ProjectFileLinkRepository projectFileLinkRepository;
    private final R2Client r2Client;

    public JsonResourceServiceImpl(@Qualifier("jackson2Mapper") ObjectMapper objectMapper,
                                   FileService fileService,
                                   ProjectFileLinkRepository projectFileLinkRepository,
                                   R2Client r2Client) {
        this.objectMapper = objectMapper;
        this.fileService = fileService;
        this.projectFileLinkRepository = projectFileLinkRepository;
        this.r2Client = r2Client;
    }

    @Override
    public JsonResourceSnapshot readResource(Long userId, Long projectId, Long fileId, Long documentId) {
        if (fileId == null || fileId <= 0 || (documentId != null && documentId > 0)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持普通 JSON 文件的 file_id");
        }
        return readFileResource(userId, projectId, fileId);
    }

    private JsonResourceSnapshot readFileResource(Long userId, Long projectId, Long fileId) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "project_id 非法");
        }

        // 复用现有项目文件读取链路做权限校验；无权限时由底层抛出业务异常。
        fileService.listProjectFiles(userId, projectId);

        ProjectFileLinkView fileView = projectFileLinkRepository.listProjectFiles(projectId).stream()
                .filter(view -> view.getFileId() != null && view.getFileId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "项目内未找到目标文件"));
        if (fileView.getObjectKey() == null || fileView.getObjectKey().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "目标文件缺少可读取的对象存储内容");
        }

        byte[] bytes;
        try {
            bytes = r2Client.downloadBytes(fileView.getObjectKey());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取 JSON 文件内容失败");
        }

        JsonNode root = parseJson(bytes);
        String canonicalJson = writeJson(root);
        return new JsonResourceSnapshot(
                "file",
                fileId,
                0L,
                false,
                HashUtils.sha256Hex(canonicalJson.getBytes(StandardCharsets.UTF_8)),
                resolveSourceName(fileView),
                "",
                root
        );
    }

    private JsonNode parseJson(byte[] bytes) {
        try {
            String text = new String(bytes == null ? new byte[0] : bytes, StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
                text = text.substring(1);
            }
            return objectMapper.readTree(text);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标资源不是合法 JSON");
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "JSON 资源序列化失败");
        }
    }

    private String resolveSourceName(ProjectFileLinkView fileView) {
        if (fileView == null) {
            return "";
        }
        if (fileView.getDisplayName() != null && !fileView.getDisplayName().isBlank()) {
            return fileView.getDisplayName().trim();
        }
        if (fileView.getFileId() != null) {
            return "file-" + fileView.getFileId();
        }
        return "";
    }

}
