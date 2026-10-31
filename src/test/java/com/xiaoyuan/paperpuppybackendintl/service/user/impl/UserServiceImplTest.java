package com.xiaoyuan.paperpuppybackendintl.service.user.impl;

import com.xiaoyuan.paperpuppybackendintl.model.dto.onboarding.OnboardingSelectionContext;
import com.xiaoyuan.paperpuppybackendintl.model.entity.User;
import com.xiaoyuan.paperpuppybackendintl.repository.UserRepository;
import com.xiaoyuan.paperpuppybackendintl.service.user.assembler.UserAssembler;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    @Test
    void createUserFromEmailLoginDoesNotRequireASeedProject() {
        UserRepository userRepository = mock(UserRepository.class);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                new UserAssembler()
        );
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });

        User saved = service.createUserFromEmailLogin("new@example.com");

        assertEquals(42L, saved.getId());
        assertEquals("new@example.com", saved.getEmail());
    }

    @Test
    void createUserFromEmailLoginStoresSignupPreferences() {
        UserRepository userRepository = mock(UserRepository.class);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                new UserAssembler()
        );
        when(userRepository.findByEmail("localized@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });
        OnboardingSelectionContext context =
                OnboardingSelectionContext.of("zh_CN", "cn", "Asia/Shanghai", null);

        User saved = service.createUserFromEmailLogin("localized@example.com", context);

        assertEquals("zh-CN", saved.getPreferredLocale());
        assertEquals("CN", saved.getLastKnownCountry());
        assertEquals("Asia/Shanghai", saved.getPreferredTimeZone());
    }
}
