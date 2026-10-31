package com.xiaoyuan.paperpuppybackendintl.controller.session;

import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.common.PageRequest;
import com.xiaoyuan.paperpuppybackendintl.common.PageResponse;
import com.xiaoyuan.paperpuppybackendintl.common.ResultUtils;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.SessionCreateRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.SessionDTO;
import com.xiaoyuan.paperpuppybackendintl.service.session.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

/**
 * Project 维度的会话接口（创建/列表）
 */
@RestController
@RequestMapping("/projects")
@AuthCheck
@Validated
@RequiredArgsConstructor
public class ProjectSessionController {

    private final SessionService sessionService;

    // 在指定项目下创建会话
    @PostMapping("/{projectId}/create")
    public BaseResponse<SessionDTO> createSession(@PathVariable Long projectId,
                                                  @RequestBody SessionCreateRequest req,
                                                  @CurrentUserId Long userId) {
        return ResultUtils.success(sessionService.createSession(userId, projectId, req));
    }

    // 列出项目下的会话
    @PostMapping("/{projectId}/list")
    public BaseResponse<PageResponse<SessionDTO>> listSessions(@PathVariable Long projectId,
                                                               @RequestParam(value = "archived", required = false) Boolean archived,
                                                               @Valid @RequestBody(required = false) PageRequest page,
                                                               @CurrentUserId Long userId) {
        return ResultUtils.success(sessionService.listSessions(userId, projectId, archived, page));
    }
}


