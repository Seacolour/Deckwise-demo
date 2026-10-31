package com.xiaoyuan.paperpuppyagent.core.skill.binding;

import java.util.List;

/**
 * 单个 skill.binding.json 的根对象。
 */
public record SkillBindingManifest(
        String skillId,
        String version,
        List<SkillFunctionBinding> bindings
) {
}
