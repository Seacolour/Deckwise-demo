package com.xiaoyuan.paperpuppybackendintl.controller.session;

import com.xiaoyuan.paperpuppybackendintl.model.dto.session.SessionDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.SessionMessageDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.SessionUpdateRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.UserMessageSendRequest;
import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.common.PageRequest;
import com.xiaoyuan.paperpuppybackendintl.common.PageResponse;
import com.xiaoyuan.paperpuppybackendintl.common.ResultUtils;
import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import com.xiaoyuan.paperpuppybackendintl.service.session.ChatService;
import com.xiaoyuan.paperpuppybackendintl.service.session.SessionMessageService;
import com.xiaoyuan.paperpuppybackendintl.service.session.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Session 维度接口（详情/更新/删除/消息）
 */
@RestController
@RequestMapping("/sessions")
@AuthCheck
@Validated
public class SessionController {

    private final SessionService sessionService;
    private final SessionMessageService sessionMessageService;
    private final ChatService chatService;

    public SessionController(SessionService sessionService,
                             SessionMessageService sessionMessageService,
                             ChatService chatService) {
        this.sessionService = sessionService;
        this.sessionMessageService = sessionMessageService;
        this.chatService = chatService;
    }

    @GetMapping("/getSessionById")
    public BaseResponse<SessionDTO> getMySessionById(@RequestParam Long sessionId,
                                                     @CurrentUserId Long userId) {
        return ResultUtils.success(sessionService.getSession(userId, sessionId));
    }


    @PutMapping("/{id}")
    public BaseResponse<SessionDTO> updateSession(@PathVariable("id") Long sessionId,
                                                  @RequestBody SessionUpdateRequest req,
                                                  @CurrentUserId Long userId) {
        return ResultUtils.success(sessionService.updateSession(userId, sessionId, req));
    }

    @PostMapping("/deleteSession")
    public BaseResponse<Boolean> deleteSession(@RequestParam Long sessionId,
                                               @CurrentUserId Long userId) {
        boolean b = sessionService.deleteSession(userId, sessionId);
        return ResultUtils.success(b);
    }

    @GetMapping("/getSessionMessages")
    public BaseResponse<PageResponse<SessionMessageDTO>> listMessages(@RequestParam Long sessionId,
                                                                      @Valid @ModelAttribute PageRequest page,
                                                                      @CurrentUserId Long userId) {
        return ResultUtils.success(sessionMessageService.listMessages(userId, sessionId, page));
    }

    /**
     * 增量获取消息：按 seq 拉取（比时间戳更稳定），包含 system 附件元信息（attachment-v1）。
     * <p>前端用法：lastSeq = messages[messages.length-1].seq；下次传 afterSeq=lastSeq。</p>
     */
    @GetMapping("/{sessionId}/messages/after-seq")
    public BaseResponse<List<SessionMessageDTO>> listMessagesAfterSeq(@PathVariable Long sessionId,
                                                                      @RequestParam(required = false) Integer afterSeq,
                                                                      @RequestParam(required = false) Integer limit,
                                                                      @CurrentUserId Long userId) {
        return ResultUtils.success(sessionMessageService.listMessagesAfterSeq(userId, sessionId, afterSeq, limit));
    }

    /**
     * 正式对接 AI gRPC（流式返回 SSE）
     */
    @PostMapping(value = "/{sessionId}/sendRPC", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessageRPC(@PathVariable Long sessionId,
                                     @RequestBody UserMessageSendRequest req,
                                     @CurrentUserId Long userId) {
        return chatService.chat(userId, sessionId, req);
    }

    /**
     * P0：中断流式回复
     */
    @PostMapping("/{sessionId}/abort")
    public BaseResponse<Boolean> abort(@PathVariable Long sessionId,
                                       @CurrentUserId Long userId) {
        chatService.abort(userId, sessionId);
        return ResultUtils.success(true);
    }

}


