package com.xiaoyuan.paperpuppybackendintl.service.user.impl;

import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.email.resend.ResendEmailClient;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UserDTO;
import com.xiaoyuan.paperpuppybackendintl.model.entity.User;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserLoginRecord;
import com.xiaoyuan.paperpuppybackendintl.repository.UserLoginRecordRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserPasswordCredentialRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserRepository;
import com.xiaoyuan.paperpuppybackendintl.service.user.UserService;
import com.xiaoyuan.paperpuppybackendintl.service.user.assembler.UserAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServicePasswordTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserPasswordCredentialRepository credentials = mock(UserPasswordCredentialRepository.class);
    private final UserLoginRecordRepository loginRecords = mock(UserLoginRecordRepository.class);
    private final UserAssembler assembler = mock(UserAssembler.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ResendEmailClient email = mock(ResendEmailClient.class);
    private final UserService userService = mock(UserService.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private AuthServiceImpl auth;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        auth = new AuthServiceImpl(users, assembler, redis, loginRecords, email, userService, credentials);
    }

    @Test
    void verifiedAccountCanSetPasswordAndLoginWithoutCreatingAnotherUser() {
        User user = verifiedUser();
        when(users.findByEmail("me@example.com")).thenReturn(Optional.of(user));
        when(values.setIfAbsent(eq("login:email:password-code:me@example.com:cooldown"),
                eq("1"), eq(Duration.ofMinutes(1)))).thenReturn(true);

        auth.sendPasswordEmailCode(" Me@Example.com ");

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("login:email:password-code:me@example.com"),
                code.capture(), eq(Duration.ofMinutes(5)));
        assertTrue(code.getValue().matches("[0-9]{6}"));
        verify(email).sendEmail(eq(java.util.List.of("me@example.com")), anyString(),
                contains(code.getValue()), anyString(), isNull(), anyString());

        when(values.getAndDelete("login:email:password-code:me@example.com"))
                .thenReturn(code.getValue());
        auth.updatePassword("ME@example.com", code.getValue(), "long-test-password-123");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(credentials).upsertPasswordHash(eq(42L), hash.capture());
        assertNotEquals("long-test-password-123", hash.getValue());
        assertTrue(new BCryptPasswordEncoder().matches("long-test-password-123", hash.getValue()));

        when(credentials.findPasswordHash(42L)).thenReturn(Optional.of(hash.getValue()));
        when(assembler.toDTO(user)).thenReturn(new UserDTO());
        assertNotNull(auth.loginWithPassword("ME@example.com", "long-test-password-123",
                "127.0.0.1", "test-client").getUserToken());
        verify(loginRecords).save(argThat((UserLoginRecord record) ->
                record.getProvider().name().equals("EMAIL_PASSWORD") && record.getUserId().equals(42L)));
        verify(userService, never()).createUserFromEmailLogin(anyString(), any());
    }

    @Test
    void unknownEmailDoesNotSendPasswordCodeOrRevealAccount() {
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        auth.sendPasswordEmailCode("missing@example.com");
        verifyNoInteractions(email, credentials);
    }

    @Test
    void wrongCodeIsConsumedAndDoesNotSetPassword() {
        when(values.getAndDelete("login:email:password-code:me@example.com"))
                .thenReturn("123456");
        assertThrows(BusinessException.class,
                () -> auth.updatePassword("me@example.com", "654321", "long-test-password-123"));
        verifyNoInteractions(credentials);
    }

    @Test
    void weakPasswordIsRejectedBeforeConsumingCode() {
        assertThrows(BusinessException.class,
                () -> auth.updatePassword("me@example.com", "123456", "short"));
        verify(values, never()).getAndDelete(anyString());
    }

    @Test
    void incorrectPasswordCannotLogin() {
        when(users.findByEmail("me@example.com")).thenReturn(Optional.of(verifiedUser()));
        when(credentials.findPasswordHash(42L)).thenReturn(Optional.of(
                new BCryptPasswordEncoder(4).encode("the-correct-password")));
        assertThrows(BusinessException.class,
                () -> auth.loginWithPassword("me@example.com", "wrong-password", "127.0.0.1", "test"));
        verifyNoInteractions(loginRecords);
    }

    @Test
    void unverifiedAccountCannotUsePasswordEvenWhenCredentialExists() {
        User user = verifiedUser();
        user.setEmailVerified(false);
        when(users.findByEmail("me@example.com")).thenReturn(Optional.of(user));
        assertThrows(BusinessException.class,
                () -> auth.loginWithPassword("me@example.com", "long-test-password-123",
                        "127.0.0.1", "test"));
        verifyNoInteractions(credentials, loginRecords);
    }

    @Test
    void passwordLongerThanBcryptLimitCannotAuthenticateByMatchingPrefix() {
        String prefix = "a".repeat(72);
        assertThrows(BusinessException.class,
                () -> auth.loginWithPassword("me@example.com", prefix + "suffix", "127.0.0.1", "test"));
        verifyNoInteractions(users, credentials);
    }

    private User verifiedUser() {
        User user = new User();
        user.setId(42L);
        user.setEmail("me@example.com");
        user.setEmailVerified(true);
        user.setActive(true);
        user.setDeleted(false);
        return user;
    }
}
