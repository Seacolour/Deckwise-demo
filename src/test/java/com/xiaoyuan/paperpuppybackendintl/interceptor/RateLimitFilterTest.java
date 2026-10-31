package com.xiaoyuan.paperpuppybackendintl.interceptor;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisClient;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisKey;
import com.xiaoyuan.paperpuppybackendintl.utils.security.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    @Test
    void returnsRetryAfterHeaderForBlockedAuthBucket() throws ServletException, IOException {
        RedisClient redisClient = mock(RedisClient.class);
        String globalIpKey = RedisKey.rateLimit("global.ip:localhost");
        String authIpKey = RedisKey.rateLimit("auth.ip:localhost");
        when(redisClient.incrementWithExpire(globalIpKey, 60, TimeUnit.SECONDS)).thenReturn(1L);
        when(redisClient.ttl(globalIpKey, TimeUnit.SECONDS)).thenReturn(30L);
        when(redisClient.incrementWithExpire(authIpKey, 300, TimeUnit.SECONDS)).thenReturn(11L);
        when(redisClient.ttl(authIpKey, TimeUnit.SECONDS)).thenReturn(240L);
        RateLimitFilter filter = new RateLimitFilter(redisClient);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/user/login");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(429, response.getStatus());
        assertEquals("auth.ip", response.getHeader("X-RateLimit-Bucket"));
        assertEquals("10", response.getHeader("X-RateLimit-Limit"));
        assertEquals("0", response.getHeader("X-RateLimit-Remaining"));
        assertEquals("240", response.getHeader("Retry-After"));
        assertNotNull(response.getHeader("X-RateLimit-Reset"));
    }

    @Test
    void newPasswordEndpointsUseAuthBucket() throws ServletException, IOException {
        for (String path : new String[] {
                "/api/user/login/password", "/api/user/password/email-code", "/api/user/password"
        }) {
            RedisClient redisClient = mock(RedisClient.class);
            String globalIpKey = RedisKey.rateLimit("global.ip:localhost");
            String authIpKey = RedisKey.rateLimit("auth.ip:localhost");
            when(redisClient.incrementWithExpire(globalIpKey, 60, TimeUnit.SECONDS)).thenReturn(1L);
            when(redisClient.ttl(globalIpKey, TimeUnit.SECONDS)).thenReturn(30L);
            when(redisClient.incrementWithExpire(authIpKey, 300, TimeUnit.SECONDS)).thenReturn(11L);
            when(redisClient.ttl(authIpKey, TimeUnit.SECONDS)).thenReturn(240L);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            new RateLimitFilter(redisClient).doFilter(request, response, new MockFilterChain());

            assertEquals(429, response.getStatus(), path);
            assertEquals("auth.ip", response.getHeader("X-RateLimit-Bucket"), path);
        }
    }

    @Test
    void authenticatedProjectGetUsesNormalUserBucket() throws ServletException, IOException {
        RedisClient redisClient = mock(RedisClient.class);
        String globalIpKey = RedisKey.rateLimit("global.ip:localhost");
        String globalUserKey = RedisKey.rateLimit("global.user:42");
        when(redisClient.incrementWithExpire(globalIpKey, 60, TimeUnit.SECONDS)).thenReturn(1L);
        when(redisClient.ttl(globalIpKey, TimeUnit.SECONDS)).thenReturn(50L);
        when(redisClient.incrementWithExpire(globalUserKey, 60, TimeUnit.SECONDS)).thenReturn(1L);
        when(redisClient.ttl(globalUserKey, TimeUnit.SECONDS)).thenReturn(50L);
        RateLimitFilter filter = new RateLimitFilter(redisClient);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects/42");
        request.setRemoteAddr("127.0.0.1");
        request.setCookies(new Cookie("deckwise_session", JwtUtil.generateToken(42L, "user@example.com")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        verify(redisClient).incrementWithExpire(globalUserKey, 60, TimeUnit.SECONDS);
    }
}
