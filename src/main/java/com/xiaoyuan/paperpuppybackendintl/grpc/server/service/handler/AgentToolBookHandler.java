package com.xiaoyuan.paperpuppybackendintl.grpc.server.service.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.ReadToolBookRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.ReadToolBookResponse;
import com.xiaoyuan.paperpuppybackendintl.service.toolbook.ToolBookService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AgentToolBookHandler {

    private final ToolBookService toolBookService;

    @Qualifier("jackson2Mapper")
    private final ObjectMapper objectMapper;

    public ReadToolBookResponse readToolBook(ReadToolBookRequest request) {
        String mode = normalize(request.getMode());
        String bookKey = blankToEmpty(request.getBookKey());
        String nodeKey = blankToEmpty(request.getNodeKey());
        int maxChars = request.getMaxChars();

        Object result = switch (mode) {
            case "list_books" -> toolBookService.listBooks();
            case "read_toc" -> toolBookService.getBookToc(bookKey);
            case "read_node" -> toolBookService.readNode(bookKey, nodeKey, maxChars > 0 ? maxChars : null);
            default -> throw new IllegalArgumentException("Unsupported read_tool_book mode: " + request.getMode());
        };

        return ReadToolBookResponse.newBuilder()
                .setSuccess(true)
                .setMode(mode)
                .setBookKey(bookKey)
                .setResultJson(toJson(result))
                .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize tool book result", e);
        }
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToEmpty(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
