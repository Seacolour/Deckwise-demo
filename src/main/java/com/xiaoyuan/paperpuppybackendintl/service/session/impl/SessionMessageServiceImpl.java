package com.xiaoyuan.paperpuppybackendintl.service.session.impl;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.common.PageRequest;
import com.xiaoyuan.paperpuppybackendintl.common.PageResponse;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.SessionMessageDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.UserMessageSendRequest;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Session;
import com.xiaoyuan.paperpuppybackendintl.model.entity.SessionMessage;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageRole;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageStatus;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageType;
import com.xiaoyuan.paperpuppybackendintl.repository.AgentRunPersistenceRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.SessionMessageRepository;
import com.xiaoyuan.paperpuppybackendintl.service.session.SessionMessageService;
import com.xiaoyuan.paperpuppybackendintl.service.session.SessionService;
import com.xiaoyuan.paperpuppybackendintl.service.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话消息 Service（实现类）
 */
@Service
@RequiredArgsConstructor
public class SessionMessageServiceImpl implements SessionMessageService {

    private final SessionService sessionService;
    private final SessionMessageRepository sessionMessageRepository;
    private final ProjectService projectService;
    private final AgentRunPersistenceRepository agentRunPersistenceRepository;

    @Override
    public PageResponse<SessionMessageDTO> listMessages(Long userId, Long sessionId, PageRequest page) {
        int current = page == null ? 1 : page.getCurrent();
        int pageSize = page == null ? 10 : page.getPageSize();
        if (current <= 0) {
            current = 1;
        }
        if (pageSize <= 0) {
            pageSize = 10;
        }
        pageSize = Math.min(pageSize, 200);
        int offset = (current - 1) * pageSize;

        Session s = sessionService.requireSession(userId, sessionId);
        List<SessionMessage> messages = sessionMessageRepository.listBySession(s.getId(), offset, pageSize);
        List<SessionMessageDTO> records = attachAgentMessageGroups(s.getId(), messages);
        int total = sessionMessageRepository.countBySession(s.getId());
        return PageResponse.of(current, pageSize, total, records);
    }

    @Override
    public List<SessionMessageDTO> listMessagesAfterSeq(Long userId, Long sessionId, Integer afterSeq, Integer limit) {
        int realAfterSeq = afterSeq == null ? 0 : Math.max(afterSeq, 0);
        int realLimit = limit == null ? 200 : Math.max(limit, 1);
        realLimit = Math.min(realLimit, 500);

        Session s = sessionService.requireSession(userId, sessionId);
        List<SessionMessage> messages = sessionMessageRepository.listBySessionAfterSeq(s.getId(), realAfterSeq, realLimit);
        return attachAgentMessageGroups(s.getId(), messages);
    }

    @Override
    public SessionMessageDTO appendUserMessage(Long userId, Long sessionId, UserMessageSendRequest req) {
        return appendUserMessageInternal(userId, sessionId, req == null ? null : req.getMessage());
    }

    private SessionMessageDTO appendUserMessageInternal(Long userId, Long sessionId, String content) {
        Session s = sessionService.requireSession(userId, sessionId);
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        }
        // 用户输入：单输入框文本。raw_content 由后端决定，默认等于 content
        // 用户发送文件时，因为前端和AI所需资源类型的差异，raw_content为文件元信息，content为解析后文件内容
        String raw = content;

        SessionMessage m = new SessionMessage();
        m.setSessionId(s.getId());
        m.setRole(SessionMessageRole.USER);
        m.setType(SessionMessageType.TEXT);
        // Agent 场景：保留 markdown，避免丢失结构/行信息导致重复工具调用
        m.setContent(raw);
        m.setRawContent(raw);
        m.setStatus(SessionMessageStatus.FINAL);
        m.setDeleted(false);

        SessionMessage saved = sessionMessageRepository.insertAutoSeq(m);

        // 同步刷新会话的 last_user_message / last_active_at
        sessionService.touchAfterUserMessage(userId, s.getId(), content);

        // 同步刷新项目的 last_active_at
        projectService.touchProjectActivity(s.getProjectId());

        return toDTO(saved);
    }

    private List<SessionMessageDTO> attachAgentMessageGroups(Long sessionId, List<SessionMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<SessionMessageDTO> records = messages.stream()
                .map(this::toDTO)
                .toList();
        Integer firstSeq = records.stream()
                .map(SessionMessageDTO::getSeq)
                .filter(seq -> seq != null && seq > 0)
                .min(Integer::compareTo)
                .orElse(null);
        Integer lastSeq = records.stream()
                .map(SessionMessageDTO::getSeq)
                .filter(seq -> seq != null && seq > 0)
                .max(Integer::compareTo)
                .orElse(null);
        if (sessionId == null || firstSeq == null || lastSeq == null) {
            return records;
        }

        List<AgentRunPersistenceRepository.SessionAgentRunMessageSpan> spans =
                agentRunPersistenceRepository.listSessionAgentRunMessageSpans(sessionId, firstSeq, lastSeq);
        if (spans.isEmpty()) {
            return records;
        }
        for (SessionMessageDTO dto : records) {
            Integer seq = dto.getSeq();
            if (seq == null) {
                continue;
            }
            for (AgentRunPersistenceRepository.SessionAgentRunMessageSpan span : spans) {
                if (span.firstMessageSeq() == null || span.lastMessageSeq() == null) {
                    continue;
                }
                if (seq < span.firstMessageSeq() || seq > span.lastMessageSeq()) {
                    continue;
                }
                dto.setAgentRunId(span.agentRunId());
                dto.setAgentMessageGroupId(span.agentRunId());
                dto.setAgentMessageStartSeq(span.firstMessageSeq());
                dto.setAgentMessageEndSeq(span.lastMessageSeq());
                dto.setAgentRunStatus(span.status());
                break;
            }
        }
        return records;
    }

    private SessionMessageDTO toDTO(SessionMessage m) {
        if (m == null) {
            return null;
        }
        SessionMessageDTO dto = new SessionMessageDTO();
        dto.setId(m.getId());
        dto.setSessionId(m.getSessionId());
        dto.setSeq(m.getSeq());
        dto.setRole(m.getRole() != null ? m.getRole().toDatabaseValue() : null);
        dto.setType(m.getType() != null ? m.getType().toDatabaseValue() : null);
        dto.setContent(m.getContent());
        dto.setRawContent(m.getRawContent());
        dto.setName(m.getName());
        dto.setToolCallId(m.getToolCallId());
        dto.setParts(m.getParts() != null ? m.getParts().data() : null);
        dto.setMetadata(m.getMetadata() != null ? m.getMetadata().data() : null);
        dto.setStatus(m.getStatus() != null ? m.getStatus().toDatabaseValue() : null);
        dto.setTokenCount(m.getTokenCount());
        dto.setCreatedAt(m.getCreatedAt());
        return dto;
    }
}
