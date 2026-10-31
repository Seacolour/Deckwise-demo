package com.xiaoyuan.paperpuppybackendintl.service.admin;

import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelCatalogService;
import com.xiaoyuan.paperpuppybackendintl.service.toolbook.impl.ToolBookServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminMemoryCacheServiceTest {

    @Test
    void refreshModelCatalogEvictsItsCache() {
        ModelCatalogService modelCatalogService = mock(ModelCatalogService.class);
        AdminMemoryCacheService service = new AdminMemoryCacheService(
                modelCatalogService,
                mock(ToolBookServiceImpl.class));

        AdminMemoryCacheService.CacheRefreshResult result = service.refresh("model-catalog", 7L);

        assertEquals("model-catalog", result.cacheKey());
        assertTrue(result.refreshed());
        verify(modelCatalogService).evictCache();
    }

    @Test
    void refreshAllEvictsEveryRegisteredCache() {
        ModelCatalogService modelCatalogService = mock(ModelCatalogService.class);
        ToolBookServiceImpl toolBookService = mock(ToolBookServiceImpl.class);
        AdminMemoryCacheService service = new AdminMemoryCacheService(
                modelCatalogService,
                toolBookService);

        var results = service.refreshAll(7L);

        assertEquals(2, results.size());
        verify(modelCatalogService).evictCache();
        verify(toolBookService).evictCache();
    }

    @Test
    void refreshRejectsUnknownCache() {
        AdminMemoryCacheService service = newService();

        assertThrows(BusinessException.class, () -> service.refresh("missing-cache", 7L));
    }

    @Test
    void listCachesReturnsRegisteredCacheDescriptors() {
        AdminMemoryCacheService service = newService();

        var descriptors = service.listCaches();

        assertEquals(2, descriptors.size());
        assertEquals("model-catalog", descriptors.getFirst().cacheKey());
        assertTrue(descriptors.stream().anyMatch(descriptor -> "tool-books".equals(descriptor.cacheKey())));
    }

    private static AdminMemoryCacheService newService() {
        return new AdminMemoryCacheService(
                mock(ModelCatalogService.class),
                mock(ToolBookServiceImpl.class));
    }
}
