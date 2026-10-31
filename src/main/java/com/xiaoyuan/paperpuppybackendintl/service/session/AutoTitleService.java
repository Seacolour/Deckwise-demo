package com.xiaoyuan.paperpuppybackendintl.service.session;

/**
 * 自动生成会话标题（首轮对话完成后）。
 */
public interface AutoTitleService {

    /**
     * 尝试在“首轮对话完成”后更新会话标题（幂等，不会覆盖用户手动标题）。
     */
    void tryUpdateTitleAfterFirstTurn(Long userId, Long sessionId, String userText, String assistantText);
}


