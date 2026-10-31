package com.xiaoyuan.paperpuppybackendintl.grpc.server;

import com.xiaoyuan.paperpuppybackendintl.grpc.config.AgentToolGrpcAuthProperties;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolAuthenticationInterceptorTest {

    @Test
    void validTokenReachesBusinessHandler() {
        AgentToolAuthenticationInterceptor interceptor = interceptor(true, "service-secret");
        ServerCall<String, String> call = call();
        ServerCallHandler<String, String> next = handler();
        ServerCall.Listener<String> expected = listener();
        Metadata headers = new Metadata();
        headers.put(AgentToolAuthenticationInterceptor.SERVICE_TOKEN_METADATA_KEY, "service-secret");
        when(next.startCall(call, headers)).thenReturn(expected);

        ServerCall.Listener<String> actual = interceptor.interceptCall(call, headers, next);

        assertSame(expected, actual);
        verify(next).startCall(call, headers);
        verify(call, never()).close(any(Status.class), any(Metadata.class));
    }

    @Test
    void previousTokenIsAcceptedDuringRotationWindow() {
        AgentToolAuthenticationInterceptor interceptor = new AgentToolAuthenticationInterceptor(
                new AgentToolGrpcAuthProperties(true, "new-secret", "old-secret"));
        ServerCall<String, String> call = call();
        ServerCallHandler<String, String> next = handler();
        ServerCall.Listener<String> expected = listener();
        Metadata headers = new Metadata();
        headers.put(AgentToolAuthenticationInterceptor.SERVICE_TOKEN_METADATA_KEY, "old-secret");
        when(next.startCall(call, headers)).thenReturn(expected);

        assertSame(expected, interceptor.interceptCall(call, headers, next));
        verify(next).startCall(call, headers);
    }

    @Test
    void missingTokenIsRejectedBeforeBusinessHandler() {
        AgentToolAuthenticationInterceptor interceptor = interceptor(true, "service-secret");
        ServerCall<String, String> call = call();
        ServerCallHandler<String, String> next = handler();

        interceptor.interceptCall(call, new Metadata(), next);

        ArgumentCaptor<Status> status = ArgumentCaptor.forClass(Status.class);
        verify(call).close(status.capture(), any(Metadata.class));
        assertEquals(Status.Code.UNAUTHENTICATED, status.getValue().getCode());
        verify(next, never()).startCall(any(), any());
    }

    @Test
    void wrongTokenIsRejectedBeforeBusinessHandler() {
        AgentToolAuthenticationInterceptor interceptor = interceptor(true, "service-secret");
        ServerCall<String, String> call = call();
        ServerCallHandler<String, String> next = handler();
        Metadata headers = new Metadata();
        headers.put(AgentToolAuthenticationInterceptor.SERVICE_TOKEN_METADATA_KEY, "wrong-secret");

        interceptor.interceptCall(call, headers, next);

        ArgumentCaptor<Status> status = ArgumentCaptor.forClass(Status.class);
        verify(call).close(status.capture(), any(Metadata.class));
        assertEquals(Status.Code.UNAUTHENTICATED, status.getValue().getCode());
        verify(next, never()).startCall(any(), any());
    }

    @Test
    void disabledAuthKeepsExistingLocalCallPath() {
        AgentToolAuthenticationInterceptor interceptor = interceptor(false, null);
        ServerCall<String, String> call = call();
        ServerCallHandler<String, String> next = handler();
        ServerCall.Listener<String> expected = listener();
        Metadata headers = new Metadata();
        when(next.startCall(call, headers)).thenReturn(expected);

        assertSame(expected, interceptor.interceptCall(call, headers, next));
        verify(next).startCall(call, headers);
    }

    @Test
    void enabledAuthFailsFastWhenServerTokenIsBlank() {
        AgentToolGrpcAuthProperties properties = new AgentToolGrpcAuthProperties(true, "  ", null);

        assertThrows(IllegalStateException.class,
                () -> new AgentToolAuthenticationInterceptor(properties));
    }

    private static AgentToolAuthenticationInterceptor interceptor(boolean enabled, String token) {
        return new AgentToolAuthenticationInterceptor(new AgentToolGrpcAuthProperties(enabled, token, null));
    }

    @SuppressWarnings("unchecked")
    private static ServerCall<String, String> call() {
        return mock(ServerCall.class);
    }

    @SuppressWarnings("unchecked")
    private static ServerCallHandler<String, String> handler() {
        return mock(ServerCallHandler.class);
    }

    private static ServerCall.Listener<String> listener() {
        return new ServerCall.Listener<>() {
        };
    }
}
