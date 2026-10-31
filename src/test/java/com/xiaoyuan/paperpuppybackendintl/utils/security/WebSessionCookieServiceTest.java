package com.xiaoyuan.paperpuppybackendintl.utils.security;

import com.xiaoyuan.paperpuppybackendintl.config.TokenSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSessionCookieServiceTest {

    @Test
    void setSessionCookieWritesHttpOnlyLaxCookie() {
        TokenSecurityProperties props = new TokenSecurityProperties();
        var cookieProps = props.getWebSessionCookie();
        cookieProps.setSecure(true);
        cookieProps.setMaxAgeDays(30);

        MockHttpServletResponse response = new MockHttpServletResponse();
        new WebSessionCookieService(props).setSessionCookie(response, "jwt-token");

        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie.contains("deckwise_session=jwt-token"));
        assertTrue(setCookie.contains("Path=/"));
        assertTrue(setCookie.contains("Max-Age=2592000"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
    }

    @Test
    void clearSessionCookieExpiresCookie() {
        TokenSecurityProperties props = new TokenSecurityProperties();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new WebSessionCookieService(props).clearSessionCookie(response);

        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie.contains("deckwise_session="));
        assertTrue(setCookie.contains("Max-Age=0"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
    }
}
