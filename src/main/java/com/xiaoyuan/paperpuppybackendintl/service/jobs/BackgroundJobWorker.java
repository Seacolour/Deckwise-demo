package com.xiaoyuan.paperpuppybackendintl.service.jobs;

import com.xiaoyuan.paperpuppybackendintl.config.BackgroundJobProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BackgroundJobWorker {

    private final BackgroundJobProperties properties;
    private final BackgroundJobService backgroundJobService;

    @Scheduled(fixedDelayString = "${app.background-jobs.poll-delay-ms:5000}")
    public void runDueJobs() {
        if (!properties.isEnabled()) {
            return;
        }
        int claimed = backgroundJobService.runDueJobs();
        if (claimed > 0) {
            log.info("[BackgroundJobWorker] processed due jobs: count={}", claimed);
        }
    }

    @Scheduled(fixedDelayString = "${app.background-jobs.recover-delay-ms:60000}")
    public void recoverStaleRunningJobs() {
        if (!properties.isEnabled()) {
            return;
        }
        int recovered = backgroundJobService.recoverStaleRunningJobs();
        if (recovered > 0) {
            log.warn("[BackgroundJobWorker] recovered stale running jobs: count={}", recovered);
        }
    }
}
