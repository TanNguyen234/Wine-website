package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.service.PasswordResetService;
import com.strongwine.strongwine.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private PasswordResetService passwordResetService;

    @InjectMocks
    private AuthController authController;

    @Test
    @DisplayName("Attempting to self-register as SHIPPER must be blocked")
    void testSelfRegisterAsShipperBlocked() {
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        String result = authController.handleRegister(
                "shipper_wannabe",
                "shipper@example.com",
                "secret123",
                "secret123",
                "SHIPPER",
                redirectAttributes
        );

        assertThat(result).isEqualTo("redirect:/register");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("error");
        assertThat(redirectAttributes.getFlashAttributes().get("error").toString())
                .isEqualTo("Không được phép tự đăng ký tài khoản Shipper. Tài khoản Shipper chỉ được cấp bởi Quản trị viên.");
        verify(userService, never()).createUser(any());
    }

    @Test
    @DisplayName("Attempting to self-register as ADMIN must be blocked")
    void testSelfRegisterAsAdminBlocked() {
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        String result = authController.handleRegister(
                "admin_wannabe",
                "admin@example.com",
                "secret123",
                "secret123",
                "ADMIN",
                redirectAttributes
        );

        assertThat(result).isEqualTo("redirect:/register");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("error");
        assertThat(redirectAttributes.getFlashAttributes().get("error").toString())
                .isEqualTo("Không được phép tự đăng ký tài khoản Quản trị viên.");
        verify(userService, never()).createUser(any());
    }

    @Test
    @DisplayName("Normal registration creates regular USER account")
    void testNormalRegistrationCreatesUser() {
        when(userService.isValidEmailFormat("user@example.com")).thenReturn(true);
        when(userService.usernameExists("new_user")).thenReturn(false);
        when(userService.emailExists("user@example.com")).thenReturn(false);

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        String result = authController.handleRegister(
                "new_user",
                "user@example.com",
                "secret123",
                "secret123",
                null,
                redirectAttributes
        );

        assertThat(result).isEqualTo("redirect:/login");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("success");
        verify(userService, times(1)).createUser(any(User.class));
    }
}
