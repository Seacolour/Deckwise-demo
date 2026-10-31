package com.xiaoyuan.paperpuppyagent.common.aop;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
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

    /**
     * 不记录日志的 gRPC 方法全名列表，默认排除健康探针。
     *
     * <p>配置示例：grpc.logging.silent-methods=grpc.health.v1.Health/Check,grpc.health.v1.Health/Watch</p>
     */
    @Value("${grpc.logging.silent-methods:grpc.health.v1.Health/Check}")
    private List<String> silentMethods;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String method = call.getMethodDescriptor().getFullMethodName();

        if (silentMethods.contains(method)) {
            return next.startCall(call, headers);
        }

        String requestId = UUID.randomUUID().toString();
        String peer = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR) + "";

        long start = System.currentTimeMillis();
        if (logHeaders) {
            Set<String> headerKeys = headers == null ? Set.of() : headers.keys();
            log.info("gRPC request start, id: {}, method: {}, peer: {}, headerKeys: {}",
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
