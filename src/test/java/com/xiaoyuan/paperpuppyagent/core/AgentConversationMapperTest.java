package com.xiaoyuan.paperpuppyagent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseInputItem;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConversationMapperTest {

    @Test
    void extractsMultipleImageUrlsAndKeepsLegacySingleValueCompatibility() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentConversationMapper mapper = new AgentConversationMapper(objectMapper);
        ObjectNode result = objectMapper.createObjectNode();
        result.putArray("image_urls")
                .add("https://example.test/page-1.webp")
                .add("https://example.test/page-2.webp")
                .add("https://example.test/page-1.webp");
        result.put("image_url", "https://example.test/page-1.webp");

        assertEquals(List.of(
                        "https://example.test/page-1.webp",
                        "https://example.test/page-2.webp"),
                mapper.extractImageUrlsFromToolResult(result));
    }

    @Test
    void buildInitialInputFromCanonicalRestoresProviderContextWindow() {
        AgentConversationMapper mapper = new AgentConversationMapper(new ObjectMapper());
        AgentChatRequest request = AgentChatRequest.newBuilder()
                .setFullContextJson("""
                        {
                          "version": "v1",
                          "items": [
                            {
                              "type": "provider_context_window",
                              "provider": "openai",
                              "format": "responses_compacted_window_v1",
                              "items": [
                                {
                                  "type": "compaction",
                                  "id": "cmp-1",
                                  "encrypted_content": "encrypted-window"
                                }
                              ]
                            },
                            {
                              "role": "user",
                              "type": "text",
                              "content": "delta user"
                            }
                          ]
                        }
                        """)
                .build();

        List<ResponseInputItem> input = mapper.buildInitialInputFromCanonical(request);

        assertEquals(2, input.size());
        assertTrue(input.get(0).isCompaction());
        assertEquals("cmp-1", input.get(0).asCompaction().id().orElseThrow());
        assertEquals("encrypted-window", input.get(0).asCompaction().encryptedContent());
        assertTrue(input.get(1).isEasyInputMessage());
        assertEquals(EasyInputMessage.Role.USER, input.get(1).asEasyInputMessage().role());

        // A provider-specific encrypted window must never be replayed into another provider.
        List<ResponseInputItem> deepSeekInput = mapper.buildInitialInputFromCanonical(request, "deepseek");
        assertEquals(1, deepSeekInput.size());
        assertTrue(deepSeekInput.getFirst().isEasyInputMessage());
    }

    @Test
    void deepSeekResponsesReplaysPairedFunctionCallsAndChatText() {
        AgentConversationMapper mapper = new AgentConversationMapper(new ObjectMapper());
        AgentChatRequest request = AgentChatRequest.newBuilder()
                .setFullContextJson("""
                        {"version":"v1","items":[
                          {"role":"assistant","type":"tool_output","tool_call_id":"call-1","name":"document_read","raw_content":"{}"},
                          {"role":"tool","type":"tool_output","tool_call_id":"call-1","name":"document_read","raw_content":"{\\"content\\":\\"private\\"}"},
                          {"role":"assistant","type":"text","content":"Here is the document summary."},
                          {"role":"user","type":"text","content":"Please explain it."}
                        ]}
                        """)
                .build();

        List<ResponseInputItem> input = mapper.buildInitialInputFromCanonical(request, "deepseek");

        assertEquals(4, input.size());
        assertTrue(input.get(0).isFunctionCall());
        assertTrue(input.get(1).isFunctionCallOutput());
        assertTrue(input.get(2).isEasyInputMessage());
        assertTrue(input.get(3).isEasyInputMessage());
    }
}
