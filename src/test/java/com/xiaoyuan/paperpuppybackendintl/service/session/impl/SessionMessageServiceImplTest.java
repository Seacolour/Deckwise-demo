package com.xiaoyuan.paperpuppybackendintl.service.session.impl;

import com.xiaoyuan.paperpuppybackendintl.common.PageRequest;
import com.xiaoyuan.paperpuppybackendintl.common.PageResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.SessionMessageDTO;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Session;
import com.xiaoyuan.paperpuppybackendintl.model.entity.SessionMessage;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageRole;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageStatus;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageType;
import com.xiaoyuan.paperpuppybackendintl.repository.AgentRunPersistenceRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.SessionMessageRepository;
import com.xiaoyuan.paperpuppybackendintl.service.project.ProjectService;
import com.xiaoyuan.paperpuppybackendintl.service.session.SessionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionMessageServiceImplTest {

    @Test
    void listMessagesAttachesAgentRunSpanToMessagesInSameGroup() {
        SessionService sessionService = mock(SessionService.class);
        SessionMessageRepository sessionMessageRepository = mock(SessionMessageRepository.class);
        ProjectService projectService = mock(ProjectService.class);
        AgentRunPersistenceRepository agentRunPersistenceRepository = mock(AgentRunPersistenceRepository.class);
        SessionMessageServiceImpl service = new SessionMessageServiceImpl(
                sessionService,
                sessionMessageRepository,
                projectService,
                agentRunPersistenceRepository
        );

        Session session = new Session();
        session.setId(101L);
        session.setProjectId(501L);
        when(sessionService.requireSession(7L, 101L)).thenReturn(session);
        when(sessionMessageRepository.listBySession(101L, 0, 20)).thenReturn(List.of(
                message(1L, 1, SessionMessageRole.USER, SessionMessageType.TEXT, "user prompt"),
                message(2L, 2, SessionMessageRole.ASSISTANT, SessionMessageType.TEXT, "assistant start"),
                message(3L, 3, SessionMessageRole.ASSISTANT, SessionMessageType.TOOL_OUTPUT, "{}"),
                message(4L, 4, SessionMessageRole.ASSISTANT, SessionMessageType.TEXT, "assistant done")
        ));
        when(sessionMessageRepository.countBySession(101L)).thenReturn(4);
        when(agentRunPersistenceRepository.listSessionAgentRunMessageSpans(101L, 1, 4))
                .thenReturn(List.of(new AgentRunPersistenceRepository.SessionAgentRunMessageSpan(
                        11L,
                        "run_abc",
                        "completed",
                        2,
                        4
                )));

        PageRequest page = new PageRequest();
        page.setCurrent(1);
        page.setPageSize(20);

        PageResponse<SessionMessageDTO> response = service.listMessages(7L, 101L, page);

        List<SessionMessageDTO> records = response.getRecords();
        assertEquals(4, records.size());
        assertNull(records.get(0).getAgentMessageGroupId());
        for (int i = 1; i < records.size(); i++) {
            assertEquals("run_abc", records.get(i).getAgentRunId());
            assertEquals("run_abc", records.get(i).getAgentMessageGroupId());
            assertEquals(2, records.get(i).getAgentMessageStartSeq());
            assertEquals(4, records.get(i).getAgentMessageEndSeq());
            assertEquals("completed", records.get(i).getAgentRunStatus());
        }
        verify(agentRunPersistenceRepository).listSessionAgentRunMessageSpans(101L, 1, 4);
    }

    private static SessionMessage message(Long id,
                                          Integer seq,
                                          SessionMessageRole role,
                                          SessionMessageType type,
                                          String content) {
        SessionMessage message = new SessionMessage();
        message.setId(id);
        message.setSessionId(101L);
        message.setSeq(seq);
        message.setRole(role);
        message.setType(type);
        message.setContent(content);
        message.setStatus(SessionMessageStatus.FINAL);
        message.setDeleted(false);
        return message;
    }
}
