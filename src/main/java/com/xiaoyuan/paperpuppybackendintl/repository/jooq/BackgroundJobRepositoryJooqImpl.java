package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.records.BackgroundJobsRecord;
import com.xiaoyuan.paperpuppybackendintl.model.entity.BackgroundJob;
import com.xiaoyuan.paperpuppybackendintl.model.enums.BackgroundJobStatus;
import com.xiaoyuan.paperpuppybackendintl.repository.BackgroundJobRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.BackgroundJobs.BACKGROUND_JOBS;
import static org.jooq.impl.DSL.using;

@Repository
@Primary
@RequiredArgsConstructor
public class BackgroundJobRepositoryJooqImpl extends BaseJooqRepository<BackgroundJobsRecord, BackgroundJob>
        implements BackgroundJobRepository {

    private static final JSONB EMPTY_JSON_OBJECT = JSONB.valueOf("{}");
    private static final List<String> ACTIVE_STATUSES = List.of(
            BackgroundJobStatus.PENDING.getValue(),
            BackgroundJobStatus.RUNNING.getValue());

    private final DSLContext dsl;

    @Override
    public BackgroundJob enqueue(BackgroundJob job) {
        return enqueueInternal(job, false);
    }

    @Override
    public BackgroundJob enqueueOrExtendPending(BackgroundJob job) {
        return enqueueInternal(job, true);
    }

    private BackgroundJob enqueueInternal(BackgroundJob job, boolean extendPending) {
        if (job == null || isBlank(job.getJobType())) {
            throw new IllegalArgumentException("jobType must not be blank");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime nextRunAt = job.getNextRunAt() == null ? now : job.getNextRunAt();
        Integer priority = job.getPriority() == null ? 0 : job.getPriority();
        Integer maxAttempts = job.getMaxAttempts() == null || job.getMaxAttempts() <= 0 ? 5 : job.getMaxAttempts();
        String dedupKey = normalizeBlankToNull(job.getDedupKey());

        Optional<BackgroundJob> inserted = dsl.insertInto(BACKGROUND_JOBS)
                .set(BACKGROUND_JOBS.JOB_TYPE, job.getJobType().trim())
                .set(BACKGROUND_JOBS.DEDUP_KEY, dedupKey)
                .set(BACKGROUND_JOBS.STATUS, BackgroundJobStatus.PENDING.getValue())
                .set(BACKGROUND_JOBS.PRIORITY, priority)
                .set(BACKGROUND_JOBS.PAYLOAD, defaultJson(job.getPayload()))
                .set(BACKGROUND_JOBS.RESULT, defaultJson(job.getResult()))
                .set(BACKGROUND_JOBS.METADATA, defaultJson(job.getMetadata()))
                .set(BACKGROUND_JOBS.ERROR_MESSAGE, truncate(job.getErrorMessage()))
                .set(BACKGROUND_JOBS.ATTEMPTS, 0)
                .set(BACKGROUND_JOBS.MAX_ATTEMPTS, maxAttempts)
                .set(BACKGROUND_JOBS.NEXT_RUN_AT, nextRunAt)
                .set(BACKGROUND_JOBS.CREATED_BY, job.getCreatedBy())
                .onConflict(BACKGROUND_JOBS.JOB_TYPE, BACKGROUND_JOBS.DEDUP_KEY)
                .where(BACKGROUND_JOBS.DEDUP_KEY.isNotNull()
                        .and(BACKGROUND_JOBS.STATUS.in(ACTIVE_STATUSES)))
                .doNothing()
                .returning()
                .fetchOptional(this::map);

        if (inserted.isPresent()) {
            return inserted.get();
        }
        if (dedupKey != null) {
            Optional<BackgroundJob> active = findActiveByDedupKey(job.getJobType().trim(), dedupKey);
            if (extendPending && active.isPresent()) {
                return extendPendingJob(active.get(), job, nextRunAt, priority, maxAttempts);
            }
            return active.orElse(null);
        }
        return null;
    }

    private BackgroundJob extendPendingJob(BackgroundJob active,
            BackgroundJob requested,
            OffsetDateTime nextRunAt,
            Integer priority,
            Integer maxAttempts) {
        if (active == null || active.getId() == null) {
            return active;
        }
        if (!BackgroundJobStatus.PENDING.getValue().equals(active.getStatus())) {
            return active;
        }
        OffsetDateTime activeNextRunAt = active.getNextRunAt();
        if (nextRunAt == null || (activeNextRunAt != null && !nextRunAt.isAfter(activeNextRunAt))) {
            return active;
        }
        int effectivePriority = Math.max(active.getPriority() == null ? 0 : active.getPriority(), priority == null ? 0 : priority);
        int effectiveMaxAttempts = Math.max(
                active.getMaxAttempts() == null ? 1 : active.getMaxAttempts(),
                maxAttempts == null ? 1 : maxAttempts);
        Optional<BackgroundJob> updated = dsl.update(BACKGROUND_JOBS)
                .set(BACKGROUND_JOBS.NEXT_RUN_AT, nextRunAt)
                .set(BACKGROUND_JOBS.PAYLOAD, defaultJson(requested.getPayload()))
                .set(BACKGROUND_JOBS.PRIORITY, effectivePriority)
                .set(BACKGROUND_JOBS.MAX_ATTEMPTS, effectiveMaxAttempts)
                .set(BACKGROUND_JOBS.CREATED_BY, requested.getCreatedBy())
                .where(BACKGROUND_JOBS.ID.eq(active.getId())
                        .and(BACKGROUND_JOBS.STATUS.eq(BackgroundJobStatus.PENDING.getValue()))
                        .and(BACKGROUND_JOBS.NEXT_RUN_AT.lt(nextRunAt)))
                .returning()
                .fetchOptional(this::map);
        if (updated.isPresent()) {
            return updated.get();
        }
        return findActiveByDedupKey(active.getJobType(), active.getDedupKey())
                .orElse(active);
    }

    @Override
    public Optional<BackgroundJob> findActiveByDedupKey(String jobType, String dedupKey) {
        if (isBlank(jobType) || isBlank(dedupKey)) {
            return Optional.empty();
        }
        return dsl.selectFrom(BACKGROUND_JOBS)
                .where(BACKGROUND_JOBS.JOB_TYPE.eq(jobType.trim())
                        .and(BACKGROUND_JOBS.DEDUP_KEY.eq(dedupKey.trim()))
                        .and(BACKGROUND_JOBS.STATUS.in(ACTIVE_STATUSES)))
                .orderBy(BACKGROUND_JOBS.ID.desc())
                .limit(1)
                .fetchOptional(this::map);
    }

    @Override
    public List<BackgroundJob> claimDueJobs(String workerId,
            Collection<String> jobTypes,
            int limit,
            OffsetDateTime now) {
        if (isBlank(workerId) || jobTypes == null || jobTypes.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<String> safeTypes = jobTypes.stream()
                .filter(s -> !isBlank(s))
                .map(String::trim)
                .distinct()
                .toList();
        if (safeTypes.isEmpty()) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        OffsetDateTime effectiveNow = now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now;

        return dsl.transactionResult(configuration -> {
            DSLContext tx = using(configuration);
            List<Long> ids = tx.select(BACKGROUND_JOBS.ID)
                    .from(BACKGROUND_JOBS)
                    .where(BACKGROUND_JOBS.STATUS.eq(BackgroundJobStatus.PENDING.getValue())
                            .and(BACKGROUND_JOBS.JOB_TYPE.in(safeTypes))
                            .and(BACKGROUND_JOBS.NEXT_RUN_AT.le(effectiveNow))
                            .and(BACKGROUND_JOBS.ATTEMPTS.lt(BACKGROUND_JOBS.MAX_ATTEMPTS)))
                    .orderBy(BACKGROUND_JOBS.PRIORITY.desc(),
                            BACKGROUND_JOBS.NEXT_RUN_AT.asc(),
                            BACKGROUND_JOBS.ID.asc())
                    .limit(safeLimit)
                    .forUpdate()
                    .skipLocked()
                    .fetch(BACKGROUND_JOBS.ID);
            if (ids.isEmpty()) {
                return List.of();
            }
            return tx.update(BACKGROUND_JOBS)
                    .set(BACKGROUND_JOBS.STATUS, BackgroundJobStatus.RUNNING.getValue())
                    .set(BACKGROUND_JOBS.LOCKED_BY, workerId.trim())
                    .set(BACKGROUND_JOBS.LOCKED_AT, effectiveNow)
                    .set(BACKGROUND_JOBS.STARTED_AT, effectiveNow)
                    .set(BACKGROUND_JOBS.FINISHED_AT, (OffsetDateTime) null)
                    .set(BACKGROUND_JOBS.ERROR_MESSAGE, (String) null)
                    .set(BACKGROUND_JOBS.ATTEMPTS, BACKGROUND_JOBS.ATTEMPTS.plus(1))
                    .where(BACKGROUND_JOBS.ID.in(ids)
                            .and(BACKGROUND_JOBS.STATUS.eq(BackgroundJobStatus.PENDING.getValue())))
                    .returning()
                    .fetch(this::map);
        });
    }

    @Override
    public Optional<BackgroundJob> markSucceeded(Long jobId, JSONB result) {
        if (jobId == null || jobId <= 0) {
            return Optional.empty();
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return dsl.update(BACKGROUND_JOBS)
                .set(BACKGROUND_JOBS.STATUS, BackgroundJobStatus.SUCCEEDED.getValue())
                .set(BACKGROUND_JOBS.RESULT, defaultJson(result))
                .set(BACKGROUND_JOBS.ERROR_MESSAGE, (String) null)
                .set(BACKGROUND_JOBS.LOCKED_BY, (String) null)
                .set(BACKGROUND_JOBS.LOCKED_AT, (OffsetDateTime) null)
                .set(BACKGROUND_JOBS.FINISHED_AT, now)
                .where(BACKGROUND_JOBS.ID.eq(jobId)
                        .and(BACKGROUND_JOBS.STATUS.eq(BackgroundJobStatus.RUNNING.getValue())))
                .returning()
                .fetchOptional(this::map);
    }

    @Override
    public Optional<BackgroundJob> markFailedOrRetry(Long jobId,
            String errorMessage,
            JSONB result,
            OffsetDateTime nextRunAt) {
        if (jobId == null || jobId <= 0) {
            return Optional.empty();
        }
        return dsl.transactionResult(configuration -> {
            DSLContext tx = using(configuration);
            BackgroundJobsRecord current = tx.selectFrom(BACKGROUND_JOBS)
                    .where(BACKGROUND_JOBS.ID.eq(jobId)
                            .and(BACKGROUND_JOBS.STATUS.eq(BackgroundJobStatus.RUNNING.getValue())))
                    .forUpdate()
                    .fetchOne();
            if (current == null) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            boolean exhausted = current.getAttempts() != null
                    && current.getMaxAttempts() != null
                    && current.getAttempts() >= current.getMaxAttempts();
            String nextStatus = exhausted
                    ? BackgroundJobStatus.DEAD.getValue()
                    : BackgroundJobStatus.PENDING.getValue();
            OffsetDateTime effectiveNextRunAt = nextRunAt == null ? now : nextRunAt;
            BackgroundJob updated = tx.update(BACKGROUND_JOBS)
                    .set(BACKGROUND_JOBS.STATUS, nextStatus)
                    .set(BACKGROUND_JOBS.RESULT, defaultJson(result))
                    .set(BACKGROUND_JOBS.ERROR_MESSAGE, truncate(errorMessage))
                    .set(BACKGROUND_JOBS.NEXT_RUN_AT, effectiveNextRunAt)
                    .set(BACKGROUND_JOBS.LOCKED_BY, (String) null)
                    .set(BACKGROUND_JOBS.LOCKED_AT, (OffsetDateTime) null)
                    .set(BACKGROUND_JOBS.FINISHED_AT, now)
                    .where(BACKGROUND_JOBS.ID.eq(jobId))
                    .returning()
                    .fetchOne(this::map);
            return Optional.ofNullable(updated);
        });
    }

    @Override
    public int recoverStaleRunningJobs(OffsetDateTime cutoff, OffsetDateTime nextRunAt) {
        if (cutoff == null) {
            return 0;
        }
        OffsetDateTime effectiveNextRunAt = nextRunAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : nextRunAt;
        int retried = dsl.update(BACKGROUND_JOBS)
                .set(BACKGROUND_JOBS.STATUS, BackgroundJobStatus.PENDING.getValue())
                .set(BACKGROUND_JOBS.NEXT_RUN_AT, effectiveNextRunAt)
                .set(BACKGROUND_JOBS.ERROR_MESSAGE, "Recovered stale running job")
                .set(BACKGROUND_JOBS.LOCKED_BY, (String) null)
                .set(BACKGROUND_JOBS.LOCKED_AT, (OffsetDateTime) null)
                .set(BACKGROUND_JOBS.FINISHED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .where(BACKGROUND_JOBS.STATUS.eq(BackgroundJobStatus.RUNNING.getValue())
                        .and(BACKGROUND_JOBS.LOCKED_AT.le(cutoff))
                        .and(BACKGROUND_JOBS.ATTEMPTS.lt(BACKGROUND_JOBS.MAX_ATTEMPTS)))
                .execute();
        int dead = dsl.update(BACKGROUND_JOBS)
                .set(BACKGROUND_JOBS.STATUS, BackgroundJobStatus.DEAD.getValue())
                .set(BACKGROUND_JOBS.ERROR_MESSAGE, "Recovered stale running job after max attempts")
                .set(BACKGROUND_JOBS.LOCKED_BY, (String) null)
                .set(BACKGROUND_JOBS.LOCKED_AT, (OffsetDateTime) null)
                .set(BACKGROUND_JOBS.FINISHED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .where(BACKGROUND_JOBS.STATUS.eq(BackgroundJobStatus.RUNNING.getValue())
                        .and(BACKGROUND_JOBS.LOCKED_AT.le(cutoff))
                        .and(BACKGROUND_JOBS.ATTEMPTS.ge(BACKGROUND_JOBS.MAX_ATTEMPTS)))
                .execute();
        return retried + dead;
    }

    @Override
    protected BackgroundJob map(BackgroundJobsRecord record) {
        if (record == null) {
            return null;
        }
        BackgroundJob job = new BackgroundJob();
        job.setId(record.getId());
        job.setJobType(record.getJobType());
        job.setDedupKey(record.getDedupKey());
        job.setStatus(record.getStatus());
        job.setPriority(record.getPriority());
        job.setPayload(record.getPayload());
        job.setResult(record.getResult());
        job.setMetadata(record.getMetadata());
        job.setErrorMessage(record.getErrorMessage());
        job.setAttempts(record.getAttempts());
        job.setMaxAttempts(record.getMaxAttempts());
        job.setNextRunAt(record.getNextRunAt());
        job.setLockedBy(record.getLockedBy());
        job.setLockedAt(record.getLockedAt());
        job.setStartedAt(record.getStartedAt());
        job.setFinishedAt(record.getFinishedAt());
        job.setCreatedBy(record.getCreatedBy());
        job.setCreatedAt(record.getCreatedAt());
        job.setUpdatedAt(record.getUpdatedAt());
        return job;
    }

    private JSONB defaultJson(JSONB jsonb) {
        return jsonb == null ? EMPTY_JSON_OBJECT : jsonb;
    }

    private static String normalizeBlankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 4_000) {
            return value;
        }
        return value.substring(0, 4_000);
    }
}
