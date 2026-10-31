package com.xiaoyuan.paperpuppybackendintl.controller.user;

import com.xiaoyuan.paperpuppybackendintl.model.dto.onboarding.OnboardingSelectionContext;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UserLoginRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UserLoginResult;
import com.xiaoyuan.paperpuppybackendintl.service.user.AuthService;
import com.xiaoyuan.paperpuppybackendintl.service.user.UserCheckinService;
import com.xiaoyuan.paperpuppybackendintl.service.user.UserService;
import com.xiaoyuan.paperpuppybackendintl.utils.security.WebSessionCookieService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerOnboardingContextTest {

    @Test
    void loginPassesRequestLocaleCountryAndTimeZoneToAuthService() {
        AuthService authService = mock(AuthService.class);
        UserLoginResult loginResult = new UserLoginResult();
        loginResult.setUserToken("token");
        when(authService.login(eq("new@example.com"), eq("123456"), any(), any(),
                any(OnboardingSelectionContext.class))).thenReturn(loginResult);
        UserController controller = new UserController(
                mock(UserService.class),
                authService,
                mock(UserCheckinService.class),
                mock(WebSessionCookieService.class)
        );
        UserLoginRequest body = new UserLoginRequest();
        body.setEmail("new@example.com");
        body.setCode("123456");
        body.setLocale("zh_CN");
        body.setCountry("cn");
        body.setTimeZone("Asia/Shanghai");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

        controller.login(body, request, new MockHttpServletResponse());

        ArgumentCaptor<OnboardingSelectionContext> contextCaptor =
                ArgumentCaptor.forClass(OnboardingSelectionContext.class);
        verify(authService).login(eq("new@example.com"), eq("123456"), any(), any(), contextCaptor.capture());
        OnboardingSelectionContext context = contextCaptor.getValue();
        assertEquals("zh-CN", context.locale());
        assertEquals("CN", context.country());
        assertEquals("Asia/Shanghai", context.timeZone());
        assertEquals("zh-CN,zh;q=0.9,en;q=0.8", context.acceptLanguage());
    }
}
