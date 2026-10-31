package com.xiaoyuan.paperpuppybackendintl.model.entity;

import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageRole;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageStatus;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageType;
import lombok.Data;
import org.jooq.JSONB;

import java.time.OffsetDateTime;

@Data
public class SessionMessage {
    private Long id;
    private Long sessionId;
    private Integer seq;
    private SessionMessageRole role;
    private SessionMessageType type;
    private String content;
    private String rawContent;
    private JSONB parts;
    private String name;
    private String toolCallId;
    private JSONB metadata;
    private SessionMessageStatus status;
    private Integer tokenCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
}


