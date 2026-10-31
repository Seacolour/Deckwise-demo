package com.xiaoyuan.paperpuppybackendintl.controller.user;

import com.xiaoyuan.paperpuppybackendintl.model.dto.user.EmailCodeRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.PasswordLoginRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.PasswordUpdateRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UserLoginResult;
import com.xiaoyuan.paperpuppybackendintl.service.user.AuthService;
import com.xiaoyuan.paperpuppybackendintl.service.user.UserCheckinService;
import com.xiaoyuan.paperpuppybackendintl.service.user.UserService;
import com.xiaoyuan.paperpuppybackendintl.utils.security.WebSessionCookieService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserControllerPasswordTest {

    @Test
    void passwordEndpointsDelegateToAuthAndIssueTheNormalSessionCookie() {
        AuthService auth = mock(AuthService.class);
        WebSessionCookieService cookies = mock(WebSessionCookieService.class);
        UserController controller = new UserController(mock(UserService.class), auth,
                mock(UserCheckinService.class), cookies);

        EmailCodeRequest codeRequest = new EmailCodeRequest();
        codeRequest.setEmail("me@example.com");
        assertEquals(true, controller.sendPasswordEmailCode(codeRequest).getData());
        verify(auth).sendPasswordEmailCode("me@example.com");

        PasswordUpdateRequest update = new PasswordUpdateRequest();
        update.setEmail("me@example.com");
        update.setCode("123456");
        update.setNewPassword("long-test-password-123");
        assertEquals(true, controller.updatePassword(update).getData());
        verify(auth).updatePassword("me@example.com", "123456", "long-test-password-123");

        PasswordLoginRequest loginRequest = new PasswordLoginRequest();
        loginRequest.setEmail("me@example.com");
        loginRequest.setPassword("long-test-password-123");
        UserLoginResult result = new UserLoginResult();
        result.setUserToken("new-token");
        when(auth.loginWithPassword(eq("me@example.com"), eq("long-test-password-123"),
                any(), eq("test-client"))).thenReturn(result);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "test-client");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals(result, controller.loginWithPassword(loginRequest, request, response).getData());
        verify(cookies).setSessionCookie(response, "new-token");

        when(auth.hasPassword(42L)).thenReturn(true);
        assertEquals(true, controller.hasPassword(42L).getData());
    }
}
