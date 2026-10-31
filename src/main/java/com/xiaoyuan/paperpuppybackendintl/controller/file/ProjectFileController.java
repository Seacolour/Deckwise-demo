package com.xiaoyuan.paperpuppybackendintl.controller.file;

import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.common.ResultUtils;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FilePurgePreviewResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FilePurgeRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FilePurgeResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FileUploadCacheResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.ProjectFileMappingDTO;
import com.xiaoyuan.paperpuppybackendintl.service.file.FileService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对齐旧项目：/projects/{projectId}/files/move|remove|page
 * 新项目先实现 add/remove/list（page 后续再补）。
 */
@RestController
@RequestMapping("/projects")
@AuthCheck
public class ProjectFileController {

    private final FileService fileService;

    public ProjectFileController(FileService fileService) {
        this.fileService = fileService;
    }


    @PostMapping("/{projectId}/files/move")
    public BaseResponse<Boolean> move(@PathVariable Long projectId,
                                     @RequestParam String cacheKey,
                                     @RequestParam(required = false) String fileName,
                                     @CurrentUserId Long userId) {
        return ResultUtils.success(fileService.addFileToProject(userId, projectId, cacheKey, fileName,null));
    }

    @PostMapping("/{projectId}/files/{fileId}/duplicate")
    public BaseResponse<FileUploadCacheResponse> duplicate(@PathVariable Long projectId,
                                                           @PathVariable Long fileId,
                                                           @RequestParam(required = false) String newName,
                                                           @CurrentUserId Long userId) {
        return ResultUtils.success(fileService.duplicateProjectFile(userId, projectId, fileId, newName));
    }

    @PostMapping("/{projectId}/files/remove")
    public BaseResponse<Boolean> remove(@PathVariable Long projectId,
                                        @RequestParam String cacheKey,
                                        @CurrentUserId Long userId) {
        return ResultUtils.success(fileService.removeFileFromProject(userId, projectId, cacheKey));
    }

    @GetMapping("/{projectId}/files/{fileId}/purge-preview")
    public BaseResponse<FilePurgePreviewResponse> purgePreview(@PathVariable Long projectId,
                                                               @PathVariable Long fileId,
                                                               @CurrentUserId Long userId) {
        return ResultUtils.success(fileService.previewProjectFilePurge(userId, projectId, fileId));
    }

    @PostMapping("/{projectId}/files/{fileId}/purge")
    public BaseResponse<FilePurgeResponse> purge(@PathVariable Long projectId,
                                                 @PathVariable Long fileId,
                                                 @RequestBody(required = false) FilePurgeRequest request,
                                                 @CurrentUserId Long userId) {
        return ResultUtils.success(fileService.purgeProjectFile(userId, projectId, fileId, request));
    }

    @PatchMapping("/{projectId}/files/{fileId}/rename")
    public BaseResponse<Boolean> rename(@PathVariable Long projectId,
                                        @PathVariable Long fileId,
                                        @RequestParam String newName,
                                        @CurrentUserId Long userId) {
        return ResultUtils.success(fileService.renameProjectFile(userId, projectId, fileId, newName));
    }

    @GetMapping("/{projectId}/files")
    public BaseResponse<List<ProjectFileMappingDTO>> list(@PathVariable Long projectId,
                                                          @CurrentUserId Long userId) {
        return ResultUtils.success(fileService.listProjectFiles(userId, projectId));
    }
}


