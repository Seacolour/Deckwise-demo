package com.xiaoyuan.paperpuppybackendintl.controller.user;

import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.common.ResultUtils;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.CreateUserRuleRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UpdateUserRuleRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UserRuleDTO;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserRule;
import com.xiaoyuan.paperpuppybackendintl.repository.UserRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/rules")
@RequiredArgsConstructor
@AuthCheck
public class UserRuleController {

    private static final int MAX_RULES_PER_USER = 50;

    private final UserRuleRepository userRuleRepository;

    @GetMapping
    public BaseResponse<List<UserRuleDTO>> listRules(@CurrentUserId Long userId) {
        List<UserRuleDTO> list = userRuleRepository.listByUserId(userId)
                .stream().map(this::toDTO).toList();
        return ResultUtils.success(list);
    }

    @PostMapping
    public BaseResponse<UserRuleDTO> createRule(@CurrentUserId Long userId,
                                                @Validated @RequestBody CreateUserRuleRequest req) {
        int count = userRuleRepository.countByUserId(userId);
        if (count >= MAX_RULES_PER_USER) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "规则数量已达上限（" + MAX_RULES_PER_USER + "条）");
        }
        UserRule rule = new UserRule();
        rule.setUserId(userId);
        rule.setContent(req.getContent());
        rule.setEnabled(true);
        rule.setSource("user");
        UserRule saved = userRuleRepository.insert(rule);
        return ResultUtils.success(toDTO(saved));
    }

    @PutMapping("/{ruleId}")
    public BaseResponse<Boolean> updateRule(@CurrentUserId Long userId,
                                            @PathVariable Long ruleId,
                                            @Validated @RequestBody UpdateUserRuleRequest req) {
        UserRule rule = userRuleRepository.findById(ruleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "规则不存在"));
        if (!rule.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作此规则");
        }
        boolean ok = userRuleRepository.updateContent(ruleId, req.getContent());
        return ResultUtils.success(ok);
    }

    @PutMapping("/{ruleId}/toggle")
    public BaseResponse<Boolean> toggleRule(@CurrentUserId Long userId,
                                            @PathVariable Long ruleId,
                                            @RequestParam boolean enabled) {
        UserRule rule = userRuleRepository.findById(ruleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "规则不存在"));
        if (!rule.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作此规则");
        }
        boolean ok = userRuleRepository.setEnabled(ruleId, enabled);
        return ResultUtils.success(ok);
    }

    @DeleteMapping("/{ruleId}")
    public BaseResponse<Boolean> deleteRule(@CurrentUserId Long userId,
                                            @PathVariable Long ruleId) {
        UserRule rule = userRuleRepository.findById(ruleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "规则不存在"));
        if (!rule.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作此规则");
        }
        boolean ok = userRuleRepository.softDelete(ruleId);
        return ResultUtils.success(ok);
    }

    private UserRuleDTO toDTO(UserRule rule) {
        UserRuleDTO dto = new UserRuleDTO();
        dto.setId(rule.getId());
        dto.setContent(rule.getContent());
        dto.setEnabled(rule.getEnabled());
        dto.setSource(rule.getSource());
        dto.setCreatedAt(rule.getCreatedAt());
        dto.setUpdatedAt(rule.getUpdatedAt());
        return dto;
    }
}
