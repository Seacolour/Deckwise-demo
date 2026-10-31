package com.xiaoyuan.paperpuppybackendintl.model.dto.session;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class SessionMessageDTO {
    private Long id;
    private Long sessionId;
    private Integer seq;
    private String role;
    private String type;
    private String content;
    private String rawContent;
    private String name;
    private String toolCallId;
    private String parts;
    private String metadata;
    private String status;
    private Integer tokenCount;
    private OffsetDateTime createdAt;
    private String agentRunId;
    private String agentMessageGroupId;
    private Integer agentMessageStartSeq;
    private Integer agentMessageEndSeq;
    private String agentRunStatus;
}


