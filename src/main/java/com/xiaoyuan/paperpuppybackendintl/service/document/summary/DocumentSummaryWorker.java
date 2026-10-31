package com.xiaoyuan.paperpuppybackendintl.service.document.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.config.openai.OpenAiDocumentSummaryProperties;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Document;
import com.xiaoyuan.paperpuppybackendintl.repository.DocumentRepository;
import com.xiaoyuan.paperpuppybackendintl.service.openai.OpenAiDocumentSummaryGenerator;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 队列消费者（多实例可并行消费）。
 *
 * <p>职责：对 Markdown 文档生成摘要并写回 documents.summary + documents.summary_version。</p>
 */
@Slf4j
@Component
public class DocumentSummaryWorker {

    private static final Duration PROCESSING_STALE = Duration.ofMinutes(10);
    private static final int RECOVER_BATCH = 50;

    // 全局并发控制（跨实例共享）
    private static final String GLOBAL_SEMAPHORE_KEY = "pp:sem:doc_summary";
    private static final int GLOBAL_PERMITS = 2;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final OpenAiDocumentSummaryGenerator generator;
    private final DocumentSummaryJobQueue queue;
    private final DocumentRepository documentRepository;
    private final RedissonClient redissonClient;
    private final TaskExecutor storageExecutor;
    private final OpenAiDocumentSummaryProperties props;

    private volatile boolean stopped = false;
    private RSemaphore semaphore;

    public DocumentSummaryWorker(StringRedisTemplate redis,
                                 @Qualifier("jackson2Mapper") ObjectMapper objectMapper,
                                 OpenAiDocumentSummaryGenerator generator,
                                 DocumentSummaryJobQueue queue,
                                 DocumentRepository documentRepository,
                                 RedissonClient redissonClient,
                                 @Qualifier("storageExecutor") TaskExecutor storageExecutor,
                                 OpenAiDocumentSummaryProperties props) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.generator = generator;
        this.queue = queue;
        this.documentRepository = documentRepository;
        this.redissonClient = redissonClient;
        this.storageExecutor = storageExecutor;
        this.props = props;
    }

    @PostConstruct
    public void start() {
        if (props == null || !props.enabled()) {
            log.info("[DocumentSummaryWorker] disabled by config (openai.document-summary.enabled=false)");
            return;
        }
        log.info("[DocumentSummaryWorker] starting: model={}, timeoutMs={}, maxInputChars={}, maxSummaryLen={}, maxAttempts={}",
                props.model(), props.timeoutMs(), props.maxInputChars(), props.maxSummaryLength(), props.maxAttempts());
        semaphore = redissonClient.getSemaphore(GLOBAL_SEMAPHORE_KEY);
        try {
            semaphore.trySetPermits(GLOBAL_PERMITS);
        } catch (Exception ignored) {
        }
        storageExecutor.execute(this::consumeLoop);
    }

    @PreDestroy
    public void stop() {
        stopped = true;
        log.info("[DocumentSummaryWorker] stopping...");
    }

    private void consumeLoop() {
        long errorBackoffMs = 200;
        while (!stopped && !Thread.currentThread().isInterrupted()) {
            try {
                String payload = redis.opsForList().rightPopAndLeftPush(
                        DocumentSummaryJobQueue.READY_LIST,
                        DocumentSummaryJobQueue.PROCESSING_LIST,
                        5,
                        TimeUnit.SECONDS
                );
                if (payload == null || payload.isBlank()) {
                    continue;
                }

                redis.opsForZSet().add(DocumentSummaryJobQueue.PROCESSING_ZSET, payload, System.currentTimeMillis());

                DocumentSummaryJobQueue.Job job = objectMapper.readValue(payload, DocumentSummaryJobQueue.Job.class);
                long documentId = job.documentId();
                int expectedVersion = job.expectedVersion();

                Document doc = documentRepository.findById(documentId).orElse(null);
                if (doc == null || doc.getId() == null) {
                    log.warn("[DocumentSummaryWorker] document not found, ack: documentId={}", documentId);
                    ack(payload, documentId, expectedVersion);
                    continue;
                }

                int docVersion = doc.getVersion() == null ? 1 : doc.getVersion();
                Integer summaryVersion = doc.getSummaryVersion();
                if (doc.getSummary() != null && !doc.getSummary().isBlank()
                        && summaryVersion != null
                        && summaryVersion >= docVersion) {
                    // 已是最新摘要
                    log.info("[DocumentSummaryWorker] already up-to-date, ack: documentId={}, docVersion={}, summaryVersion={}",
                            documentId, docVersion, summaryVersion);
                    ack(payload, documentId, expectedVersion);
                    continue;
                }

                // 若入队时的版本已经落后，优先按最新版本生成（去重靠 docId+version）
                if (expectedVersion > 0 && expectedVersion != docVersion) {
                    log.info("[DocumentSummaryWorker] version changed, requeue new version: documentId={}, expectedVersion={}, currentVersion={}",
                            documentId, expectedVersion, docVersion);
                    retryAsNewVersion(payload, job, docVersion);
                    continue;
                }

                boolean acquired = semaphore == null || semaphore.tryAcquire(Duration.ofSeconds(2));
                if (!acquired) {
                    requeue(payload, job);
                    continue;
                }

                try {
                    String text = doc.getDraftContent();
                    if (text == null || text.isBlank()) {
                        // 没有可摘要内容：直接 ack（避免空跑）
                        log.warn("[DocumentSummaryWorker] empty draftContent, skip: documentId={}, docVersion={}", documentId, docVersion);
                        ack(payload, documentId, expectedVersion);
                        continue;
                    }
                    log.info("[DocumentSummaryWorker] generating summary: documentId={}, docVersion={}, titleLen={}, draftLen={}",
                            documentId, docVersion,
                            doc.getTitle() == null ? 0 : doc.getTitle().length(),
                            text.length());
                    String summary = generator.generateSummary(doc.getTitle(), text);
                    if (summary == null || summary.isBlank()) {
                        log.warn("[DocumentSummaryWorker] summary empty, retry: documentId={}, docVersion={}, attempts={}",
                                documentId, docVersion, job.attempts());
                        retryOrFail(payload, job);
                        continue;
                    }

                    boolean ok = documentRepository.updateSummary(documentId, summary, docVersion, OffsetDateTime.now(), null);
                    if (ok) {
                        log.info("[DocumentSummaryWorker] summary updated: documentId={}, summaryLen={}, summaryVersion={}",
                                documentId, summary.length(), docVersion);
                        ack(payload, documentId, expectedVersion);
                    } else {
                        log.warn("[DocumentSummaryWorker] updateSummary failed, retry: documentId={}, docVersion={}, attempts={}",
                                documentId, docVersion, job.attempts());
                        retryOrFail(payload, job);
                    }
                } finally {
                    if (semaphore != null) {
                        try {
                            semaphore.release();
                        } catch (Exception ignored) {
                        }
                    }
                }
                errorBackoffMs = 200;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (stopped) {
                    log.info("[DocumentSummaryWorker] shutting down, exit consume loop");
                    return;
                }
                log.warn("[DocumentSummaryWorker] consume loop error", e);
                try {
                    Thread.sleep(Math.min(errorBackoffMs, 2000));
                    errorBackoffMs = Math.min(errorBackoffMs * 2, 2000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.info("[DocumentSummaryWorker] consume loop exited");
    }

    private void ack(String payload, long documentId, int expectedVersion) {
        redis.opsForList().remove(DocumentSummaryJobQueue.PROCESSING_LIST, 1, payload);
        redis.opsForZSet().remove(DocumentSummaryJobQueue.PROCESSING_ZSET, payload);
        queue.clearDedup(documentId, expectedVersion);
    }

    private void retryOrFail(String payload, DocumentSummaryJobQueue.Job job) {
        int attempts = job.attempts();
        int maxAttempts = props == null ? 3 : Math.max(1, props.maxAttempts());
        if (attempts + 1 >= maxAttempts) {
            ack(payload, job.documentId(), job.expectedVersion());
            return;
        }
        DocumentSummaryJobQueue.Job next = new DocumentSummaryJobQueue.Job(job.documentId(), job.expectedVersion(), attempts + 1);
        requeue(payload, next);
    }

    private void retryAsNewVersion(String payload, DocumentSummaryJobQueue.Job job, int newVersion) {
        try {
            // 移除旧 processing 项
            redis.opsForList().remove(DocumentSummaryJobQueue.PROCESSING_LIST, 1, payload);
            redis.opsForZSet().remove(DocumentSummaryJobQueue.PROCESSING_ZSET, payload);
            // 入新版本任务（dedup 由 queue 控制）
            queue.enqueue(new DocumentSummaryJobQueue.Job(job.documentId(), newVersion, job.attempts()));
        } catch (Exception e) {
            log.warn("[DocumentSummaryWorker] retryAsNewVersion failed, documentId={}", job.documentId(), e);
        }
    }

    private void requeue(String payload, DocumentSummaryJobQueue.Job next) {
        try {
            redis.opsForList().remove(DocumentSummaryJobQueue.PROCESSING_LIST, 1, payload);
            redis.opsForZSet().remove(DocumentSummaryJobQueue.PROCESSING_ZSET, payload);
            String nextPayload = objectMapper.writeValueAsString(next);
            redis.opsForList().leftPush(DocumentSummaryJobQueue.READY_LIST, nextPayload);
        } catch (Exception e) {
            log.warn("[DocumentSummaryWorker] requeue failed, documentId={}", next.documentId(), e);
        }
    }

    /**
     * 回收超时 processing 任务（实例崩溃/重启后可恢复）。
     */
    @Scheduled(fixedDelayString = "${app.summary.document.recover.delay-ms:60000}")
    public void recoverStaleProcessing() {
        long cutoff = System.currentTimeMillis() - PROCESSING_STALE.toMillis();
        Set<String> stuck = redis.opsForZSet().rangeByScore(DocumentSummaryJobQueue.PROCESSING_ZSET, 0, cutoff, 0, RECOVER_BATCH);
        if (stuck == null || stuck.isEmpty()) {
            return;
        }
        for (String payload : stuck) {
            if (payload == null || payload.isBlank()) continue;
            Long removed = redis.opsForList().remove(DocumentSummaryJobQueue.PROCESSING_LIST, 1, payload);
            redis.opsForZSet().remove(DocumentSummaryJobQueue.PROCESSING_ZSET, payload);
            if (removed != null && removed > 0) {
                redis.opsForList().leftPush(DocumentSummaryJobQueue.READY_LIST, payload);
            }
        }
    }
}

