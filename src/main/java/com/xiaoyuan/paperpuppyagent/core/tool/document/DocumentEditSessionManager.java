package com.xiaoyuan.paperpuppyagent.core.tool.document;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 文档编辑会话管理器。
 *
 * <p>
 * 管理所有活跃的 DocumentEditSession，按 sessionId + documentId 索引。
 * </p>
 */
@Slf4j
@Component
public class DocumentEditSessionManager {

    /**
     * Key: "sessionId:documentId"
     *
     * <p>使用 Caffeine 做有界缓存 + TTL，避免内存无上限增长。</p>
     */
    private final Cache<String, DocumentEditSession> sessions;

    public DocumentEditSessionManager(
            @Value("${agent.document-edit-session.max-size:2048}") long maxSize,
            @Value("${agent.document-edit-session.expire-after-access-minutes:30}") long expireAfterAccessMinutes) {
        long size = Math.max(128, maxSize);
        long minutes = Math.max(1, expireAfterAccessMinutes);
        this.sessions = Caffeine.newBuilder()
                .maximumSize(size)
                .expireAfterAccess(Duration.ofMinutes(minutes))
                .build();
        log.info("[DocumentEditSessionManager] init: maxSize={}, expireAfterAccessMinutes={}", size, minutes);
    }

    /**
     * 获取或创建编辑会话。
     */
    public DocumentEditSession getOrCreate(long sessionId, long documentId, String initialContent, int documentVersion) {
        String key = buildKey(sessionId, documentId);
        return sessions.get(key, k -> {
            log.info("[DocumentEditSessionManager] Creating new session: sessionId={}, documentId={}",
                    sessionId, documentId);
            return new DocumentEditSession(documentId, initialContent, documentVersion);
        });
    }

    /**
     * 获取已存在的编辑会话。
     */
    public DocumentEditSession get(long sessionId, long documentId) {
        return sessions.getIfPresent(buildKey(sessionId, documentId));
    }

    /**
     * 更新会话的初始内容（用于从 Backend 获取最新内容后更新）。
     */
    public DocumentEditSession createOrUpdate(long sessionId, long documentId, String content, int documentVersion) {
        String key = buildKey(sessionId, documentId);
        DocumentEditSession existing = sessions.getIfPresent(key);
        if (existing == null) {
            DocumentEditSession newSession = new DocumentEditSession(documentId, content, documentVersion);
            sessions.put(key, newSession);
            return newSession;
        }
        // 如果已存在，不覆盖（保持 Agent 侧为权威）
        return existing;
    }

    /**
     * 移除编辑会话。
     */
    public void remove(long sessionId, long documentId) {
        String key = buildKey(sessionId, documentId);
        sessions.invalidate(key);
        log.info("[DocumentEditSessionManager] Removed session: {}", key);
    }

    /**
     * 清理指定 sessionId 的所有编辑会话。
     */
    public void clearSession(long sessionId) {
        String prefix = sessionId + ":";
        sessions.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        log.info("[DocumentEditSessionManager] Cleared all sessions for sessionId={}", sessionId);
    }

    private String buildKey(long sessionId, long documentId) {
        return sessionId + ":" + documentId;
    }
}
