package com.xiaoyuan.paperpuppybackendintl.service.document.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

/**
 * 文档摘要任务队列（Redis 轻量队列）。
 *
 * <p>复用 PdfPreviewJobQueue 的模式：ready list + processing list + processing zset（用于崩溃回收） + dedup key（短期去重）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentSummaryJobQueue {

    // ready -> processing 原子搬运（BRPOPLPUSH）
    public static final String READY_LIST = "pp:q:doc_summary:ready";
    public static final String PROCESSING_LIST = "pp:q:doc_summary:processing";
    public static final String PROCESSING_ZSET = "pp:q:doc_summary:processing_z"; // score=timestamp_ms, member=job_json
    private static final String DEDUP_PREFIX = "pp:q:doc_summary:dedup:"; // key = prefix + docId + ":v:" + expectedVersion

    // 任务去重 TTL：避免同一 doc+version 在短时间内被重复入队（也避免无限增长）
    private static final Duration DEDUP_TTL = Duration.ofDays(1);

    private final StringRedisTemplate redis;
    @Qualifier("jackson2Mapper")
    private final ObjectMapper objectMapper;

    public record Job(long documentId, int expectedVersion, int attempts) {
    }

    /**
     * 在事务提交后入队，保证 DB 状态与队列一致。
     */
    public void enqueueAfterCommit(Job job) {
        if (job == null || job.documentId() <= 0) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("[DocumentSummaryJobQueue] enqueueAfterCommit -> enqueue: documentId={}, expectedVersion={}",
                            job.documentId(), job.expectedVersion());
                    enqueue(job);
                }
            });
        } else {
            log.info("[DocumentSummaryJobQueue] enqueueAfterCommit(no-tx) -> enqueue: documentId={}, expectedVersion={}",
                    job.documentId(), job.expectedVersion());
            enqueue(job);
        }
    }

    /**
     * 直接入队（带 docId+expectedVersion 去重）。
     */
    public void enqueue(Job job) {
        if (job == null || job.documentId() <= 0) {
            return;
        }
        try {
            String dedupKey = dedupKey(job.documentId(), job.expectedVersion());
            Boolean ok = redis.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_TTL);
            if (ok == null || !ok) {
                log.info("[DocumentSummaryJobQueue] dedup hit, skip enqueue: documentId={}, expectedVersion={}",
                        job.documentId(), job.expectedVersion());
                return;
            }
            String payload = objectMapper.writeValueAsString(job);
            redis.opsForList().leftPush(READY_LIST, payload);
            log.info("[DocumentSummaryJobQueue] enqueued: documentId={}, expectedVersion={}, attempts={}",
                    job.documentId(), job.expectedVersion(), job.attempts());
        } catch (Exception e) {
            log.warn("[DocumentSummaryJobQueue] enqueue failed, documentId={}", job.documentId(), e);
        }
    }

    public void clearDedup(long documentId, int expectedVersion) {
        if (documentId <= 0) return;
        redis.delete(dedupKey(documentId, expectedVersion));
    }

    private static String dedupKey(long documentId, int expectedVersion) {
        return DEDUP_PREFIX + documentId + ":v:" + expectedVersion;
    }
}

