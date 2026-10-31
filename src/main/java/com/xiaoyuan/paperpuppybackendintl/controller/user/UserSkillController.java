package com.xiaoyuan.paperpuppybackendintl.controller.user;

import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.common.ResultUtils;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.BatchToggleSkillRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.ToggleSkillRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UnifiedSkillDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UserSkillSettingDTO;
import com.xiaoyuan.paperpuppybackendintl.model.entity.SkillRegistryItem;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserCustomSkill;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserSkillSetting;
import com.xiaoyuan.paperpuppybackendintl.repository.SkillRegistryRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserCustomSkillRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserSkillSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户技能开关管理 API
 *
 * <pre>
 * GET    /user/skills         — 列出所有可配置技能（含当前启用状态）
 * PUT    /user/skills/toggle  — 切换单个技能开关
 * PUT    /user/skills/batch   — 批量切换技能开关
 * DELETE /user/skills/{name}  — 重置某技能为默认状态
 * </pre>
 */
@RestController
@RequestMapping("/user/skills")
@RequiredArgsConstructor
@AuthCheck
public class UserSkillController {

    private final SkillRegistryRepository skillRegistryRepository;
    private final UserSkillSettingRepository userSkillSettingRepository;
    private final UserCustomSkillRepository userCustomSkillRepository;

    /**
     * 列出所有可配置技能 + 用户当前的启用状态。
     * system_locked 的技能也会返回（供前端展示"不可关闭"标记），但不允许修改。
     */
    @GetMapping
    public BaseResponse<List<UserSkillSettingDTO>> listSkills(@CurrentUserId Long userId) {
        List<SkillRegistryItem> allSkills = skillRegistryRepository.listAll();
        Map<String, UserSkillSetting> userSettings = userSkillSettingRepository.listByUserId(userId)
                .stream()
                .collect(Collectors.toMap(UserSkillSetting::getSkillName, s -> s, (a, b) -> a));

        List<UserSkillSettingDTO> result = new ArrayList<>(allSkills.size());
        for (SkillRegistryItem skill : allSkills) {
            UserSkillSettingDTO dto = new UserSkillSettingDTO();
            dto.setSkillName(skill.getSkillName());
            dto.setDisplayName(skill.getDisplayName());
            dto.setDescription(skill.getDescription());
            dto.setCategory(skill.getCategory());
            dto.setSystemLocked(skill.getSystemLocked());
            dto.setDisplayOrder(skill.getDisplayOrder());
            dto.setIcon(skill.getIcon());

            UserSkillSetting userSetting = userSettings.get(skill.getSkillName());
            if (userSetting != null) {
                dto.setEnabled(userSetting.getEnabled());
                dto.setUserSettingUpdatedAt(userSetting.getUpdatedAt());
            } else {
                dto.setEnabled(skill.getDefaultEnabled());
                dto.setUserSettingUpdatedAt(null);
            }
            result.add(dto);
        }
        return ResultUtils.success(result);
    }

    /**
     * 聚合视图：将系统 Function Tool 和用户自定义 PromptSkill 合并为统一列表。
     * 系统工具按 display_order 排序在前，自定义技能按创建时间排序在后。
     */
    @GetMapping("/all")
    public BaseResponse<List<UnifiedSkillDTO>> listAllSkills(@CurrentUserId Long userId) {
        // 1) 系统 Function Tool
        List<SkillRegistryItem> registrySkills = skillRegistryRepository.listAll();
        Map<String, UserSkillSetting> userSettings = userSkillSettingRepository.listByUserId(userId)
                .stream()
                .collect(Collectors.toMap(UserSkillSetting::getSkillName, s -> s, (a, b) -> a));

        List<UnifiedSkillDTO> result = new ArrayList<>();
        for (SkillRegistryItem skill : registrySkills) {
            UnifiedSkillDTO dto = new UnifiedSkillDTO();
            dto.setType("system_tool");
            dto.setSkillName(skill.getSkillName());
            dto.setDisplayName(skill.getDisplayName());
            dto.setDescription(skill.getDescription());
            dto.setCategory(skill.getCategory());
            dto.setSystemLocked(skill.getSystemLocked());
            dto.setDisplayOrder(skill.getDisplayOrder());
            dto.setIcon(skill.getIcon());

            UserSkillSetting userSetting = userSettings.get(skill.getSkillName());
            dto.setEnabled(userSetting != null ? userSetting.getEnabled() : skill.getDefaultEnabled());
            dto.setUpdatedAt(userSetting != null ? userSetting.getUpdatedAt() : null);
            result.add(dto);
        }

        // 2) 用户自定义 PromptSkill
        List<UserCustomSkill> customSkills = userCustomSkillRepository.listByUserId(userId);
        for (UserCustomSkill cs : customSkills) {
            UnifiedSkillDTO dto = new UnifiedSkillDTO();
            dto.setType("prompt_skill");
            dto.setSkillName(cs.getSkillName());
            dto.setDisplayName(cs.getDisplayName());
            dto.setDescription(cs.getDescription());
            dto.setCategory("custom");
            dto.setEnabled(cs.getEnabled());
            dto.setSystemLocked(false);
            dto.setCustomSkillId(cs.getId());
            dto.setSourceType(cs.getSourceType());
            dto.setVersion(cs.getVersion());
            dto.setUpdatedAt(cs.getUpdatedAt());
            result.add(dto);
        }

        return ResultUtils.success(result);
    }

    /**
     * 切换单个技能的开关。
     */
    @PutMapping("/toggle")
    public BaseResponse<UserSkillSettingDTO> toggleSkill(@CurrentUserId Long userId,
                                                         @Validated @RequestBody ToggleSkillRequest req) {
        assertNotSystemLocked(req.getSkillName());
        SkillRegistryItem skill = requireRegistered(req.getSkillName());

        UserSkillSetting saved = userSkillSettingRepository.upsert(userId, req.getSkillName(), req.getEnabled());
        return ResultUtils.success(toDTO(skill, saved));
    }

    /**
     * 批量切换技能开关（前端"按分类全部关闭/打开"场景）。
     */
    @PutMapping("/batch")
    public BaseResponse<List<UserSkillSettingDTO>> batchToggle(@CurrentUserId Long userId,
                                                               @Validated @RequestBody BatchToggleSkillRequest req) {
        List<UserSkillSettingDTO> results = new ArrayList<>(req.getSkills().size());
        for (BatchToggleSkillRequest.Item item : req.getSkills()) {
            assertNotSystemLocked(item.getSkillName());
            SkillRegistryItem skill = requireRegistered(item.getSkillName());
            UserSkillSetting saved = userSkillSettingRepository.upsert(userId, item.getSkillName(), item.getEnabled());
            results.add(toDTO(skill, saved));
        }
        return ResultUtils.success(results);
    }

    /**
     * 重置某技能为默认状态（删除用户的自定义设置）。
     */
    @DeleteMapping("/{skillName}")
    public BaseResponse<Boolean> resetSkill(@CurrentUserId Long userId,
                                            @PathVariable String skillName) {
        boolean ok = userSkillSettingRepository.softDelete(userId, skillName);
        return ResultUtils.success(ok);
    }

    // ========================= helpers =========================

    private SkillRegistryItem requireRegistered(String skillName) {
        return skillRegistryRepository.findBySkillName(skillName)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR,
                        "未知的技能: " + skillName));
    }

    private void assertNotSystemLocked(String skillName) {
        if (skillRegistryRepository.isSystemLocked(skillName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "系统核心技能不可关闭: " + skillName);
        }
    }

    private UserSkillSettingDTO toDTO(SkillRegistryItem skill, UserSkillSetting setting) {
        UserSkillSettingDTO dto = new UserSkillSettingDTO();
        dto.setSkillName(skill.getSkillName());
        dto.setDisplayName(skill.getDisplayName());
        dto.setDescription(skill.getDescription());
        dto.setCategory(skill.getCategory());
        dto.setSystemLocked(skill.getSystemLocked());
        dto.setDisplayOrder(skill.getDisplayOrder());
        dto.setIcon(skill.getIcon());
        dto.setEnabled(setting.getEnabled());
        dto.setUserSettingUpdatedAt(setting.getUpdatedAt());
        return dto;
    }
}
