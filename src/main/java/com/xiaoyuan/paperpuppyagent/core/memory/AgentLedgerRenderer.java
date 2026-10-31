package com.xiaoyuan.paperpuppyagent.core.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.config.AgentWorkMemoryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 将 run-scoped ledger 渲染为短 system message。
 */
@Component
@RequiredArgsConstructor
public class AgentLedgerRenderer {
    private final ObjectMapper om;
    private final AgentWorkMemoryProperties properties;
    private final AgentRunLedgerStore ledgerStore;

    public Optional<String> render(String agentRunId) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        return ledgerStore.snapshot(agentRunId).map(this::renderSnapshot);
    }

    private String renderSnapshot(AgentRunLedger.Snapshot snapshot) {
        ObjectNode root = om.createObjectNode();
        root.put("agentRunId", snapshot.agentRunId());
        root.put("status", snapshot.status());
        if (!Objects.toString(snapshot.goal(), "").isBlank()) {
            root.put("goal", snapshot.goal());
        }
        root.set("todos", todos(snapshot.todos()));
        root.set("facts", facts(snapshot.facts()));
        root.set("decisions", decisions(snapshot.decisions()));
        root.set("artifacts", artifacts(snapshot.artifacts()));
        root.set("recentToolCalls", recentToolCalls(snapshot.recentToolCalls()));
        root.set("readReuseHints", readReuseHints(snapshot.reuseHints()));
        root.set("budget", budget(snapshot.budget()));
        ArrayNode rules = root.putArray("rules");
        rules.add("Do not repeat the same read for the same resource/revision/path when the needed fact is already visible here.");
        rules.add("If a compacted tool result lacks required detail, use agent_tool_result_open with the call_id and mode=path for a local subtree.");
        rules.add("Use agent_ledger_update to record important todos, facts, decisions, and artifact revisions that will matter in later steps.");
        rules.add("If budget warnings are present, avoid more tool calls unless necessary and prepare a concise final answer with completed and remaining work.");

        String json;
        try {
            json = om.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            json = root.toString();
        }
        String text = "System working memory for this Agent run. Treat this as compact, current state; "
                + "it is not user-visible and does not replace tool results.\n" + json;
        int max = Math.max(properties.getMaxLedgerViewChars(), 1000);
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n...(ledger_view_truncated,len=" + text.length() + ")";
    }

    private ArrayNode todos(List<AgentRunLedger.TodoEntry> entries) {
        ArrayNode out = om.createArrayNode();
        int limit = Math.max(properties.getMaxLedgerTodos(), 1);
        int start = Math.max(0, entries.size() - limit);
        for (int i = start; i < entries.size(); i++) {
            AgentRunLedger.TodoEntry entry = entries.get(i);
            ObjectNode n = out.addObject();
            n.put("id", entry.id());
            n.put("text", entry.text());
            n.put("status", entry.status());
            putIfNotBlank(n, "note", entry.note());
            putIfNotBlank(n, "evidenceCallId", entry.evidenceCallId());
        }
        return out;
    }

    private ArrayNode facts(List<AgentRunLedger.TextEntry> entries) {
        ArrayNode out = om.createArrayNode();
        int limit = Math.max(properties.getMaxLedgerFacts(), 1);
        int start = Math.max(0, entries.size() - limit);
        for (int i = start; i < entries.size(); i++) {
            AgentRunLedger.TextEntry entry = entries.get(i);
            ObjectNode n = out.addObject();
            n.put("text", entry.text());
            putIfNotBlank(n, "sourceCallId", entry.sourceCallId());
        }
        return out;
    }

    private ArrayNode decisions(List<AgentRunLedger.DecisionEntry> entries) {
        ArrayNode out = om.createArrayNode();
        int limit = Math.max(properties.getMaxLedgerDecisions(), 1);
        int start = Math.max(0, entries.size() - limit);
        for (int i = start; i < entries.size(); i++) {
            AgentRunLedger.DecisionEntry entry = entries.get(i);
            ObjectNode n = out.addObject();
            n.put("text", entry.text());
            putIfNotBlank(n, "reason", entry.reason());
            putIfNotBlank(n, "sourceCallId", entry.sourceCallId());
        }
        return out;
    }

    private ArrayNode artifacts(List<AgentRunLedger.ArtifactEntry> entries) {
        ArrayNode out = om.createArrayNode();
        int limit = Math.max(properties.getMaxLedgerArtifacts(), 1);
        int start = Math.max(0, entries.size() - limit);
        for (int i = start; i < entries.size(); i++) {
            AgentRunLedger.ArtifactEntry entry = entries.get(i);
            ObjectNode n = out.addObject();
            putIfNotBlank(n, "kind", entry.kind());
            if (entry.documentId() != null) {
                n.put("documentId", entry.documentId());
            }
            if (entry.fileId() != null) {
                n.put("fileId", entry.fileId());
            }
            putIfNotBlank(n, "path", entry.path());
            putIfNotBlank(n, "revision", entry.revision());
            putIfNotBlank(n, "sourceCallId", entry.sourceCallId());
        }
        return out;
    }

    private ArrayNode recentToolCalls(List<AgentRunLedger.ToolCallEntry> entries) {
        ArrayNode out = om.createArrayNode();
        int limit = Math.max(properties.getMaxLedgerRecentToolCalls(), 1);
        int start = Math.max(0, entries.size() - limit);
        for (int i = start; i < entries.size(); i++) {
            AgentRunLedger.ToolCallEntry entry = entries.get(i);
            ObjectNode n = out.addObject();
            n.put("callId", entry.callId());
            n.put("tool", entry.tool());
            n.put("status", entry.status());
            putIfNotBlank(n, "summary", entry.summary());
            putIfNotBlank(n, "rawResultRef", entry.rawResultRef());
            n.put("resultChars", entry.resultChars());
            putIfNotBlank(n, "errorCode", entry.errorCode());
            putIfNotBlank(n, "duplicateOfCallId", entry.duplicateOfCallId());
            putIfNotBlank(n, "reuseHint", entry.reuseHint());
            if (entry.documentId() != null) {
                n.put("documentId", entry.documentId());
            }
            if (entry.fileId() != null) {
                n.put("fileId", entry.fileId());
            }
            putIfNotBlank(n, "revision", entry.revision());
        }
        return out;
    }

    private ArrayNode readReuseHints(List<AgentRunLedger.ReuseHintEntry> entries) {
        ArrayNode out = om.createArrayNode();
        int limit = Math.max(properties.getMaxLedgerReuseHints(), 1);
        int start = Math.max(0, entries.size() - limit);
        for (int i = start; i < entries.size(); i++) {
            AgentRunLedger.ReuseHintEntry entry = entries.get(i);
            ObjectNode n = out.addObject();
            n.put("callId", entry.callId());
            n.put("duplicateOfCallId", entry.duplicateOfCallId());
            n.put("tool", entry.tool());
            putIfNotBlank(n, "hint", entry.hint());
        }
        return out;
    }

    private ObjectNode budget(AgentRunLedger.BudgetSnapshot budget) {
        ObjectNode out = om.createObjectNode();
        ArrayNode warnings = out.putArray("warnings");
        out.set("modelCalls", budgetMetric(
                budget == null ? 0 : budget.modelCalls(),
                properties.getSoftMaxModelCallsPerRun(),
                "modelCalls",
                warnings));
        out.set("toolCalls", budgetMetric(
                budget == null ? 0 : budget.toolCalls(),
                properties.getSoftMaxToolCallsPerRun(),
                "toolCalls",
                warnings));
        out.set("toolResultOpenCalls", budgetMetric(
                budget == null ? 0 : budget.toolResultOpenCalls(),
                properties.getSoftMaxToolResultOpenCallsPerRun(),
                "toolResultOpenCalls",
                warnings));
        if (!warnings.isEmpty()) {
            out.put("next_step_hint", "Budget is close to or over soft limit. Prefer finishing with existing facts, and call tools only for essential missing information.");
        }
        return out;
    }

    private ObjectNode budgetMetric(int used, int softLimit, String name, ArrayNode warnings) {
        int limit = Math.max(softLimit, 1);
        int warnPercent = Math.min(Math.max(properties.getBudgetWarningPercent(), 1), 100);
        ObjectNode metric = om.createObjectNode();
        metric.put("used", Math.max(used, 0));
        metric.put("softLimit", limit);
        metric.put("remainingBeforeSoftLimit", Math.max(limit - used, 0));
        int percent = (int) Math.floor((Math.max(used, 0) * 100.0) / limit);
        metric.put("usedPercent", percent);
        if (used >= limit) {
            metric.put("status", "soft_limit_reached");
            warnings.add(name + " reached soft limit " + used + "/" + limit + "; stop expanding and prepare a concise final answer unless essential information is missing.");
        } else if (percent >= warnPercent) {
            metric.put("status", "near_soft_limit");
            warnings.add(name + " is near soft limit " + used + "/" + limit + "; avoid optional tool calls.");
        } else {
            metric.put("status", "ok");
        }
        return metric;
    }

    private static void putIfNotBlank(ObjectNode node, String field, String value) {
        String text = Objects.toString(value, "").trim();
        if (!text.isBlank()) {
            node.put(field, text);
        }
    }
}
