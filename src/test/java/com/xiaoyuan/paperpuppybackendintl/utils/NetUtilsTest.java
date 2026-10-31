package com.xiaoyuan.paperpuppybackendintl.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetUtilsTest {

    @Test
    void acceptsCanonicalForwardedAddressFromPrivateReverseProxy() {
        MockHttpServletRequest request = requestFrom("172.18.0.2");
        request.addHeader("X-Forwarded-For", "198.51.100.42");

        assertEquals("198.51.100.42", NetUtils.getIpAddress(request));
    }

    @Test
    void ignoresAllForwardingHeadersFromPublicPeer() {
        MockHttpServletRequest request = requestFrom("203.0.113.20");
        request.addHeader("X-Forwarded-For", "198.51.100.125");
        request.addHeader("X-Real-IP", "198.51.100.126");
        request.addHeader("CF-Connecting-IP", "198.51.100.127");

        assertEquals("203.0.113.20", NetUtils.getIpAddress(request));
    }

    @Test
    void rejectsCommaSeparatedForwardingChain() {
        MockHttpServletRequest request = requestFrom("172.18.0.2");
        request.addHeader("X-Forwarded-For", "198.51.100.42, 203.0.113.10");

        assertEquals("172.18.0.2", NetUtils.getIpAddress(request));
    }

    @Test
    void ignoresLegacyProxyHeadersEvenFromTrustedPeer() {
        MockHttpServletRequest request = requestFrom("172.18.0.2");
        request.addHeader("Proxy-Client-IP", "198.51.100.42");
        request.addHeader("WL-Proxy-Client-IP", "198.51.100.43");

        assertEquals("172.18.0.2", NetUtils.getIpAddress(request));
    }

    @Test
    void acceptsIpv6LiteralFromTrustedProxy() {
        MockHttpServletRequest request = requestFrom("10.148.0.2");
        request.addHeader("CF-Connecting-IP", "2001:db8::42");

        assertEquals("2001:db8::42", NetUtils.getIpAddress(request));
    }

    @Test
    void rejectsInvalidForwardedAddress() {
        MockHttpServletRequest request = requestFrom("172.18.0.2");
        request.addHeader("X-Forwarded-For", "attacker.example");

        assertEquals("172.18.0.2", NetUtils.getIpAddress(request));
    }

    @Test
    void normalizesLoopbackAddress() {
        assertEquals("localhost", NetUtils.getIpAddress(requestFrom("::1")));
    }

    private MockHttpServletRequest requestFrom(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
