package com.xiaoyuan.paperpuppybackendintl.service.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.config.BackgroundJobProperties;
import com.xiaoyuan.paperpuppybackendintl.model.entity.BackgroundJob;
import com.xiaoyuan.paperpuppybackendintl.repository.BackgroundJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.jooq.JSONB;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BackgroundJobService {

    private final BackgroundJobRepository repository;
    private final BackgroundJobProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, BackgroundJobHandler> handlers;
    private final String workerId;

    public BackgroundJobService(BackgroundJobRepository repository,
            BackgroundJobProperties properties,
            ObjectMapper objectMapper,
            List<BackgroundJobHandler> handlers) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.handlers = handlers == null
                ? Map.of()
                : handlers.stream()
                        .filter(handler -> handler != null && handler.jobType() != null && !handler.jobType().isBlank())
                        .collect(Collectors.toMap(
                                handler -> handler.jobType().trim(),
                                Function.identity(),
                                (first, second) -> first,
                                LinkedHashMap::new));
        this.workerId = buildWorkerId();
    }

    public BackgroundJob enqueue(String jobType, String dedupKey, Object payload, Long createdBy) {
        return enqueue(jobType, dedupKey, payload, createdBy, 0, null, null);
    }

    public BackgroundJob enqueue(String jobType,
            String dedupKey,
            Object payload,
            Long createdBy,
            int priority,
            Integer maxAttempts,
            OffsetDateTime nextRunAt) {
        return enqueue(jobType, dedupKey, payload, createdBy, priority, maxAttempts, nextRunAt, false);
    }

    public BackgroundJob enqueue(String jobType,
            String dedupKey,
            Object payload,
            Long createdBy,
            int priority,
            Integer maxAttempts,
            OffsetDateTime nextRunAt,
            boolean extendPending) {
        BackgroundJob job = new BackgroundJob();
        job.setJobType(requireJobType(jobType));
        job.setDedupKey(normalizeBlankToNull(dedupKey));
        job.setPriority(priority);
        job.setPayload(toJsonb(payload));
        job.setResult(toJsonb(Map.of()));
        job.setMetadata(toJsonb(Map.of()));
        job.setCreatedBy(createdBy);
        job.setMaxAttempts(safeMaxAttempts(maxAttempts));
        job.setNextRunAt(nextRunAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : nextRunAt);
        BackgroundJob enqueued = extendPending
                ? repository.enqueueOrExtendPending(job)
                : repository.enqueue(job);
        if (enqueued != null) {
            log.info("[BackgroundJob] enqueued or active: id={}, type={}, dedupKey={}, status={}, nextRunAt={}",
                    enqueued.getId(),
                    enqueued.getJobType(),
                    enqueued.getDedupKey(),
                    enqueued.getStatus(),
                    enqueued.getNextRunAt());
        }
        return enqueued;
    }

    public void enqueueAfterCommit(String jobType, String dedupKey, Object payload, Long createdBy) {
        enqueueAfterCommit(jobType, dedupKey, payload, createdBy, null, false);
    }

    public void enqueueAfterCommit(String jobType,
            String dedupKey,
            Object payload,
            Long createdBy,
            OffsetDateTime nextRunAt,
            boolean extendPending) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueue(jobType, dedupKey, payload, createdBy, 0, null, nextRunAt, extendPending);
                }
            });
            return;
        }
        enqueue(jobType, dedupKey, payload, createdBy, 0, null, nextRunAt, extendPending);
    }

    public int runDueJobs() {
        if (!properties.isEnabled() || handlers.isEmpty()) {
            return 0;
        }
        Collection<String> jobTypes = handlers.keySet();
        List<BackgroundJob> jobs = repository.claimDueJobs(
                workerId,
                jobTypes,
                safeBatchSize(),
                OffsetDateTime.now(ZoneOffset.UTC));
        for (BackgroundJob job : jobs) {
            executeClaimedJob(job);
        }
        return jobs.size();
    }

    public int recoverStaleRunningJobs() {
        if (!properties.isEnabled()) {
            return 0;
        }
        long staleSeconds = Math.max(60, properties.getStaleRunningSeconds());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return repository.recoverStaleRunningJobs(now.minusSeconds(staleSeconds), now);
    }

    private void executeClaimedJob(BackgroundJob job) {
        if (job == null || job.getId() == null) {
            return;
        }
        BackgroundJobHandler handler = handlers.get(job.getJobType());
        if (handler == null) {
            repository.markFailedOrRetry(
                    job.getId(),
                    "No background job handler registered for type: " + job.getJobType(),
                    toJsonb(Map.of("status", "handler_missing")),
                    nextRetryAt(job));
            return;
        }
        try {
            BackgroundJobExecutionResult result = handler.handle(job);
            repository.markSucceeded(job.getId(), toJsonb(result == null ? Map.of() : result.result()));
        } catch (Exception e) {
            log.warn("[BackgroundJob] job failed: id={}, type={}, attempts={}, err={}",
                    job.getId(),
                    job.getJobType(),
                    job.getAttempts(),
                    e.getMessage(),
                    e);
            repository.markFailedOrRetry(
                    job.getId(),
                    e.getMessage(),
                    toJsonb(Map.of(
                            "status", "failed",
                            "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())),
                    nextRetryAt(job));
        }
    }

    private OffsetDateTime nextRetryAt(BackgroundJob job) {
        int attempts = job == null || job.getAttempts() == null ? 1 : Math.max(1, job.getAttempts());
        long initial = Math.max(1, properties.getInitialRetryDelaySeconds());
        long max = Math.max(initial, properties.getMaxRetryDelaySeconds());
        long delay = initial;
        for (int i = 1; i < attempts; i++) {
            if (delay >= max) {
                break;
            }
            delay = Math.min(max, delay * 2);
        }
        return OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(delay);
    }

    private JSONB toJsonb(Object value) {
        if (value instanceof JSONB jsonb) {
            return jsonb;
        }
        Object safeValue = value == null ? Map.of() : value;
        try {
            return JSONB.valueOf(objectMapper.writeValueAsString(safeValue));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize background job JSON payload", e);
        }
    }

    private int safeBatchSize() {
        return Math.max(1, Math.min(properties.getBatchSize(), 100));
    }

    private int safeMaxAttempts(Integer maxAttempts) {
        int configured = maxAttempts == null ? properties.getMaxAttempts() : maxAttempts;
        return Math.max(1, configured);
    }

    private static String requireJobType(String jobType) {
        if (jobType == null || jobType.isBlank()) {
            throw new IllegalArgumentException("jobType must not be blank");
        }
        return jobType.trim();
    }

    private static String normalizeBlankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String buildWorkerId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown-host";
        }
        return host + "-" + UUID.randomUUID();
    }
}
