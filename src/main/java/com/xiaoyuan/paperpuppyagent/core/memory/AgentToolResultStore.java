package com.xiaoyuan.paperpuppyagent.core.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppyagent.config.AgentWorkMemoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单次 Agent run 内的工具结果存储。
 *
 * <p>这是阶段二 MVP 的内存版 ToolResultStore。它不跨 run、不跨进程、不持久化。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolResultStore {
    private final ObjectMapper om;
    private final AgentWorkMemoryProperties properties;

    private final Map<String, LinkedHashMap<String, StoredToolResult>> resultsByRun = new ConcurrentHashMap<>();

    public Optional<StoredToolResult> store(String agentRunId, String callId, String toolName, JsonNode result) {
        String runId = normalize(agentRunId);
        String cid = normalize(callId);
        if (!properties.isEnabled() || runId.isBlank() || cid.isBlank() || result == null) {
            return Optional.empty();
        }

        String raw;
        try {
            raw = om.writeValueAsString(result);
        } catch (Exception e) {
            log.debug("[Agent][Memory] tool result not stored because serialization failed: runId={}, callId={}, tool={}, err={}",
                    runId, cid, toolName, e.getMessage());
            return Optional.empty();
        }

        int maxChars = Math.max(properties.getMaxStoredResultChars(), 0);
        if (maxChars <= 0 || raw.length() > maxChars) {
            log.info("[Agent][Memory] tool result not stored because it is too large: runId={}, callId={}, tool={}, chars={}, maxChars={}",
                    runId, cid, toolName, raw.length(), maxChars);
            return Optional.empty();
        }

        StoredToolResult stored = new StoredToolResult(
                runId,
                cid,
                Objects.toString(toolName, ""),
                result.deepCopy(),
                raw,
                raw.length(),
                Instant.now());
        LinkedHashMap<String, StoredToolResult> runResults =
                resultsByRun.computeIfAbsent(runId, ignored -> new LinkedHashMap<>());
        synchronized (runResults) {
            runResults.put(cid, stored);
            trimRunResults(runResults);
        }
        return Optional.of(stored);
    }

    public Optional<StoredToolResult> find(String agentRunId, String callId) {
        String runId = normalize(agentRunId);
        String cid = normalize(callId);
        if (runId.isBlank() || cid.isBlank()) {
            return Optional.empty();
        }
        LinkedHashMap<String, StoredToolResult> runResults = resultsByRun.get(runId);
        if (runResults == null) {
            return Optional.empty();
        }
        synchronized (runResults) {
            return Optional.ofNullable(runResults.get(cid));
        }
    }

    public void clearRun(String agentRunId) {
        String runId = normalize(agentRunId);
        if (!runId.isBlank()) {
            resultsByRun.remove(runId);
        }
    }

    private void trimRunResults(LinkedHashMap<String, StoredToolResult> runResults) {
        int max = Math.max(properties.getMaxResultsPerRun(), 1);
        while (runResults.size() > max) {
            String firstKey = runResults.keySet().iterator().next();
            runResults.remove(firstKey);
        }
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").trim();
    }
}
