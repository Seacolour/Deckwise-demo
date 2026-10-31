package com.xiaoyuan.paperpuppybackendintl.service.user.impl;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.exception.ThrowUtils;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.email.resend.ResendEmailClient;
import com.xiaoyuan.paperpuppybackendintl.model.dto.onboarding.OnboardingSelectionContext;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UserDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UserLoginResult;
import com.xiaoyuan.paperpuppybackendintl.model.entity.User;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserLoginRecord;
import com.xiaoyuan.paperpuppybackendintl.model.enums.IdentityProvider;
import com.xiaoyuan.paperpuppybackendintl.model.enums.UserRole;
import com.xiaoyuan.paperpuppybackendintl.repository.UserLoginRecordRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserPasswordCredentialRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserRepository;
import com.xiaoyuan.paperpuppybackendintl.service.user.AuthService;
import com.xiaoyuan.paperpuppybackendintl.service.user.UserService;
import com.xiaoyuan.paperpuppybackendintl.service.user.assembler.UserAssembler;
import com.xiaoyuan.paperpuppybackendintl.utils.security.JwtUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;

/**
 * 认证 Service（实现类）
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserAssembler userAssembler;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserLoginRecordRepository userLoginRecordRepository;
    private final ResendEmailClient resendEmailClient;
    private final UserService userService;
    private final UserPasswordCredentialRepository passwordCredentialRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);
    private static final Duration PASSWORD_CODE_TTL = Duration.ofMinutes(5);
    private static final Duration PASSWORD_CODE_COOLDOWN = Duration.ofMinutes(1);

    @Override
    @Transactional
    public UserLoginResult login(String email,
                                 String code,
                                 String ip,
                                 String userAgent,
                                 OnboardingSelectionContext onboardingContext) {
        ThrowUtils.throwIf(!StringUtils.hasText(email),
                ErrorCode.PARAMS_ERROR, "邮箱不能为空");

        // 校验验证码
        ThrowUtils.throwIf(!StringUtils.hasText(code),
                ErrorCode.PARAMS_ERROR, "验证码不能为空");
        String codeKey = buildEmailLoginCodeKey(email);
        String cachedCode = stringRedisTemplate.opsForValue().get(codeKey);
        ThrowUtils.throwIf(cachedCode == null || !cachedCode.equals(code),
                ErrorCode.PARAMS_ERROR, "验证码错误或已过期");
        // 使用一次后立即删除
        stringRedisTemplate.delete(codeKey);

        // 查询是否已有用户（排除已注销用户）
        User user = userRepository.findByEmail(email)
                .filter(u -> !Boolean.TRUE.equals(u.getDeleted()))
                .orElse(null);

        if (user != null) {
            // 已存在用户：账号状态检查
            if (Boolean.FALSE.equals(user.getActive())) {
                throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "该账号已被禁用");
            }
            if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                user.setEmailVerified(true);
                userRepository.save(user);
            }
            return buildLoginResult(user, IdentityProvider.EMAIL, ip, userAgent);
        }

        // 不存在：自动注册
        User saved = userService.createUserFromEmailLogin(email, onboardingContext);
        return buildLoginResult(saved, IdentityProvider.EMAIL, ip, userAgent);
    }

    public void sendLoginEmailCode(String email) {
        ThrowUtils.throwIf(!StringUtils.hasText(email),
                ErrorCode.PARAMS_ERROR, "邮箱不能为空");

        String code = generate6DigitCode();
        // 生成一次性幂等键：Resend 要求同一 Idempotency-Key 24h 内必须匹配相同 body；
        // 由于验证码每次不同，因此这里必须每次发送都用新的 key，避免 409 invalid_idempotent_request。
        String idempotencyKey = "login-code-" + email.toLowerCase() + "-" + generateIdempotencyNonce();
        String key = buildEmailLoginCodeKey(email);
        // 写入 Redis，5 分钟过期
        stringRedisTemplate.opsForValue().set(key, code, Duration.ofMinutes(5));

        // 发送邮件：统一走 Resend（不再走 SMTP）
        try {
            String subject = "Deckwise Demo 登录验证码";
            String text = "您本次的登录验证码是：" + code + "，5 分钟内有效。如非本人操作，请忽略。";
            String html = "<p>您本次的登录验证码是：<b>" + code + "</b>，5 分钟内有效。</p>"
                    + "<p>如非本人操作，请忽略。</p>";
            resendEmailClient.sendEmail(
                    java.util.List.of(email),
                    subject,
                    text,
                    html,
                    null,
                    idempotencyKey
            );
        } catch (RuntimeException e) {
            // 避免“码已发放但邮件没发出去”导致用户无法收到验证码
            stringRedisTemplate.delete(key);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "验证码发送失败，请稍后重试");
        }
    }

    @Override
    @Transactional
    public UserLoginResult loginWithPassword(String email, String password, String ip, String userAgent) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱或密码错误");
        }
        User user = userRepository.findByEmail(normalizeEmail(email)).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getEmailVerified())
                || !Boolean.TRUE.equals(user.getActive()) || Boolean.TRUE.equals(user.getDeleted())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱或密码错误");
        }
        String hash = passwordCredentialRepository.findPasswordHash(user.getId()).orElse(null);
        if (hash == null || !PASSWORD_ENCODER.matches(password, hash)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱或密码错误");
        }
        return buildLoginResult(user, IdentityProvider.EMAIL_PASSWORD, ip, userAgent);
    }

    @Override
    public void sendPasswordEmailCode(String email) {
        ThrowUtils.throwIf(!StringUtils.hasText(email), ErrorCode.PARAMS_ERROR, "邮箱不能为空");
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        // Do not disclose whether an account exists or has password login enabled.
        if (user == null || !Boolean.TRUE.equals(user.getEmailVerified())
                || !Boolean.TRUE.equals(user.getActive()) || Boolean.TRUE.equals(user.getDeleted())) {
            return;
        }
        String cooldownKey = buildPasswordCodeKey(normalizedEmail) + ":cooldown";
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", PASSWORD_CODE_COOLDOWN);
        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }
        String codeKey = buildPasswordCodeKey(normalizedEmail);
        String code = generate6DigitCode();
        stringRedisTemplate.opsForValue().set(codeKey, code, PASSWORD_CODE_TTL);
        try {
            resendEmailClient.sendEmail(
                    java.util.List.of(normalizedEmail),
                    "Deckwise Demo 密码设置验证码",
                    "您本次的密码设置或重置验证码是：" + code + "，5 分钟内有效。如非本人操作，请忽略。",
                    "<p>您本次的密码设置或重置验证码是：<b>" + code
                            + "</b>，5 分钟内有效。</p><p>如非本人操作，请忽略。</p>",
                    null,
                    "password-code-" + normalizedEmail + "-" + generateIdempotencyNonce());
        } catch (RuntimeException e) {
            stringRedisTemplate.delete(codeKey);
            stringRedisTemplate.delete(cooldownKey);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "验证码发送失败，请稍后重试");
        }
    }

    @Override
    @Transactional
    public void updatePassword(String email, String code, String newPassword) {
        ThrowUtils.throwIf(!StringUtils.hasText(email) || !StringUtils.hasText(code),
                ErrorCode.PARAMS_ERROR, "验证码错误或已过期");
        validatePassword(newPassword);
        String normalizedEmail = normalizeEmail(email);
        String cachedCode = stringRedisTemplate.opsForValue()
                .getAndDelete(buildPasswordCodeKey(normalizedEmail));
        if (cachedCode == null || !cachedCode.equals(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码错误或已过期");
        }
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getEmailVerified())
                || !Boolean.TRUE.equals(user.getActive()) || Boolean.TRUE.equals(user.getDeleted())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码错误或已过期");
        }
        passwordCredentialRepository.upsertPasswordHash(user.getId(), PASSWORD_ENCODER.encode(newPassword));
    }

    @Override
    public boolean hasPassword(Long userId) {
        return passwordCredentialRepository.findPasswordHash(userId).isPresent();
    }

    private void validatePassword(String password) {
        if (!StringUtils.hasText(password) || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码至少 12 位，且不能超过 72 字节");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String buildPasswordCodeKey(String email) {
        return "login:email:password-code:" + email;
    }

    private static String generateIdempotencyNonce() {
        // 短随机串即可（Idempotency-Key <= 256 chars），避免泄露验证码本身
        byte[] bytes = new byte[12];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public void logout(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        String redisKey = "login:token:" + token;
        stringRedisTemplate.delete(redisKey);
    }

    @Override
    public UserDTO getCurrentUser(String token) {
        User user = requireUserFromToken(token);
        return userAssembler.toDTO(user);
    }

    @Override
    public UserDTO updateCurrentUser(String token, String name) {
        User user = requireUserFromToken(token);
        if (StringUtils.hasText(name)) {
            user.setName(name);
        }
        User saved = userRepository.save(user);
        return userAssembler.toDTO(saved);
    }

    @Override
    @Transactional
    public void deleteCurrentUser(String token) {
        User user = requireUserFromToken(token);


        user.setActive(false);
        user.setDeleted(true);
        // 释放 email 唯一约束：追加 deleted + 16进制时间戳，使同一 email 可重新注册，且可追溯删除时间
        if (user.getEmail() != null) {
            String hexTs = Long.toHexString(System.currentTimeMillis());
            user.setEmail(user.getEmail() + "::deleted::" + hexTs);
        }
        userRepository.save(user);

        // 失效当前 token：放在事务提交后执行，避免 DB 回滚但 token 已失效导致的体验问题
        runAfterCommit(() -> logout(token));
    }

    @Override
    public User requireUserFromToken(String token) {
        ThrowUtils.throwIf(!StringUtils.hasText(token),
                ErrorCode.NOT_LOGIN_ERROR, "未登录");

        // token 必须存在于 Redis（用于登出失效/黑名单控制）
        String redisKey = "login:token:" + token;
        String cachedUserId = stringRedisTemplate.opsForValue().get(redisKey);
        ThrowUtils.throwIf(!StringUtils.hasText(cachedUserId),
                ErrorCode.TOKEN_INVALID, "登录已失效，请重新登录");

        Long userId = JwtUtil.getUserId(token);
        ThrowUtils.throwIf(userId == null,
                ErrorCode.TOKEN_INVALID, "未登录");

        // 防止 token 被篡改/串用：Redis 中记录的 userId 必须与 token 解析一致
        if (!cachedUserId.equals(String.valueOf(userId))) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "登录已失效，请重新登录");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在"));
        if (Boolean.TRUE.equals(user.getDeleted())) {
            // 已注销用户：清除残留 token 并拒绝访问
            stringRedisTemplate.delete(redisKey);
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "账号已注销，请重新注册");
        }
        return user;
    }

    /**
     * 构建登录结果：更新最后登录时间、写 Redis token、记录登录日志
     */
    private UserLoginResult buildLoginResult(User user, IdentityProvider provider,
                                             String ip, String userAgent) {
        // 更新最后登录时间
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        // 记录登录日志
        UserLoginRecord record = new UserLoginRecord();
        record.setUserId(user.getId());
        record.setLoginTime(OffsetDateTime.now());
        record.setIpAddress(ip);
        record.setUserAgent(userAgent);
        record.setProvider(provider);
        record.setSuccess(true);
        userLoginRecordRepository.save(record);

        return issueSessionResult(user);
    }

    private UserLoginResult issueSessionResult(User user) {
        // 生成登录 JWT，并写入 Redis（便于未来做登出/黑名单）
        String token = JwtUtil.generateToken(user.getId(), user.getEmail());
        String redisKey = "login:token:" + token;
        // Redis 写入放在事务提交后：避免 DB 回滚但 token 已生效（产生“幽灵登录”）
        runAfterCommit(() ->
                stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(user.getId()), Duration.ofDays(30))
        );

        // 返回结果
        UserDTO userDTO = userAssembler.toDTO(user);
        UserLoginResult result = new UserLoginResult();
        result.setUser(userDTO);
        result.setUserToken(token);
        return result;
    }

    private void runAfterCommit(Runnable action) {
        if (action == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private String buildEmailLoginCodeKey(String email) {
        return "login:email:code:" + email.toLowerCase();
    }

    private String generate6DigitCode() {
        int code = SECURE_RANDOM.nextInt(900000) + 100000;
        return String.valueOf(code);
    }
}


