package com.xiaoyuan.paperpuppybackendintl.model.dto.session;

import lombok.Data;

import java.util.List;

/**
 * 前端发送消息时附带的瞬时 UI / 环境上下文。
 *
 * <p>这些字段只用于本轮 AI 上下文补充，不作为用户消息正文持久化。</p>
 */
@Data
public class UserMessageClientContext {

    private ActiveDocument activeDocument;

    private List<String> referencedDocumentIds;

    private EnvironmentContext environment;

    @Data
    public static class ActiveDocument {
        private String documentId;
        private String type;
        private String fileName;
        private String cacheKey;
    }

    @Data
    public static class EnvironmentContext {
        private String clientNow;
        private String timeZone;
        private String locale;
        private String region;
    }
}
