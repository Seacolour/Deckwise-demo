package com.xiaoyuan.paperpuppybackendintl.aop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogInterceptorTest {

    private final LogInterceptor interceptor = new LogInterceptor();

    @Test
    void centralRedactorMasksAssignmentsJsonAndQueryParametersBeforeTruncation() {
        String value = "LoginRequest(idToken=header.payload.signature, nonce=raw-value), "
                + "json={\"accessToken\":\"access-value\"}, authorization=Bearer bearer-value, "
                + "pollSecret=desktop-poll-secret, path=/callback?code=123456&name=visible";

        String summary = interceptor.summarizeArg(value);

        assertFalse(summary.contains("header.payload.signature"));
        assertFalse(summary.contains("raw-value"));
        assertFalse(summary.contains("access-value"));
        assertFalse(summary.contains("bearer-value"));
        assertFalse(summary.contains("desktop-poll-secret"));
        assertFalse(summary.contains("123456"));
        assertTrue(summary.contains("idToken=<redacted>"));
        assertTrue(summary.contains("nonce=<redacted>"));
        assertTrue(summary.contains("pollSecret=<redacted>"));
        assertTrue(summary.contains("name=visible"));
    }
}
