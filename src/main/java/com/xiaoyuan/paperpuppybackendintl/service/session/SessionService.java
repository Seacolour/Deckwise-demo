package com.xiaoyuan.paperpuppybackendintl.service.session;

import com.xiaoyuan.paperpuppybackendintl.model.dto.session.SessionCreateRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.SessionDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.SessionUpdateRequest;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Session;
import com.xiaoyuan.paperpuppybackendintl.common.PageRequest;
import com.xiaoyuan.paperpuppybackendintl.common.PageResponse;

/**
 *  会话 Service（接口 + impl）
 */
public interface SessionService {

    SessionDTO createSession(Long userId, Long projectId, SessionCreateRequest req);

    PageResponse<SessionDTO> listSessions(Long userId, Long projectId, Boolean archived, PageRequest page);

    SessionDTO getSession(Long userId, Long sessionId);

    SessionDTO updateSession(Long userId, Long sessionId, SessionUpdateRequest req);

    boolean deleteSession(Long userId, Long sessionId);

    void touchAfterUserMessage(Long userId, Long sessionId, String lastUserMessage);

    Session requireSession(Long userId, Long sessionId);
}


