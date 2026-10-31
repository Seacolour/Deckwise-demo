package com.xiaoyuan.paperpuppybackendintl.aop;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * @author Seacolour
 * @date 2025/8/8 15:24
 * @description: gRPC全链路日志
 */
@Slf4j
@Component
public class GrpcLogInterceptor implements ServerInterceptor {

    /**
     * 是否记录 gRPC headers（生产环境建议关闭，避免泄露敏感信息）。
     *
     * <p>配置：grpc.logging.log-headers=false</p>
     */
    @Value("${grpc.logging.log-headers:false}")
    private boolean logHeaders;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String requestId = UUID.randomUUID().toString();
        String method = call.getMethodDescriptor().getFullMethodName();
        String peer = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR) + "";

        long start = System.currentTimeMillis();
        if (logHeaders) {
            // 仅记录 header key，避免 value 泄露（Authorization/cookie/未来的内部 token 等）
            Set<String> headerKeys = headers == null ? Set.of() : headers.keys();
            log.info("gRPC request start, id: {}, method: {}, peer: {}, headersKeys: {}",
                    requestId, method, peer, headerKeys);
        } else {
            log.info("gRPC request start, id: {}, method: {}, peer: {}",
                    requestId, method, peer);
        }

        ServerCall.Listener<ReqT> listener = next.startCall(call, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onComplete() {
                long cost = System.currentTimeMillis() - start;
                log.info("gRPC request end, id: {}, method: {}, cost: {} ms", requestId, method, cost);
                super.onComplete();
            }

            @Override
            public void onCancel() {
                long cost = System.currentTimeMillis() - start;
                log.warn("gRPC request cancelled, id: {}, method: {}, cost: {} ms", requestId, method, cost);
                super.onCancel();
            }
        };
    }
}
