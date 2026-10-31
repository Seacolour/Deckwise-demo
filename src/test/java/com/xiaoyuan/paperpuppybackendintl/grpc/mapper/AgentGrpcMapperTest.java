package com.xiaoyuan.paperpuppybackendintl.grpc.mapper;

import com.xiaoyuan.paperpuppybackendintl.grpc.client.model.AiChatRequest;
import com.xiaoyuan.paperpuppybackendintl.service.session.model.ChatExecutionPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentGrpcMapperTest {

    @Test
    void toGrpcCarriesFileUrls() {
        AiChatRequest request = AiChatRequest.builder()
                .userId("1")
                .sessionId("2")
                .message("read this")
                .fileUrls(List.of("https://example.com/file.pdf"))
                .model("gpt-5.6-terra")
                .providerId("openai")
                .providerApiKey("private-test-key")
                .executionPolicy(ChatExecutionPolicy.builder()
                        .allowAgentSkills(true)
                        .build())
                .build();

        var grpc = AgentGrpcMapper.toGrpc(request);

        assertEquals(1, grpc.getFileUrlsCount());
        assertEquals("https://example.com/file.pdf", grpc.getFileUrls(0));
        assertEquals("gpt-5.6-terra", grpc.getModel());
        assertEquals("openai", grpc.getProviderId());
        assertEquals("private-test-key", grpc.getProviderApiKey());
    }
}
