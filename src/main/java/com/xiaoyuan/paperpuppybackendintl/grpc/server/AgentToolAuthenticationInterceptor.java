package com.xiaoyuan.paperpuppybackendintl.grpc.server;

import com.xiaoyuan.paperpuppybackendintl.grpc.config.AgentToolGrpcAuthProperties;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates the internal Agent caller before any Agent Tool handler runs.
 */
@Slf4j
@Component
public class AgentToolAuthenticationInterceptor implements ServerInterceptor {

    public static final String SERVICE_TOKEN_METADATA_NAME = "x-paperpuppy-service-token";
    static final Metadata.Key<String> SERVICE_TOKEN_METADATA_KEY =
            Metadata.Key.of(SERVICE_TOKEN_METADATA_NAME, Metadata.ASCII_STRING_MARSHALLER);

    private final boolean enabled;
    private final List<byte[]> expectedTokens;

    public AgentToolAuthenticationInterceptor(AgentToolGrpcAuthProperties properties) {
        this.enabled = properties.enabled();
        this.expectedTokens = properties.acceptedServiceTokens().stream()
                .map(token -> token.getBytes(StandardCharsets.UTF_8))
                .toList();
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        if (!enabled) {
            return next.startCall(call, headers);
        }

        String supplied = headers == null ? null : headers.get(SERVICE_TOKEN_METADATA_KEY);
        if (!matches(supplied)) {
            String method = call.getMethodDescriptor() == null
                    ? "unknown"
                    : call.getMethodDescriptor().getFullMethodName();
            log.warn("Rejected unauthenticated Agent Tool gRPC call: method={}, credentialPresent={}",
                    method, supplied != null && !supplied.isBlank());
            call.close(
                    Status.UNAUTHENTICATED.withDescription("invalid internal service credentials"),
                    new Metadata());
            return new ServerCall.Listener<>() {
            };
        }

        return next.startCall(call, headers);
    }

    private boolean matches(String supplied) {
        if (supplied == null) {
            return false;
        }
        byte[] suppliedBytes = supplied.getBytes(StandardCharsets.UTF_8);
        boolean matched = false;
        for (byte[] expectedToken : expectedTokens) {
            matched |= MessageDigest.isEqual(expectedToken, suppliedBytes);
        }
        return matched;
    }
}
