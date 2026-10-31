package com.xiaoyuan.paperpuppybackendintl.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BackgroundJobStatus {
    PENDING("pending"),
    RUNNING("running"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    DEAD("dead"),
    CANCELLED("cancelled");

    private final String value;
}
