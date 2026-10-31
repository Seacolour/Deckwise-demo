package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.BackgroundJob;
import org.jooq.JSONB;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BackgroundJobRepository {

    BackgroundJob enqueue(BackgroundJob job);

    BackgroundJob enqueueOrExtendPending(BackgroundJob job);

    Optional<BackgroundJob> findActiveByDedupKey(String jobType, String dedupKey);

    List<BackgroundJob> claimDueJobs(String workerId, Collection<String> jobTypes, int limit, OffsetDateTime now);

    Optional<BackgroundJob> markSucceeded(Long jobId, JSONB result);

    Optional<BackgroundJob> markFailedOrRetry(Long jobId, String errorMessage, JSONB result, OffsetDateTime nextRunAt);

    int recoverStaleRunningJobs(OffsetDateTime cutoff, OffsetDateTime nextRunAt);
}
