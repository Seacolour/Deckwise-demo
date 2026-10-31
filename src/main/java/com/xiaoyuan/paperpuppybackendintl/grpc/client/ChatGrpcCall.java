package com.xiaoyuan.paperpuppybackendintl.grpc.client;

/**
 * gRPC 调用句柄（允许业务层在 SSE 断开时取消流式调用）。
 */
public interface ChatGrpcCall {
    void cancel(String reason);
}
