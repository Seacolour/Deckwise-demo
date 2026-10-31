package com.xiaoyuan.paperpuppybackendintl.service.file.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FileGarbageCollectionResult;
import com.xiaoyuan.paperpuppybackendintl.model.entity.BackgroundJob;
import com.xiaoyuan.paperpuppybackendintl.service.file.FileGarbageCollectionService;
import com.xiaoyuan.paperpuppybackendintl.service.jobs.BackgroundJobExecutionResult;
import com.xiaoyuan.paperpuppybackendintl.service.jobs.BackgroundJobHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileGarbageCollectionJobHandler implements BackgroundJobHandler {

    private final ObjectMapper objectMapper;
    private final FileGarbageCollectionService fileGarbageCollectionService;

    @Override
    public String jobType() {
        return FileGarbageCollectionService.JOB_TYPE;
    }

    @Override
    public BackgroundJobExecutionResult handle(BackgroundJob job) throws Exception {
        String rawPayload = job == null || job.getPayload() == null ? "{}" : job.getPayload().data();
        FileGarbageCollectionServiceImpl.FileGcJobPayload payload = objectMapper.readValue(
                rawPayload,
                FileGarbageCollectionServiceImpl.FileGcJobPayload.class);
        if (payload.ownerUserId() == null || payload.fileId() == null) {
            throw new IllegalArgumentException("file.gc payload requires ownerUserId and fileId");
        }
        FileGarbageCollectionResult result = fileGarbageCollectionService.tryGarbageCollect(
                payload.ownerUserId(),
                payload.fileId(),
                payload.reason());
        return BackgroundJobExecutionResult.of(result);
    }
}
