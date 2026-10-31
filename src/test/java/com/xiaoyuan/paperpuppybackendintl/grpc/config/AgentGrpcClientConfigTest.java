package com.xiaoyuan.paperpuppybackendintl.grpc.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentGrpcClientConfigTest {

    @Test
    void refusesRemotePlaintextDestinationWhenByokCredentialsCanBeForwarded() {
        AgentGrpcProperties properties = new AgentGrpcProperties();
        properties.setHost("192.168.1.10");
        properties.setPort(41051);
        assertThrows(IllegalStateException.class,
                () -> new AgentGrpcClientConfig(properties).agentManagedChannel());
    }
}
