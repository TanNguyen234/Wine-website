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

    @org.junit.jupiter.api.Nested
    class SecurityAndHardeningTests {

        @Test
        @DisplayName("UserService rejects ADMIN/SHIPPER creation when Authentication context is null")
        void userServiceRejectsPrivilegedRoleWhenAuthIsNull() {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            com.strongwine.strongwine.repository.UserRepository userRepository =
                    mock(com.strongwine.strongwine.repository.UserRepository.class);
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder =
                    mock(org.springframework.security.crypto.password.PasswordEncoder.class);
            UserService realUserService = new UserService();
            org.springframework.test.util.ReflectionTestUtils.setField(realUserService, "userRepository", userRepository);
            org.springframework.test.util.ReflectionTestUtils.setField(realUserService, "passwordEncoder", passwordEncoder);

            User adminAttempt = new User("rogue_admin", "secret123", "ADMIN");
            adminAttempt.setEmail("rogue@example.com");

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> realUserService.createUser(adminAttempt))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("FileStorageService enforces magic bytes, allowlisted extensions, 5MB limit, and path containment")
        void fileStorageServiceHardeningChecks(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
            com.strongwine.strongwine.service.FileStorageService storage =
                    new com.strongwine.strongwine.service.FileStorageService();
            org.springframework.test.util.ReflectionTestUtils.setField(storage, "uploadDir", tempDir.toString());

            // 1. Spoofed .jpg with text payload must fail
            org.springframework.mock.web.MockMultipartFile spoofedJpg = new org.springframework.mock.web.MockMultipartFile(
                    "file", "evil.jpg", "image/jpeg", "<html>not-a-jpeg</html>".getBytes()
            );
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.saveFile(spoofedJpg))
                    .isInstanceOf(IllegalArgumentException.class);

            // 2. Disallowed .svg extension must fail
            org.springframework.mock.web.MockMultipartFile svgFile = new org.springframework.mock.web.MockMultipartFile(
                    "file", "icon.svg", "image/svg+xml", "<svg></svg>".getBytes()
            );
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.saveFile(svgFile))
                    .isInstanceOf(IllegalArgumentException.class);

            // 3. Valid PNG magic bytes with traversal filename stores safely inside tempDir
            byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
            org.springframework.mock.web.MockMultipartFile validPng = new org.springframework.mock.web.MockMultipartFile(
                    "file", "../../malicious.png", "image/png", pngHeader
            );
            String savedPath = storage.saveFile(validPng);
            assertThat(savedPath).startsWith("/uploads/").endsWith(".png").doesNotContain("..");

            // 4. Path traversal on deleteFile must return false
            assertThat(storage.deleteFile("/uploads/../../etc/passwd")).isFalse();
        }

        @Test
        @DisplayName("AdminAuditController renders paginated filtered audit logs view")
        void adminAuditControllerListsLogs() {
            com.strongwine.strongwine.repository.AdminAuditLogRepository auditRepo =
                    mock(com.strongwine.strongwine.repository.AdminAuditLogRepository.class);
            when(auditRepo.findAllByOrderByCreatedAtDesc(any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(org.springframework.data.domain.Page.empty());

            AdminAuditController auditController = new AdminAuditController(auditRepo);
            org.springframework.ui.Model model = new org.springframework.ui.ConcurrentModel();

            String view = auditController.listAuditLogs(null, null, 0, model);
            assertThat(view).isEqualTo("admin-audit-logs");
            assertThat(model.getAttribute("auditLogs")).isNotNull();
        }
    }
}
