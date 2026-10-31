package com.xiaoyuan.paperpuppybackendintl.controller.user;

import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.common.ResultUtils;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.ModelCredentialStatus;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.ModelCredentialUpsertRequest;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user/model-credentials")
@AuthCheck
@RequiredArgsConstructor
public class ModelCredentialController {

    private final ModelCredentialService service;

    @GetMapping
    public BaseResponse<List<ModelCredentialStatus>> list(@CurrentUserId Long userId) {
        return ResultUtils.success(service.listStatus(userId));
    }

    @PutMapping("/{providerId}")
    public BaseResponse<ModelCredentialStatus> upsert(@PathVariable String providerId,
                                                       @RequestBody ModelCredentialUpsertRequest body,
                                                       @CurrentUserId Long userId) {
        return ResultUtils.success(service.upsert(userId, providerId, body == null ? null : body.apiKey()));
    }

    @DeleteMapping("/{providerId}")
    public BaseResponse<Boolean> delete(@PathVariable String providerId,
                                        @CurrentUserId Long userId) {
        return ResultUtils.success(service.delete(userId, providerId));
    }
}
