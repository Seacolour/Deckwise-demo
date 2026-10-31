package com.xiaoyuan.paperpuppybackendintl.grpc.client.model;

/**
 * 模型响应里返回的远端 Code Interpreter 文件引用。
 *
 * <p>这是 backend 内部中性 DTO，不再绑定 AI-Intl proto。</p>
 */
public record ChatCiFileRef(
        String containerId,
        String fileId,
        String fileName
) {
}
