package com.xiaoyuan.paperpuppybackendintl.service.admin;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelCatalogService;
import com.xiaoyuan.paperpuppybackendintl.service.toolbook.impl.ToolBookServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 管理员手动失效进程内缓存的统一入口。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMemoryCacheService {

    private static final String MODEL_CATALOG = "model-catalog";
    private static final String TOOL_BOOKS = "tool-books";

    private final ModelCatalogService modelCatalogService;
    private final ToolBookServiceImpl toolBookService;

    public List<CacheDescriptor> listCaches() {
        return refreshers().values().stream()
                .map(CacheRefresher::descriptor)
                .toList();
    }

    public CacheRefreshResult refresh(String rawCacheKey, Long adminUserId) {
        String cacheKey = normalizeCacheKey(rawCacheKey);
        CacheRefresher refresher = refreshers().get(cacheKey);
        if (refresher == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未知缓存: " + rawCacheKey);
        }
        return refreshOne(refresher, adminUserId);
    }

    public List<CacheRefreshResult> refreshAll(Long adminUserId) {
        List<CacheRefreshResult> results = new ArrayList<>();
        for (CacheRefresher refresher : refreshers().values()) {
            results.add(refreshOne(refresher, adminUserId));
        }
        return results;
    }

    private CacheRefreshResult refreshOne(CacheRefresher refresher, Long adminUserId) {
        OffsetDateTime refreshedAt = OffsetDateTime.now(ZoneOffset.UTC);
        refresher.evict();
        CacheDescriptor descriptor = refresher.descriptor();
        log.info("admin evicted memory cache: cacheKey={}, adminUserId={}, refreshedAt={}",
                descriptor.cacheKey(), adminUserId, refreshedAt);
        return new CacheRefreshResult(
                descriptor.cacheKey(),
                true,
                "evicted",
                refreshedAt
        );
    }

    private Map<String, CacheRefresher> refreshers() {
        Map<String, CacheRefresher> out = new LinkedHashMap<>();
        register(out,
                MODEL_CATALOG,
                "Model catalog",
                "AI 模型目录与 token 成本观察配置",
                modelCatalogService::evictCache);
        register(out,
                TOOL_BOOKS,
                "Tool books",
                "工具书 manifest 和节点目录",
                toolBookService::evictCache);
        return out;
    }

    private void register(Map<String, CacheRefresher> out,
                          String cacheKey,
                          String displayName,
                          String description,
                          Runnable evictAction) {
        out.put(cacheKey, new CacheRefresher(
                new CacheDescriptor(cacheKey, displayName, description),
                evictAction
        ));
    }

    private String normalizeCacheKey(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "cacheKey 不能为空");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record CacheDescriptor(String cacheKey,
                                  String displayName,
                                  String description) {
    }

    public record CacheRefreshResult(String cacheKey,
                                     boolean refreshed,
                                     String message,
                                     OffsetDateTime refreshedAt) {
    }

    private record CacheRefresher(CacheDescriptor descriptor, Runnable evictAction) {

        private void evict() {
            evictAction.run();
        }
    }
}
