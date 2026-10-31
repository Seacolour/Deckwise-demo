package com.xiaoyuan.paperpuppybackendintl.grpc.server.service.handler;

import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.ManageUserMemoryRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.ManageUserMemoryResponse;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.ManageUserRulesRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.ManageUserRulesResponse;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.UserMemoryItem;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.UserRuleItem;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserMemory;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserRule;
import com.xiaoyuan.paperpuppybackendintl.repository.UserMemoryRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserRuleRepository;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AgentToolUserPreferenceHandler {

    private static final int MAX_RULES_PER_USER = 20;
    private static final int MAX_MEMORIES_PER_USER = 50;
    private static final int MAX_RULE_CONTENT_LENGTH = 500;
    private static final int MAX_MEMORY_TITLE_LENGTH = 100;
    private static final int MAX_MEMORY_CONTENT_LENGTH = 1000;

    private final UserRuleRepository userRuleRepository;
    private final UserMemoryRepository userMemoryRepository;

    public ManageUserRulesResponse manageUserRules(ManageUserRulesRequest request) {
        long userId = request.getUserId();
        String action = request.getAction();
        if (userId <= 0) {
            throw Status.INVALID_ARGUMENT.withDescription("Invalid userId").asRuntimeException();
        }

        ManageUserRulesResponse.Builder rb = ManageUserRulesResponse.newBuilder();
        switch (action) {
            case "add" -> {
                String content = request.getContent();
                if (content == null || content.isBlank()) {
                    rb.setSuccess(false).setMessage("content is required for add");
                } else if (content.length() > MAX_RULE_CONTENT_LENGTH) {
                    rb.setSuccess(false).setMessage("content exceeds max length (" + MAX_RULE_CONTENT_LENGTH + ")");
                } else if (userRuleRepository.countByUserId(userId) >= MAX_RULES_PER_USER) {
                    rb.setSuccess(false).setMessage("rule limit reached (" + MAX_RULES_PER_USER + ")");
                } else {
                    UserRule rule = new UserRule();
                    rule.setUserId(userId);
                    rule.setContent(content.trim());
                    rule.setEnabled(true);
                    rule.setSource("ai");
                    UserRule saved = userRuleRepository.insert(rule);
                    rb.setSuccess(true).setMessage("Rule added (id=" + saved.getId() + ")");
                    rb.addAllRules(buildRuleItems(userRuleRepository.listByUserId(userId)));
                }
            }
            case "update" -> {
                long ruleId = request.getRuleId();
                String content = request.getContent();
                if (ruleId <= 0) {
                    rb.setSuccess(false).setMessage("rule_id is required for update");
                } else if (content == null || content.isBlank()) {
                    rb.setSuccess(false).setMessage("content is required for update");
                } else if (content.length() > MAX_RULE_CONTENT_LENGTH) {
                    rb.setSuccess(false).setMessage("content exceeds max length (" + MAX_RULE_CONTENT_LENGTH + ")");
                } else {
                    var existing = userRuleRepository.findById(ruleId);
                    if (existing.isEmpty() || !existing.get().getUserId().equals(userId)) {
                        rb.setSuccess(false).setMessage("rule not found or not owned by user");
                    } else {
                        boolean updated = userRuleRepository.updateContent(ruleId, content.trim());
                        rb.setSuccess(updated).setMessage(updated ? "Rule updated" : "Update failed");
                        rb.addAllRules(buildRuleItems(userRuleRepository.listByUserId(userId)));
                    }
                }
            }
            case "remove" -> {
                long ruleId = request.getRuleId();
                if (ruleId <= 0) {
                    rb.setSuccess(false).setMessage("rule_id is required for remove");
                } else {
                    var existing = userRuleRepository.findById(ruleId);
                    if (existing.isEmpty() || !existing.get().getUserId().equals(userId)) {
                        rb.setSuccess(false).setMessage("rule not found or not owned by user");
                    } else {
                        boolean deleted = userRuleRepository.softDelete(ruleId);
                        rb.setSuccess(deleted).setMessage(deleted ? "Rule removed" : "Remove failed");
                        rb.addAllRules(buildRuleItems(userRuleRepository.listByUserId(userId)));
                    }
                }
            }
            case "list" -> {
                List<UserRule> rules = userRuleRepository.listByUserId(userId);
                rb.setSuccess(true).setMessage("Found " + rules.size() + " rule(s)");
                rb.addAllRules(buildRuleItems(rules));
            }
            default -> rb.setSuccess(false).setMessage("Unknown action: " + action + ". Use add/update/remove/list.");
        }

        return rb.build();
    }

    public ManageUserMemoryResponse manageUserMemory(ManageUserMemoryRequest request) {
        long userId = request.getUserId();
        String action = request.getAction();
        if (userId <= 0) {
            throw Status.INVALID_ARGUMENT.withDescription("Invalid userId").asRuntimeException();
        }

        ManageUserMemoryResponse.Builder rb = ManageUserMemoryResponse.newBuilder();
        switch (action) {
            case "create" -> {
                String title = request.getTitle();
                String content = request.getContent();
                if (title == null || title.isBlank()) {
                    rb.setSuccess(false).setMessage("title is required for create");
                } else if (content == null || content.isBlank()) {
                    rb.setSuccess(false).setMessage("content is required for create");
                } else if (title.length() > MAX_MEMORY_TITLE_LENGTH) {
                    rb.setSuccess(false).setMessage("title exceeds max length (" + MAX_MEMORY_TITLE_LENGTH + ")");
                } else if (content.length() > MAX_MEMORY_CONTENT_LENGTH) {
                    rb.setSuccess(false).setMessage("content exceeds max length (" + MAX_MEMORY_CONTENT_LENGTH + ")");
                } else if (userMemoryRepository.countByUserId(userId) >= MAX_MEMORIES_PER_USER) {
                    rb.setSuccess(false).setMessage("memory limit reached (" + MAX_MEMORIES_PER_USER + ")");
                } else {
                    UserMemory memory = new UserMemory();
                    memory.setUserId(userId);
                    memory.setTitle(title.trim());
                    memory.setContent(content.trim());
                    memory.setSource("ai");
                    UserMemory saved = userMemoryRepository.insert(memory);
                    rb.setSuccess(true).setMessage("Memory created (id=" + saved.getId() + ")");
                    rb.addAllMemories(buildMemoryItems(userMemoryRepository.listByUserId(userId)));
                }
            }
            case "update" -> {
                long memoryId = request.getMemoryId();
                String title = request.getTitle();
                String content = request.getContent();
                if (memoryId <= 0) {
                    rb.setSuccess(false).setMessage("memory_id is required for update");
                } else if ((title == null || title.isBlank()) && (content == null || content.isBlank())) {
                    rb.setSuccess(false).setMessage("title or content is required for update");
                } else if (title != null && title.length() > MAX_MEMORY_TITLE_LENGTH) {
                    rb.setSuccess(false).setMessage("title exceeds max length (" + MAX_MEMORY_TITLE_LENGTH + ")");
                } else if (content != null && content.length() > MAX_MEMORY_CONTENT_LENGTH) {
                    rb.setSuccess(false).setMessage("content exceeds max length (" + MAX_MEMORY_CONTENT_LENGTH + ")");
                } else {
                    var existing = userMemoryRepository.findById(memoryId);
                    if (existing.isEmpty() || !existing.get().getUserId().equals(userId)) {
                        rb.setSuccess(false).setMessage("memory not found or not owned by user");
                    } else {
                        UserMemory ex = existing.get();
                        String newTitle = (title != null && !title.isBlank()) ? title.trim() : ex.getTitle();
                        String newContent = (content != null && !content.isBlank()) ? content.trim() : ex.getContent();
                        boolean updated = userMemoryRepository.updateContent(memoryId, newTitle, newContent);
                        rb.setSuccess(updated).setMessage(updated ? "Memory updated" : "Update failed");
                        rb.addAllMemories(buildMemoryItems(userMemoryRepository.listByUserId(userId)));
                    }
                }
            }
            case "delete" -> {
                long memoryId = request.getMemoryId();
                if (memoryId <= 0) {
                    rb.setSuccess(false).setMessage("memory_id is required for delete");
                } else {
                    var existing = userMemoryRepository.findById(memoryId);
                    if (existing.isEmpty() || !existing.get().getUserId().equals(userId)) {
                        rb.setSuccess(false).setMessage("memory not found or not owned by user");
                    } else {
                        boolean deleted = userMemoryRepository.softDelete(memoryId);
                        rb.setSuccess(deleted).setMessage(deleted ? "Memory deleted" : "Delete failed");
                        rb.addAllMemories(buildMemoryItems(userMemoryRepository.listByUserId(userId)));
                    }
                }
            }
            case "list" -> {
                List<UserMemory> memories = userMemoryRepository.listByUserId(userId);
                rb.setSuccess(true).setMessage("Found " + memories.size() + " memory(ies)");
                rb.addAllMemories(buildMemoryItems(memories));
            }
            default -> rb.setSuccess(false).setMessage("Unknown action: " + action + ". Use create/update/delete/list.");
        }

        return rb.build();
    }

    private List<UserRuleItem> buildRuleItems(List<UserRule> rules) {
        return rules.stream().map(r -> {
            UserRuleItem.Builder b = UserRuleItem.newBuilder()
                    .setRuleId(r.getId())
                    .setContent(r.getContent())
                    .setEnabled(Boolean.TRUE.equals(r.getEnabled()))
                    .setSource(r.getSource() != null ? r.getSource() : "user");
            if (r.getCreatedAt() != null) {
                b.setCreatedAt(r.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
            return b.build();
        }).toList();
    }

    private List<UserMemoryItem> buildMemoryItems(List<UserMemory> memories) {
        return memories.stream().map(m -> {
            UserMemoryItem.Builder b = UserMemoryItem.newBuilder()
                    .setMemoryId(m.getId())
                    .setTitle(m.getTitle())
                    .setContent(m.getContent())
                    .setSource(m.getSource() != null ? m.getSource() : "ai");
            if (m.getCreatedAt() != null) {
                b.setCreatedAt(m.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
            if (m.getUpdatedAt() != null) {
                b.setUpdatedAt(m.getUpdatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
            return b.build();
        }).toList();
    }
}
