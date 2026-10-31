package com.xiaoyuan.paperpuppybackendintl.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebConfigTest {

    @Test
    void corsAllowedMethodsIncludePatchForDocumentThemeUpdates() {
        assertTrue(List.of(WebConfig.CORS_ALLOWED_METHODS).contains("PATCH"));
    }

}
