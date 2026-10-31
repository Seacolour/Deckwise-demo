package com.xiaoyuan.paperpuppybackendintl.controller.document;

import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.common.ResultUtils;
import com.xiaoyuan.paperpuppybackendintl.model.dto.document.DocumentCreateRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.document.DocumentDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.document.DocumentDraftUpdateRequest;
import com.xiaoyuan.paperpuppybackendintl.service.document.DocumentService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/documents")
@AuthCheck
@Validated
public class DocumentsController {

    private final DocumentService documentService;

    public DocumentsController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/create")
    public BaseResponse<DocumentDTO> createDocument(@RequestBody DocumentCreateRequest req,
                                                    @CurrentUserId Long userId) {
        return ResultUtils.success(documentService.create(userId, req));
    }

    @GetMapping("/{documentId}")
    public BaseResponse<DocumentDTO> getDocument(@PathVariable Long documentId,
                                                 @RequestParam(value = "includeVersions", required = false, defaultValue = "false")
                                                 boolean includeVersions,
                                                 @CurrentUserId Long userId) {
        return ResultUtils.success(documentService.get(userId, documentId, includeVersions));
    }









    @GetMapping("/list")
    public BaseResponse<List<DocumentDTO>> listDocument(@RequestParam("projectId") Long projectId,
                                                        @RequestParam(value = "offset", required = false, defaultValue = "0") int offset,
                                                        @RequestParam(value = "limit", required = false, defaultValue = "50") int limit,
                                                        @CurrentUserId Long userId) {
        return ResultUtils.success(documentService.listByProject(userId, projectId, offset, limit));
    }

    @PutMapping("/{documentId}/draft")
    public BaseResponse<DocumentDTO> updateDraft(@PathVariable Long documentId,
                                                 @RequestBody DocumentDraftUpdateRequest req,
                                                 @CurrentUserId Long userId) {
        return ResultUtils.success(documentService.updateDraft(userId, documentId, req));
    }

    @DeleteMapping("/{documentId}")
    public BaseResponse<Boolean> deleteDocument(@PathVariable Long documentId,
                                                @CurrentUserId Long userId) {
        return ResultUtils.success(documentService.delete(userId, documentId));
    }





}
