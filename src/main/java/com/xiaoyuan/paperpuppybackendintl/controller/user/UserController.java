package com.xiaoyuan.paperpuppybackendintl.controller.user;

import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.common.ResultUtils;
import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import com.xiaoyuan.paperpuppybackendintl.model.dto.checkin.CheckinRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.checkin.CheckinResultDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.checkin.CheckinStatusDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.onboarding.OnboardingSelectionContext;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.*;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserCheckinLog;
import com.xiaoyuan.paperpuppybackendintl.utils.security.JwtUtil;
import com.xiaoyuan.paperpuppybackendintl.service.user.AuthService;
import com.xiaoyuan.paperpuppybackendintl.service.user.UserCheckinService;
import com.xiaoyuan.paperpuppybackendintl.service.user.UserService;
import com.xiaoyuan.paperpuppybackendintl.utils.NetUtils;
import com.xiaoyuan.paperpuppybackendintl.utils.security.WebSessionCookieService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User Controller
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final UserCheckinService userCheckinService;
    private final WebSessionCookieService webSessionCookieService;

    @GetMapping("/{id}")
    @AuthCheck
    public BaseResponse<UserDTO> getUserById(@PathVariable Long id) {
        return ResultUtils.success(userService.getPublicUserById(id));
    }

    /**
     * 登录
     *
     * @param request
     * @return
     */
    @PostMapping("/login")
    public BaseResponse<UserLoginResult> login(@RequestBody UserLoginRequest userLoginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        String ip = NetUtils.getIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        OnboardingSelectionContext onboardingContext = onboardingContext(
                request,
                userLoginRequest.getLocale(),
                userLoginRequest.getCountry(),
                userLoginRequest.getTimeZone());
        UserLoginResult login = authService.login(userLoginRequest.getEmail(),
                userLoginRequest.getCode(), ip, userAgent, onboardingContext);
        webSessionCookieService.setSessionCookie(response, login.getUserToken());
        return ResultUtils.success(login);
    }

    /**
     * 发送登录邮箱验证码
     *
     * @param request
     * @return
     */
    @PostMapping("/login/email-code")
    public BaseResponse<Boolean> sendLoginEmailCode(@RequestBody EmailCodeRequest emailCodeRequest,
            HttpServletRequest request) {
        authService.sendLoginEmailCode(emailCodeRequest.getEmail());
        return ResultUtils.success(true);
    }

    /** Existing, verified email accounts may optionally use a password. */
    @PostMapping("/login/password")
    public BaseResponse<UserLoginResult> loginWithPassword(@RequestBody PasswordLoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        UserLoginResult login = authService.loginWithPassword(body.getEmail(), body.getPassword(),
                NetUtils.getIpAddress(request), request.getHeader("User-Agent"));
        webSessionCookieService.setSessionCookie(response, login.getUserToken());
        return ResultUtils.success(login);
    }

    /** Always returns success for unknown emails, avoiding account enumeration. */
    @PostMapping("/password/email-code")
    public BaseResponse<Boolean> sendPasswordEmailCode(@RequestBody EmailCodeRequest body) {
        authService.sendPasswordEmailCode(body.getEmail());
        return ResultUtils.success(true);
    }

    /** The same email-verification flow sets an initial password or resets it. */
    @PostMapping("/password")
    public BaseResponse<Boolean> updatePassword(@RequestBody PasswordUpdateRequest body) {
        authService.updatePassword(body.getEmail(), body.getCode(), body.getNewPassword());
        return ResultUtils.success(true);
    }

    @GetMapping("/password/status")
    @AuthCheck
    public BaseResponse<Boolean> hasPassword(@CurrentUserId Long userId) {
        return ResultUtils.success(authService.hasPassword(userId));
    }

    private OnboardingSelectionContext onboardingContext(HttpServletRequest request,
                                                         String locale,
                                                         String country,
                                                         String timeZone) {
        String acceptLanguage = request == null ? null : request.getHeader("Accept-Language");
        return OnboardingSelectionContext.of(locale, country, timeZone, acceptLanguage);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = JwtUtil.getTokenFromRequest(request);
        authService.logout(token);
        webSessionCookieService.clearSessionCookie(response);
        return ResultUtils.success(true);
    }

    /**
     * 更新当前登录用户
     */
    @PutMapping("/update/my")
    @AuthCheck
    public BaseResponse<UserDTO> updateCurrentUser(HttpServletRequest request,
            @RequestBody UpdateUserRequest body) {
        String token = JwtUtil.getTokenFromRequest(request);
        return ResultUtils.success(authService.updateCurrentUser(token, body.getName()));
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/get/login")
    @AuthCheck
    public BaseResponse<UserDTO> getLoginUser(HttpServletRequest request) {
        String token = JwtUtil.getTokenFromRequest(request);
        return ResultUtils.success(authService.getCurrentUser(token));
    }

    /**
     * 删除当前登录用户
     */
    @DeleteMapping("/delete/my")
    @AuthCheck
    public BaseResponse<Boolean> deleteCurrentUser(HttpServletRequest request) {
        String token = JwtUtil.getTokenFromRequest(request);
        authService.deleteCurrentUser(token);
        return ResultUtils.success(true);
    }

    @PostMapping("/checkin")
    @AuthCheck
    public BaseResponse<CheckinResultDTO> checkin(@RequestBody(required = false) CheckinRequest body,
            @CurrentUserId Long userId,
            HttpServletRequest request) {
        String platform = request.getHeader("X-App-Platform");
        String appVersion = request.getHeader("X-App-Version");
        String ip = NetUtils.getIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        return ResultUtils.success(userCheckinService.checkin(userId, platform, appVersion, ip, userAgent, body));
    }

    @GetMapping("/checkin/status")
    @AuthCheck
    public BaseResponse<CheckinStatusDTO> checkinStatus(@CurrentUserId Long userId) {
        return ResultUtils.success(userCheckinService.getStatus(userId));
    }

    @GetMapping("/checkin/logs")
    @AuthCheck
    public BaseResponse<List<UserCheckinLog>> checkinLogs(@RequestParam(defaultValue = "7") int days,
            @CurrentUserId Long userId) {
        return ResultUtils.success(userCheckinService.listLogs(userId, days));
    }

}
