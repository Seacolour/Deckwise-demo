package com.xiaoyuan.paperpuppybackendintl.service.session.stream;

/**
 * Agent 事件类型（显式事件模型）。
 *
 * <p>说明：这些类型是“内部统一语义”，对外 SSE event name 可由 bridge 做兼容映射。</p>
 */
public enum AgentEventType {
    TEXT,
    METADATA,
    TOOL,
    THOUGHT,
    WARNING,
    DONE,
    ERROR,
    ABORT
}


