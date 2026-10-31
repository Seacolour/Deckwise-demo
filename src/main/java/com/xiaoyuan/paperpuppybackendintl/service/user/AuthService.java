package com.xiaoyuan.paperpuppybackendintl.service.user;

import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UserDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UserLoginResult;
import com.xiaoyuan.paperpuppybackendintl.model.dto.onboarding.OnboardingSelectionContext;
import com.xiaoyuan.paperpuppybackendintl.model.entity.User;

/**
 *  认证 Service（接口 + impl）
 */
public interface AuthService {

    default UserLoginResult login(String email, String code, String ip, String userAgent) {
        return login(email, code, ip, userAgent, OnboardingSelectionContext.EMPTY);
    }

    UserLoginResult login(String email, String code, String ip, String userAgent,
                          OnboardingSelectionContext onboardingContext);

    void sendLoginEmailCode(String email);

    UserLoginResult loginWithPassword(String email, String password, String ip, String userAgent);

    void sendPasswordEmailCode(String email);

    void updatePassword(String email, String code, String newPassword);

    boolean hasPassword(Long userId);

    void logout(String token);

    UserDTO getCurrentUser(String token);

    UserDTO updateCurrentUser(String token, String name);

    void deleteCurrentUser(String token);

    User requireUserFromToken(String token);
}
