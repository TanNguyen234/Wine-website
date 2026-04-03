package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.PasswordResetToken;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.PasswordResetTokenRepository;
import com.strongwine.strongwine.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "tokenTtlMinutes", 30);
        ReflectionTestUtils.setField(passwordResetService, "configuredBaseUrl", "");
    }

    @Test
    void requestResetForUser_createsTokenForUserRole() {
        User user = new User();
        user.setId(10L);
        user.setEmail("user@example.com");
        user.setRole("USER");
        user.setUsername("user-a");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        passwordResetService.requestResetForUser("user@example.com", "http://localhost:8080");

        verify(passwordResetTokenRepository).deleteByUserId(10L);

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();

        assertThat(savedToken.getUser()).isEqualTo(user);
        assertThat(savedToken.getToken()).isNotBlank();
        assertThat(savedToken.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void requestResetForUser_ignoresNonUserRole() {
        User admin = new User();
        admin.setId(20L);
        admin.setEmail("admin@example.com");
        admin.setRole("ADMIN");

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        passwordResetService.requestResetForUser("admin@example.com", "http://localhost:8080");

        verify(passwordResetTokenRepository, never()).deleteByUserId(20L);
        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
    }

    @Test
    void resetPasswordForUser_updatesPasswordAndMarksTokenUsed() {
        User user = new User();
        user.setId(30L);
        user.setRole("USER");

        PasswordResetToken token = new PasswordResetToken();
        token.setToken("token-123");
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(passwordResetTokenRepository.findByToken("token-123")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-password");

        passwordResetService.resetPasswordForUser("token-123", "new-password");

        assertThat(user.getPassword()).isEqualTo("encoded-password");
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(token);
    }

    @Test
    void resetPasswordForUser_rejectsExpiredToken() {
        User user = new User();
        user.setId(31L);
        user.setRole("USER");

        PasswordResetToken token = new PasswordResetToken();
        token.setToken("expired-token");
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(passwordResetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.resetPasswordForUser("expired-token", "new-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không hợp lệ");

        verify(userRepository, never()).save(any(User.class));
    }
}
