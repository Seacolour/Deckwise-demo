package com.xiaoyuan.paperpuppybackendintl.service.session;

import com.xiaoyuan.paperpuppybackendintl.common.PageRequest;
import com.xiaoyuan.paperpuppybackendintl.common.PageResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.SessionMessageDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.UserMessageSendRequest;

import java.util.List;

/**
 *  会话消息 Service（接口 + impl）
 */
public interface SessionMessageService {

    /**
     * 新版分页：使用 PageRequest(current,pageSize)；会转换为 offset/limit 并透传到底层仓储。
     */
    PageResponse<SessionMessageDTO> listMessages(Long userId, Long sessionId, PageRequest page);

    /**
     * 增量拉取：返回 seq > afterSeq 的消息（按 seq 升序）。不做 role/type 过滤（包含 system 附件元信息）。
     */
    List<SessionMessageDTO> listMessagesAfterSeq(Long userId, Long sessionId, Integer afterSeq, Integer limit);

    SessionMessageDTO appendUserMessage(Long userId, Long sessionId, UserMessageSendRequest req);
}


