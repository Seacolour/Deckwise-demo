package com.xiaoyuan.paperpuppybackendintl.service.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.config.BackgroundJobProperties;
import com.xiaoyuan.paperpuppybackendintl.model.entity.BackgroundJob;
import com.xiaoyuan.paperpuppybackendintl.model.enums.BackgroundJobStatus;
import com.xiaoyuan.paperpuppybackendintl.repository.BackgroundJobRepository;
import org.jooq.JSONB;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackgroundJobServiceTest {

    @Test
    void runDueJobsDispatchesRegisteredHandlerAndMarksSucceeded() throws Exception {
        BackgroundJobRepository repository = mock(BackgroundJobRepository.class);
        BackgroundJobHandler handler = mock(BackgroundJobHandler.class);
        when(handler.jobType()).thenReturn("file.gc");
        BackgroundJobService service = new BackgroundJobService(
                repository,
                new BackgroundJobProperties(),
                new ObjectMapper(),
                List.of(handler));

        BackgroundJob job = job("file.gc");
        when(repository.claimDueJobs(any(String.class), anyCollection(), eq(10), any()))
                .thenReturn(List.of(job));
        when(handler.handle(job)).thenReturn(BackgroundJobExecutionResult.of(Map.of("status", "ok")));

        int processed = service.runDueJobs();

        assertEquals(1, processed);
        verify(handler).handle(job);
        verify(repository).markSucceeded(eq(101L), any(JSONB.class));
        verify(repository, never()).markFailedOrRetry(eq(101L), any(), any(), any());
    }

    @Test
    void runDueJobsRetriesWhenHandlerFails() throws Exception {
        BackgroundJobRepository repository = mock(BackgroundJobRepository.class);
        BackgroundJobHandler handler = mock(BackgroundJobHandler.class);
        when(handler.jobType()).thenReturn("file.gc");
        BackgroundJobProperties properties = new BackgroundJobProperties();
        properties.setInitialRetryDelaySeconds(1);
        BackgroundJobService service = new BackgroundJobService(
                repository,
                properties,
                new ObjectMapper(),
                List.of(handler));

        BackgroundJob job = job("file.gc");
        when(repository.claimDueJobs(any(String.class), anyCollection(), eq(10), any()))
                .thenReturn(List.of(job));
        when(handler.handle(job)).thenThrow(new IllegalStateException("boom"));

        int processed = service.runDueJobs();

        assertEquals(1, processed);
        verify(repository).markFailedOrRetry(eq(101L), eq("boom"), any(JSONB.class), any());
        verify(repository, never()).markSucceeded(eq(101L), any(JSONB.class));
    }

    @Test
    void enqueueCanExtendExistingPendingDedupJob() {
        BackgroundJobRepository repository = mock(BackgroundJobRepository.class);
        BackgroundJobService service = new BackgroundJobService(
                repository,
                new BackgroundJobProperties(),
                new ObjectMapper(),
                List.of());
        OffsetDateTime nextRunAt = OffsetDateTime.parse("2026-06-02T00:00:00Z");
        BackgroundJob job = job("file.gc");
        job.setStatus(BackgroundJobStatus.PENDING.getValue());
        job.setDedupKey("file.gc:7:51");
        job.setNextRunAt(nextRunAt);
        when(repository.enqueueOrExtendPending(any(BackgroundJob.class))).thenReturn(job);

        BackgroundJob result = service.enqueue(
                "file.gc",
                "file.gc:7:51",
                Map.of("fileId", 51),
                7L,
                0,
                null,
                nextRunAt,
                true);

        assertEquals(101L, result.getId());
        verify(repository).enqueueOrExtendPending(any(BackgroundJob.class));
        verify(repository, never()).enqueue(any(BackgroundJob.class));
    }

    private static BackgroundJob job(String jobType) {
        BackgroundJob job = new BackgroundJob();
        job.setId(101L);
        job.setJobType(jobType);
        job.setStatus(BackgroundJobStatus.RUNNING.getValue());
        job.setAttempts(1);
        job.setMaxAttempts(5);
        job.setPayload(JSONB.valueOf("{}"));
        return job;
    }
}
