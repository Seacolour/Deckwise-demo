package com.xiaoyuan.paperpuppyagent.grpc.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrpcServerLifecycleBindTest {
    @Test
    void plaintextByokServerBindsOnlyToIpv4Loopback() {
        var address = GrpcServerLifecycle.localBindAddress(41051);
        assertEquals("127.0.0.1", address.getHostString());
        assertTrue(address.getAddress().isLoopbackAddress());
        assertEquals(41051, address.getPort());
    }
}
