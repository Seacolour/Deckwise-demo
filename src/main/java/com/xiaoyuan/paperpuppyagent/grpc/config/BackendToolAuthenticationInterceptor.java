package com.xiaoyuan.paperpuppyagent.grpc.config;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Adds the internal service credential to every Backend Tool gRPC request.
 */
public class BackendToolAuthenticationInterceptor implements ClientInterceptor {

    public static final String SERVICE_TOKEN_METADATA_NAME = "x-paperpuppy-service-token";
    static final Metadata.Key<String> SERVICE_TOKEN_METADATA_KEY =
            Metadata.Key.of(SERVICE_TOKEN_METADATA_NAME, Metadata.ASCII_STRING_MARSHALLER);

    private final boolean enabled;
    private final String serviceToken;

    public BackendToolAuthenticationInterceptor(BackendToolGrpcAuthProperties properties) {
        this.enabled = properties.enabled();
        this.serviceToken = properties.requiredServiceToken();
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {
        ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);
        if (!enabled) {
            return call;
        }

        return new ForwardingClientCall.SimpleForwardingClientCall<>(call) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.removeAll(SERVICE_TOKEN_METADATA_KEY);
                headers.put(SERVICE_TOKEN_METADATA_KEY, serviceToken);
                super.start(responseListener, headers);
            }
        };
    }
}
