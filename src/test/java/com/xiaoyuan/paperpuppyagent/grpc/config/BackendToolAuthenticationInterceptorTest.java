package com.xiaoyuan.paperpuppyagent.grpc.config;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackendToolAuthenticationInterceptorTest {

    private static final MethodDescriptor<String, String> METHOD = MethodDescriptor.create(
            MethodDescriptor.MethodType.UNARY,
            "paperpuppy.backendintl.agent.tool.AgentToolService/Test",
            new StringMarshaller(),
            new StringMarshaller());

    @Test
    void enabledAuthAddsServiceTokenToEveryCall() {
        BackendToolAuthenticationInterceptor interceptor = interceptor(true, "service-secret");
        Channel channel = mock(Channel.class);
        ClientCall<String, String> delegate = call();
        ClientCall.Listener<String> listener = listener();
        Metadata headers = new Metadata();
        when(channel.newCall(METHOD, CallOptions.DEFAULT)).thenReturn(delegate);

        ClientCall<String, String> secured = interceptor.interceptCall(METHOD, CallOptions.DEFAULT, channel);
        secured.start(listener, headers);

        assertEquals("service-secret",
                headers.get(BackendToolAuthenticationInterceptor.SERVICE_TOKEN_METADATA_KEY));
        verify(delegate).start(same(listener), same(headers));
    }

    @Test
    void interceptorReplacesCallerSuppliedCredential() {
        BackendToolAuthenticationInterceptor interceptor = interceptor(true, "service-secret");
        Channel channel = mock(Channel.class);
        ClientCall<String, String> delegate = call();
        ClientCall.Listener<String> listener = listener();
        Metadata headers = new Metadata();
        headers.put(BackendToolAuthenticationInterceptor.SERVICE_TOKEN_METADATA_KEY, "untrusted-value");
        when(channel.newCall(METHOD, CallOptions.DEFAULT)).thenReturn(delegate);

        interceptor.interceptCall(METHOD, CallOptions.DEFAULT, channel).start(listener, headers);

        assertEquals("service-secret",
                headers.get(BackendToolAuthenticationInterceptor.SERVICE_TOKEN_METADATA_KEY));
    }

    @Test
    void disabledAuthLeavesExistingLocalCallPathUnchanged() {
        BackendToolAuthenticationInterceptor interceptor = interceptor(false, null);
        Channel channel = mock(Channel.class);
        ClientCall<String, String> delegate = call();
        when(channel.newCall(METHOD, CallOptions.DEFAULT)).thenReturn(delegate);

        ClientCall<String, String> actual = interceptor.interceptCall(METHOD, CallOptions.DEFAULT, channel);

        assertSame(delegate, actual);
        Metadata headers = new Metadata();
        actual.start(listener(), headers);
        assertNull(headers.get(BackendToolAuthenticationInterceptor.SERVICE_TOKEN_METADATA_KEY));
    }

    @Test
    void enabledAuthFailsFastWhenClientTokenIsBlank() {
        BackendToolGrpcAuthProperties properties = new BackendToolGrpcAuthProperties(true, "  ");

        assertThrows(IllegalStateException.class,
                () -> new BackendToolAuthenticationInterceptor(properties));
    }

    private static BackendToolAuthenticationInterceptor interceptor(boolean enabled, String token) {
        return new BackendToolAuthenticationInterceptor(new BackendToolGrpcAuthProperties(enabled, token));
    }

    @SuppressWarnings("unchecked")
    private static ClientCall<String, String> call() {
        return mock(ClientCall.class);
    }

    @SuppressWarnings("unchecked")
    private static ClientCall.Listener<String> listener() {
        return mock(ClientCall.Listener.class);
    }

    private static final class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
