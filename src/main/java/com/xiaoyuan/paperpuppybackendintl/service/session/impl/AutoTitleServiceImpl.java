package com.xiaoyuan.paperpuppybackendintl.service.session.impl;

import com.xiaoyuan.paperpuppybackendintl.config.openai.OpenAiAutoTitleProperties;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageRole;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageStatus;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageType;
import com.xiaoyuan.paperpuppybackendintl.repository.SessionMessageRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.SessionRepository;
import com.xiaoyuan.paperpuppybackendintl.service.openai.OpenAiTitleGenerator;
import com.xiaoyuan.paperpuppybackendintl.service.session.AutoTitleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自动标题：业务层幂等控制（首轮判定 + 仅占位符时更新）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AutoTitleServiceImpl implements AutoTitleService {

    private final OpenAiAutoTitleProperties props;
    private final SessionRepository sessionRepository;
    private final SessionMessageRepository sessionMessageRepository;
    private final OpenAiTitleGenerator titleGenerator;

    @Override
    public void tryUpdateTitleAfterFirstTurn(Long userId, Long sessionId, String userText, String assistantText) {
        if (!props.enabled()) {
            log.debug("Auto title disabled, skip: sessionId={}", sessionId);
            return;
        }
        if (userId == null || sessionId == null || sessionId <= 0) {
            log.debug("Auto title invalid args, skip: userId={}, sessionId={}", userId, sessionId);
            return;
        }

        // 1) 判定是否“首轮用户消息已写入”：user(text,final)=1 且 assistant(text,final)=0
        // - 用于“标题生成与 AI 回复并行”，不等待 assistant
        // - 不会被 metadata 消息影响（metadata type!=TEXT）
        int userFinalText = sessionMessageRepository.countBySessionAndRoleTypeStatus(
                sessionId, SessionMessageRole.USER, SessionMessageType.TEXT, SessionMessageStatus.FINAL);

        if (userFinalText==0) {
            log.debug("Auto title skip: no final user text yet: sessionId={}", sessionId);
            return;
        }
        //用户消息不为空,则读取会话标题,如果会话标题是默认值(即占位符),则进行自动生成标题(根据用户的首轮消息,不包含AI的回复)
        String titleInDB = sessionRepository.findTitleById(sessionId);
        List<String> placeholders = props.placeholderTitles();
        if (titleInDB == null || !placeholders.contains(titleInDB)) {
            log.debug("Auto session title NOT updated (placeholder mismatch or user modified): sessionId={}, titleInDB={}",sessionId, titleInDB);
            return;
        }
        // 2) 生成标题（无状态：只输入首轮 user）
        log.info("Auto title generating (user-only): sessionId={}, model={}, timeoutMs={}",
                sessionId, props.model(), props.timeoutMs());

        String title = titleGenerator.generateTitleFromUserMessage(userText);
        if (title == null || title.isBlank()) {
            log.warn("Auto title generate returned empty, skip update: sessionId={}", sessionId);
            return;
        }
        boolean updated = sessionRepository.updateTitleIfIn(sessionId, placeholders, title);
        if (updated) {
            log.info("Auto session title updated: sessionId={}, title={}", sessionId, title);
        } else {
            log.warn("Auto session title NOT updated: sessionId={}, newTitle={}, placeholders={}", sessionId, title, props.placeholderTitles());
        }
    }
}


