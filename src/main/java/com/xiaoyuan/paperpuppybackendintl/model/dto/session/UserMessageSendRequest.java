package com.xiaoyuan.paperpuppybackendintl.model.dto.session;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.xiaoyuan.paperpuppybackendintl.model.enums.ConversationMode;
import lombok.Data;

import java.util.List;

/**
 * 用户发送消息请求（MVP1：单输入框文本）
 *
 */
@Data
public class UserMessageSendRequest {

    /**
     * 对话模式：ASK / AGENT
     * <p>不传默认 ASK。</p>
     */
    private ConversationMode mode;

    private String message;

    /**
     * 文件引用列表（uploadFile 返回的 cacheKey=sha256）。
     *
     * <p>后端会将其持久化为"附件元信息"（system + rawContent JSON），供前端回放渲染。</p>
     */
    private List<String> fileCacheKeys;

    /**
     * 图片标识列表（uploadImage 返回的 cacheKey=sha256），用于多模态输入（仅图片）。
     */
    private List<String> imageCacheKeys;

    /**
     * 本轮请求的瞬时 UI / 环境上下文。
     *
     * <p>例如当前打开文档、客户端时区等。后端只会把它作为
     * 本轮 AI 上下文补充，不写入聊天消息历史。</p>
     */
    private UserMessageClientContext clientContext;

    /**
     * 是否为本次请求启用 OpenAI priority processing（映射到 service_tier=priority）。
     * <p>兼容旧客户端字段。新客户端优先使用 serviceTier / service_tier。</p>
     */
    private boolean enablePriorityProcessing;

    /**
     * 本轮请求的 OpenAI service_tier（可选）。
     *
     * <p>支持 priority / fast 开启 priority processing；default / standard / auto 关闭。
     * 为空时回退到 enablePriorityProcessing。</p>
     */
    @JsonAlias("service_tier")
    private String serviceTier;

    /**
     * 用户指定的模型名称（可选）。
     * <p>为空时使用配置的默认模型。前端从 GET /models/available 获取可用模型列表。</p>
     */
    private String model;
}


