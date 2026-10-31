package com.xiaoyuan.paperpuppybackendintl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 统一的异步执行器配置（按“当前业务域/服务”命名）。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>本项目已启用 Virtual Threads（见 application.yml: spring.threads.virtual.enabled=true）。</li>
 *   <li>VirtualThreadTaskExecutor 不做并发上限；若需要隔离/限流，应改为有界线程池或在 MQ 消费侧做并发与 prefetch 控制。</li>
 *   <li>这里仅解决“旧命名（ai/oss）与当前职责不符”的问题。</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class TaskExecutorsConfig {

    /**
     * 对话域执行器：用于 SSE/会话对话相关的后台任务调度。
     */
    @Bean("chatExecutor")
    public TaskExecutor chatExecutor() {
        return new VirtualThreadTaskExecutor("chat-");
    }

    /**
     * 存储域执行器：用于对象存储（R2/S3）相关的 IO 任务（预留）。
     * <p>注意：当前代码中暂未显式使用该 executor；保留是为了避免未来再次引入“ossUploadExecutor”一类旧名。</p>
     */
    @Bean("storageExecutor")
    public TaskExecutor storageExecutor() {
        return new VirtualThreadTaskExecutor("storage-");
    }

    @Bean("analyticsExecutor")
    public TaskExecutor analyticsExecutor() {
        return new VirtualThreadTaskExecutor("analytics-");
    }

}


