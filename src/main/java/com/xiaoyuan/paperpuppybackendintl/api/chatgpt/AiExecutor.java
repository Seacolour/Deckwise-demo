package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

/**
 * @author Seacolour
 * @date 2025/12/30 11:29
 * @description:
 */
public interface AiExecutor<P extends AiParam, R> {
    R call(P param);
}
