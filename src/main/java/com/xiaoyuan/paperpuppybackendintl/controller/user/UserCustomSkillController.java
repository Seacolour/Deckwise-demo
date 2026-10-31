package com.xiaoyuan.paperpuppybackendintl.controller.user;

import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.common.ResultUtils;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UpdateCustomSkillRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UserCustomSkillDTO;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserCustomSkill;
import com.xiaoyuan.paperpuppybackendintl.repository.UserCustomSkillRepository;
import com.xiaoyuan.paperpuppybackendintl.service.skill.ParsedSkill;
import com.xiaoyuan.paperpuppybackendintl.service.skill.SkillImportService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户自定义 PromptSkill 管理 API
 *
 * <pre>
 * GET    /user/custom-skills              — 列出所有自定义技能
 * POST   /user/custom-skills/import       — 通过 .md / .zip 文件导入
 * PUT    /user/custom-skills/{id}         — 更新内容/元数据
 * PUT    /user/custom-skills/{id}/toggle  — 切换启用/禁用
 * DELETE /user/custom-skills/{id}         — 删除
 * </pre>
 */
@RestController
@RequestMapping("/user/custom-skills")
@RequiredArgsConstructor
@AuthCheck
public class UserCustomSkillController {

    private final UserCustomSkillRepository customSkillRepository;
    private final SkillImportService skillImportService;

    @Value("${user.custom-skills.max-count:30}")
    private int maxSkillCount;

    // ========================= 列表 =========================

    @GetMapping
    @Operation(operationId = "listUserCustomSkills")
    public BaseResponse<List<UserCustomSkillDTO>> listSkills(@CurrentUserId Long userId) {
        List<UserCustomSkill> skills = customSkillRepository.listByUserId(userId);
        List<UserCustomSkillDTO> dtos = skills.stream().map(this::toDTO).toList();
        return ResultUtils.success(dtos);
    }

    // ========================= 导入 =========================

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<UserCustomSkillDTO> importSkill(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String nameOverride,
            @RequestParam(value = "description", required = false) String descOverride,
            @CurrentUserId Long userId) {

        assertQuotaNotExceeded(userId);

        ParsedSkill parsed = skillImportService.parseFromFile(file);

        String finalName = (nameOverride != null && !nameOverride.isBlank()) ? nameOverride.trim() : parsed.name();
        String finalDesc = (descOverride != null && !descOverride.isBlank()) ? descOverride.trim() : parsed.description();

        ParsedSkill merged = new ParsedSkill(
                finalName,
                parsed.displayName(),
                finalDesc,
                parsed.version(),
                parsed.content()
        );
        skillImportService.validate(merged);

        assertSkillNameNotDuplicate(userId, merged.name());

        String sourceType = detectSourceType(file);
        UserCustomSkill entity = new UserCustomSkill();
        entity.setUserId(userId);
        entity.setSkillName(merged.name());
        entity.setDisplayName(merged.displayName());
        entity.setDescription(merged.description());
        entity.setContent(merged.content());
        entity.setVersion(merged.version());
        entity.setSourceType(sourceType);
        entity.setEnabled(true);

        UserCustomSkill saved = customSkillRepository.insert(entity);
        return ResultUtils.success(toDTO(saved));
    }

    // ========================= 更新 =========================

    @PutMapping("/{id}")
    public BaseResponse<UserCustomSkillDTO> updateSkill(
            @CurrentUserId Long userId,
            @PathVariable Long id,
            @Validated @RequestBody UpdateCustomSkillRequest req) {

        UserCustomSkill existing = requireOwned(userId, id);

        boolean ok = customSkillRepository.update(
                existing.getId(),
                req.getDisplayName(),
                req.getDescription(),
                req.getContent(),
                req.getVersion()
        );
        if (!ok) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败");
        }
        return ResultUtils.success(toDTO(customSkillRepository.findById(id).orElse(existing)));
    }

    // ========================= 开关 =========================

    @PutMapping("/{id}/toggle")
    @Operation(operationId = "toggleUserCustomSkill")
    public BaseResponse<Boolean> toggleSkill(
            @CurrentUserId Long userId,
            @PathVariable Long id,
            @RequestParam("enabled") boolean enabled) {

        requireOwned(userId, id);
        boolean ok = customSkillRepository.setEnabled(id, enabled);
        return ResultUtils.success(ok);
    }

    // ========================= 删除 =========================

    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> deleteSkill(
            @CurrentUserId Long userId,
            @PathVariable Long id) {

        requireOwned(userId, id);
        boolean ok = customSkillRepository.softDelete(id);
        return ResultUtils.success(ok);
    }

    // ========================= helpers =========================

    private UserCustomSkill requireOwned(Long userId, Long skillId) {
        UserCustomSkill skill = customSkillRepository.findById(skillId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "技能不存在"));
        if (!skill.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作此技能");
        }
        return skill;
    }

    private void assertQuotaNotExceeded(Long userId) {
        int count = customSkillRepository.countByUserId(userId);
        if (count >= maxSkillCount) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "自定义技能数量已达上限（" + maxSkillCount + "）");
        }
    }

    private void assertSkillNameNotDuplicate(Long userId, String skillName) {
        if (customSkillRepository.findByUserIdAndSkillName(userId, skillName).isPresent()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "同名技能已存在: " + skillName);
        }
    }

    private static String detectSourceType(MultipartFile file) {
        String name = (file.getOriginalFilename() != null ? file.getOriginalFilename() : "").toLowerCase();
        if (name.endsWith(".zip")) return "zip";
        if (name.endsWith(".md")) return "file";
        return "manual";
    }

    private UserCustomSkillDTO toDTO(UserCustomSkill entity) {
        UserCustomSkillDTO dto = new UserCustomSkillDTO();
        dto.setId(entity.getId());
        dto.setSkillName(entity.getSkillName());
        dto.setDisplayName(entity.getDisplayName());
        dto.setDescription(entity.getDescription());
        dto.setContent(entity.getContent());
        dto.setVersion(entity.getVersion());
        dto.setSourceType(entity.getSourceType());
        dto.setEnabled(entity.getEnabled());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
