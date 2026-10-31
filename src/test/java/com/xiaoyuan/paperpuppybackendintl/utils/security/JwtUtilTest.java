package com.xiaoyuan.paperpuppybackendintl.utils.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class JwtUtilTest {

    @BeforeEach
    void setUp() {
        JwtUtil.configureWebSessionCookie(true, "deckwise_session");
    }

    @Test
    void getTokenFromRequestPrefersBearerTokenOverCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bearer-token");
        request.setCookies(new Cookie("deckwise_session", "cookie-token"));

        assertEquals("bearer-token", JwtUtil.getTokenFromRequest(request));
    }

    @Test
    void getTokenFromRequestFallsBackToWebSessionCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("deckwise_session", "cookie-token"));

        assertEquals("cookie-token", JwtUtil.getTokenFromRequest(request));
    }

    @Test
    void getTokenFromRequestReturnsNullWithoutBearerOrCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertNull(JwtUtil.getTokenFromRequest(request));
    }

    @Test
    void getTokenFromRequestIgnoresCookieWhenWebSessionCookieIsDisabled() {
        JwtUtil.configureWebSessionCookie(false, "deckwise_session");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("deckwise_session", "cookie-token"));

        assertNull(JwtUtil.getTokenFromRequest(request));
    }

    @Test
    void generatesIndependentTokensForTheSameUser() {
        String first = JwtUtil.generateToken(42L, "user@example.com");
        String second = JwtUtil.generateToken(42L, "user@example.com");

        assertNotEquals(first, second);
    }
}
