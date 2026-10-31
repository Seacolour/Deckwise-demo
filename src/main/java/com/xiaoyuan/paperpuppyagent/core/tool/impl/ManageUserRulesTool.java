package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.backend.ManageUserRulesResponse;
import com.xiaoyuan.paperpuppyagent.grpc.backend.UserRuleItem;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 管理用户持久化规则的工具。
 *
 * <p>
 * 规则会在后续所有对话中作为强约束生效。
 * 仅在用户明确要求时使用此工具。
 * </p>
 */
@Slf4j
@Component
public class ManageUserRulesTool extends Tool {

    private final BackendToolGrpcClient backendClient;

    public ManageUserRulesTool(ObjectMapper mapper, BackendToolGrpcClient backendClient) {
        super(mapper);
        this.backendClient = backendClient;
    }

    @Override
    public String name() {
        return "manage_user_rules";
    }

    @Override
    public JsonNode execute(JsonNode arguments, ToolExecutionContext context) throws Exception {
        long userId = context.userId();
        if (userId <= 0) {
            throw new IllegalStateException("工具执行上下文无效：缺少 userId");
        }

        if (arguments == null || !arguments.has("action")) {
            throw new IllegalArgumentException("缺少必需参数: action");
        }

        String action = arguments.get("action").asText();
        long ruleId = arguments.has("rule_id") ? arguments.get("rule_id").asLong(0) : 0;
        String content = arguments.has("content") ? arguments.get("content").asText(null) : null;

        log.info("[ManageUserRulesTool] userId={}, action={}, ruleId={}", userId, action, ruleId);

        ManageUserRulesResponse response = backendClient.manageUserRules(userId, action, ruleId, content);

        ObjectNode result = mapper.createObjectNode();
        result.put("success", response.getSuccess());
        result.put("message", response.getMessage());

        if (response.getRulesCount() > 0) {
            ArrayNode rulesArr = mapper.createArrayNode();
            for (UserRuleItem item : response.getRulesList()) {
                ObjectNode ruleNode = mapper.createObjectNode();
                ruleNode.put("rule_id", item.getRuleId());
                ruleNode.put("content", item.getContent());
                ruleNode.put("enabled", item.getEnabled());
                ruleNode.put("source", item.getSource());
                if (!item.getCreatedAt().isEmpty()) {
                    ruleNode.put("created_at", item.getCreatedAt());
                }
                rulesArr.add(ruleNode);
            }
            result.set("rules", rulesArr);
        }

        return result;
    }
}
