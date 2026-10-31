package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.SessionMessage;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageRole;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageStatus;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageType;

import java.util.List;

public interface SessionMessageRepository {
    int nextSeq(Long sessionId);

    SessionMessage insert(SessionMessage message);

    /**
     * 在一个短事务内：
     * - 对 session 行加锁（FOR UPDATE），串行化同一 session 的消息写入；
     * - 计算 next seq 并插入，避免并发下撞 uq_session_messages_seq(session_id, seq)。
     */
    SessionMessage insertAutoSeq(SessionMessage message);

    /**
     * 批量插入（同一 session）：
     * - 在一个短事务内对 sessions 行加锁（FOR UPDATE）
     * - 获取起始 seq 并为 messages 依次分配连续 seq
     * - 批量 insert，降低事务次数与锁竞争
     */
    List<SessionMessage> insertBatchAutoSeq(Long sessionId, List<SessionMessage> messages);

    List<SessionMessage> listBySession(Long sessionId, int offset, int limit);

    /**
     * 取最近 N 条消息（用于构建聊天上下文）。
     * <p>实现通常会先按 seq 倒序取 limit 条，再返回按 seq 升序的结果，保证上下文顺序为“从旧到新”。</p>
     */
    List<SessionMessage> listRecentBySession(Long sessionId, int limit);

    /**
     * 增量拉取：返回 seq > afterSeq 的消息（按 seq 升序）。
     * <p>用于前端增量刷新/断线续拉。默认不做 role/type 过滤（包含 system 附件元信息）。</p>
     */
    List<SessionMessage> listBySessionAfterSeq(Long sessionId, int afterSeq, int limit);

    int countBySession(Long sessionId);

    /**
     * 用于“首轮对话完成”判定：统计特定 role/type/status 的消息数量（排除软删）。
     */
    int countBySessionAndRoleTypeStatus(Long sessionId,
                                       SessionMessageRole role,
                                       SessionMessageType type,
                                       SessionMessageStatus status);
}


