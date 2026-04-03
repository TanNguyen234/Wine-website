package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.PasswordResetToken;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.PasswordResetTokenRepository;
import com.strongwine.strongwine.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@Transactional
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@strongwine.local}")
    private String fromAddress;

    @Value("${app.password-reset.token-ttl-minutes:30}")
    private int tokenTtlMinutes;

    @Value("${app.password-reset.base-url:}")
    private String configuredBaseUrl;

    public PasswordResetService(PasswordResetTokenRepository passwordResetTokenRepository,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                @Autowired(required = false) JavaMailSender mailSender) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
    }

    public void requestResetForUser(String rawEmail, String requestBaseUrl) {
        passwordResetTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        String normalizedEmail = normalizeEmail(rawEmail);
        if (normalizedEmail == null) {
            return;
        }

        Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);
        if (userOptional.isEmpty()) {
            return;
        }

        User user = userOptional.get();
        if (!"USER".equalsIgnoreCase(user.getRole())) {
            return;
        }

        passwordResetTokenRepository.deleteByUserId(user.getId());

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setToken(generateToken());
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(Math.max(5, tokenTtlMinutes)));
        PasswordResetToken savedToken = passwordResetTokenRepository.save(resetToken);

        try {
            sendResetEmail(user, savedToken, resolveBaseUrl(requestBaseUrl));
        } catch (Exception ex) {
            log.warn("Failed to send password reset email for user {}: {}", user.getUsername(), ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public boolean isResetTokenUsableForUser(String token) {
        PasswordResetToken resetToken = findByToken(token);
        if (resetToken == null) {
            return false;
        }
        if (resetToken.isUsed() || resetToken.isExpired(LocalDateTime.now())) {
            return false;
        }
        if (resetToken.getUser() == null) {
            return false;
        }
        return "USER".equalsIgnoreCase(resetToken.getUser().getRole());
    }

    public void resetPasswordForUser(String token, String newPassword) {
        PasswordResetToken resetToken = findByToken(token);
        if (resetToken == null) {
            throw new IllegalArgumentException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }

        if (resetToken.isUsed() || resetToken.isExpired(LocalDateTime.now())) {
            throw new IllegalArgumentException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }

        User user = resetToken.getUser();
        if (user == null || !"USER".equalsIgnoreCase(user.getRole())) {
            throw new IllegalStateException("Tài khoản này không hỗ trợ đặt lại mật khẩu theo luồng người dùng");
        }

        String normalizedPassword = normalizeNewPassword(newPassword);
        user.setPassword(passwordEncoder.encode(normalizedPassword));
        userRepository.save(user);

        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);
    }

    private PasswordResetToken findByToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        return passwordResetTokenRepository.findByToken(token.trim()).orElse(null);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        String normalized = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }

    private String normalizeNewPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Mật khẩu mới không được để trống");
        }
        String normalized = password.trim();
        if (normalized.length() < 6) {
            throw new IllegalArgumentException("Mật khẩu mới phải có ít nhất 6 ký tự");
        }
        return normalized;
    }

    private String generateToken() {
        byte[] bytes = new byte[48];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void sendResetEmail(User user, PasswordResetToken token, String baseUrl) {
        if (!mailEnabled) {
            throw new IllegalStateException("Tính năng gửi email đang tắt");
        }
        if (mailSender == null) {
            throw new IllegalStateException("JavaMailSender chưa sẵn sàng");
        }

        String resetLink = baseUrl + "/reset-password?token=" + token.getToken();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(user.getEmail());
        message.setSubject("StrongWine - Dat lai mat khau");
        message.setText(buildMailContent(user.getUsername(), resetLink, token.getExpiresAt()));
        mailSender.send(message);
    }

    private String buildMailContent(String username, String resetLink, LocalDateTime expiresAt) {
        return "Xin chao " + username + ",\n\n"
                + "Ban vua yeu cau dat lai mat khau cho tai khoan StrongWine.\n"
                + "Vui long mo lien ket sau de dat lai mat khau:\n"
                + resetLink + "\n\n"
                + "Lien ket nay het han vao: " + expiresAt + "\n"
                + "Neu ban khong thuc hien yeu cau nay, hay bo qua email nay.\n\n"
                + "StrongWine";
    }

    private String resolveBaseUrl(String requestBaseUrl) {
        if (configuredBaseUrl != null && !configuredBaseUrl.trim().isEmpty()) {
            return stripTrailingSlash(configuredBaseUrl.trim());
        }
        if (requestBaseUrl != null && !requestBaseUrl.trim().isEmpty()) {
            return stripTrailingSlash(requestBaseUrl.trim());
        }
        return "http://localhost:8080";
    }

    private String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
