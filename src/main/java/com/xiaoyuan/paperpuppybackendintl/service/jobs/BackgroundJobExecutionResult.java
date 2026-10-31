package com.xiaoyuan.paperpuppybackendintl.service.jobs;

import java.util.Map;

public record BackgroundJobExecutionResult(Object result) {

    public static BackgroundJobExecutionResult empty() {
        return new BackgroundJobExecutionResult(Map.of());
    }

    public static BackgroundJobExecutionResult of(Object result) {
        return new BackgroundJobExecutionResult(result == null ? Map.of() : result);
    }
}
