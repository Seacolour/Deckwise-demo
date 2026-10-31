package com.xiaoyuan.paperpuppyagent.core.skill;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 一个可被模型调用的 skill function（对应 OpenAI function calling 的一个 tool）。
 *
 * <p>当前版本只支持“内置工具”的规范化描述（manifest），执行仍委托给现有 Tool。</p>
 */
public record SkillFunction(
        String skillId,
        String skillVersion,
        String name,
        String description,
        boolean strict,
        ObjectNode inputSchema,
        SkillScope scope,
        SkillSafety safety
) {
}

