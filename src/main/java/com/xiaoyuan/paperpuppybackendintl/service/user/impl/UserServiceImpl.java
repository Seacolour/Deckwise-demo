package com.xiaoyuan.paperpuppybackendintl.service.user.impl;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.exception.ThrowUtils;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UserDTO;
import com.xiaoyuan.paperpuppybackendintl.model.entity.User;
import com.xiaoyuan.paperpuppybackendintl.model.enums.IdentityProvider;
import com.xiaoyuan.paperpuppybackendintl.model.enums.UserRole;
import com.xiaoyuan.paperpuppybackendintl.repository.UserRepository;
import com.xiaoyuan.paperpuppybackendintl.model.dto.onboarding.OnboardingSelectionContext;
import com.xiaoyuan.paperpuppybackendintl.service.user.UserService;
import com.xiaoyuan.paperpuppybackendintl.service.user.assembler.UserAssembler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


/**
 * 用户 Service（实现类）
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserAssembler userAssembler;

    @Override
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在"));
        return userAssembler.toDTO(user);
    }

    @Override
    public UserDTO getPublicUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在"));
        return userAssembler.toPublicDTO(user);
    }

    @Override
    public User getUserEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在"));
    }

    @Override
    @Transactional
    public User createUserFromEmailLogin(String email) {
        return createUserFromEmailLogin(email, OnboardingSelectionContext.EMPTY);
    }

    @Override
    @Transactional
    public User createUserFromEmailLogin(String email, OnboardingSelectionContext onboardingContext) {
        ThrowUtils.throwIf(!StringUtils.hasText(email),
                ErrorCode.PARAMS_ERROR, "邮箱不能为空");

        // 理论上上层已判断不存在；这里再兜底，避免并发下重复创建（已注销用户不阻止重新注册）
        userRepository.findByEmail(email)
                .filter(existing -> !Boolean.TRUE.equals(existing.getDeleted()))
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.ACCOUNT_CONFLICT, "邮箱已被注册");
                });

        User user = new User();
        user.setEmail(email);
        // 邮箱验证码已通过，相当于完成邮箱验证
        user.setEmailVerified(true);
        user.setName(null);
        user.setSignUpProvider(IdentityProvider.EMAIL.toDatabaseValue());
        user.setActive(true);
        user.setDeleted(false);
        user.setRole(UserRole.USER);
        applySignupPreferences(user, onboardingContext);

        User saved = userRepository.save(user);
        return saved;
    }

    private void applySignupPreferences(User user, OnboardingSelectionContext onboardingContext) {
        OnboardingSelectionContext context = normalizeContext(onboardingContext);
        if (user == null || context.isEmpty()) {
            return;
        }
        if (StringUtils.hasText(context.locale())) {
            user.setPreferredLocale(context.locale());
        }
        if (StringUtils.hasText(context.country())) {
            user.setLastKnownCountry(context.country());
        }
        if (StringUtils.hasText(context.timeZone())) {
            user.setPreferredTimeZone(context.timeZone());
        }
    }

    private OnboardingSelectionContext normalizeContext(OnboardingSelectionContext onboardingContext) {
        return onboardingContext == null ? OnboardingSelectionContext.EMPTY : onboardingContext;
    }

}
