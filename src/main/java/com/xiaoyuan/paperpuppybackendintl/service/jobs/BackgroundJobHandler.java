package com.xiaoyuan.paperpuppybackendintl.service.jobs;

import com.xiaoyuan.paperpuppybackendintl.model.entity.BackgroundJob;

public interface BackgroundJobHandler {

    String jobType();

    BackgroundJobExecutionResult handle(BackgroundJob job) throws Exception;
}
