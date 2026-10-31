package com.xiaoyuan.paperpuppybackendintl.service.session.stream;

import java.time.OffsetDateTime;

/**
 * 显式 Agent 事件（统一模型）。
 *
 * @param type    事件类型
 * @param seq     会话内单调递增序号（用于前端排序/断线续传定位）
 * @param ts      事件生成时间
 * @param payload 事件负载（可以是 String/Map/POJO）
 */
public record AgentEvent(
        AgentEventType type,
        long seq,
        OffsetDateTime ts,
        Object payload
) {
    public static AgentEvent of(AgentEventType type, long seq, Object payload) {
        return new AgentEvent(type, seq, OffsetDateTime.now(), payload);
    }
}


