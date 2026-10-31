package com.xiaoyuan.paperpuppybackendintl.controller.admin;

import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.common.ResultUtils;
import com.xiaoyuan.paperpuppybackendintl.service.admin.AdminMemoryCacheService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员进程内缓存维护接口。
 */
@RestController
@RequestMapping("/admin/caches")
@RequiredArgsConstructor
@AuthCheck(mustRole = "admin")
public class AdminCacheController {

    private final AdminMemoryCacheService adminMemoryCacheService;

    @GetMapping
    public BaseResponse<List<AdminMemoryCacheService.CacheDescriptor>> listCaches() {
        return ResultUtils.success(adminMemoryCacheService.listCaches());
    }

    @PostMapping("/{cacheKey}/refresh")
    @Operation(operationId = "refreshAdminCache")
    public BaseResponse<AdminMemoryCacheService.CacheRefreshResult> refresh(
            @PathVariable("cacheKey") String cacheKey,
            @CurrentUserId Long adminUserId) {
        return ResultUtils.success(adminMemoryCacheService.refresh(cacheKey, adminUserId));
    }

    @PostMapping("/refresh-all")
    public BaseResponse<List<AdminMemoryCacheService.CacheRefreshResult>> refreshAll(@CurrentUserId Long adminUserId) {
        return ResultUtils.success(adminMemoryCacheService.refreshAll(adminUserId));
    }
}
