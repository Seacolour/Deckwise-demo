package com.xiaoyuan.paperpuppybackendintl.config;

import com.xiaoyuan.paperpuppybackendintl.utils.security.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSessionCsrfFilterTest {

    private TokenSecurityProperties props;
    private WebSessionCsrfFilter filter;

    @BeforeEach
    void setUp() {
        props = new TokenSecurityProperties();
        JwtUtil.configureWebSessionCookie(true, props.getWebSessionCookie().getName());
        filter = new WebSessionCsrfFilter(props);
    }

    @Test
    void allowsUnsafeCookieRequestFromAllowedOrigin() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/projects");
        request.addHeader("Origin", "http://localhost:3000");
        request.setCookies(new Cookie("deckwise_session", "cookie-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
    }

    @Test
    void blocksUnsafeCookieRequestFromUnknownOrigin() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/projects");
        request.addHeader("Origin", "https://evil.example");
        request.setCookies(new Cookie("deckwise_session", "cookie-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
    }

    @Test
    void skipsOriginCheckForBearerRequests() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/projects");
        request.addHeader("Authorization", "Bearer bearer-token");
        request.addHeader("Origin", "https://evil.example");
        request.setCookies(new Cookie("deckwise_session", "cookie-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
    }
}
