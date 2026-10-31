package com.xiaoyuan.paperpuppybackendintl.controller;

import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.common.ResultUtils;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelResolutionService;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelResolutionService.AvailableModelDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelResolutionService modelResolutionService;

    @GetMapping("/available")
    @AuthCheck
    public BaseResponse<List<AvailableModelDTO>> getAvailableModels(@CurrentUserId Long userId) {
        List<AvailableModelDTO> models = modelResolutionService.getAvailableModels(userId);
        return ResultUtils.success(models);
    }
}
