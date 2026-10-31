package com.xiaoyuan.paperpuppybackendintl.service.session;

import com.xiaoyuan.paperpuppybackendintl.model.dto.session.UserMessageSendRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 *  对话 Service（接口 + impl）
 */
public interface ChatService {

    /**
     * 流式对话（接口）
     */
    SseEmitter chat(Long userId, Long sessionId, UserMessageSendRequest req);

    /**
     * 流式中断（P0）：标记中断并尽快取消 gRPC 流
     */
    void abort(Long userId, Long sessionId);
}


