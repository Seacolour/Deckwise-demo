package com.xiaoyuan.paperpuppybackendintl.service.user;

import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UserDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.onboarding.OnboardingSelectionContext;
import com.xiaoyuan.paperpuppybackendintl.model.entity.User;

/**
 * 用户 Service（接口 + impl）
 */
public interface UserService {

    UserDTO getUserById(Long id);

    /**
     * 获取用户公开信息（不含 email、role 等敏感字段）
     */
    UserDTO getPublicUserById(Long id);

    User getUserEntityById(Long id);

    /**
     * 邮箱验证码登录的“首次登录自动注册”：
     * - email 已通过验证码校验，因此直接标记 emailVerified=true
     * - 无需密码，纯邮箱 + 验证码即可
     * - 记录语言、地区与时区偏好；工作区由用户在 Web 首次操作时创建
     */
    default User createUserFromEmailLogin(String email) {
        return createUserFromEmailLogin(email, OnboardingSelectionContext.EMPTY);
    }

    User createUserFromEmailLogin(String email, OnboardingSelectionContext onboardingContext);

}
