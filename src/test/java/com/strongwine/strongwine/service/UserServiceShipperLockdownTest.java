package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceShipperLockdownTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        org.mockito.Mockito.lenient().when(passwordEncoder.encode(any())).thenReturn("encoded_pass");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Self-creating a SHIPPER account without admin authority should throw SecurityException")
    void testSelfCreatingShipperThrowsSecurityException() {
        User user = new User("bad_actor", "secret123", "SHIPPER");
        user.setEmail("bad@example.com");

        // Anonymous context
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null)
        );

        assertThatThrownBy(() -> userService.createUser(user))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Không được phép tự tạo tài khoản SHIPPER");
    }

    @Test
    @DisplayName("An administrator can successfully create a SHIPPER account")
    void testAdminCanCreateShipper() {
        // Admin context
        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                "admin", "password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        User shipperUser = new User("valid_shipper", "secret123", "SHIPPER");
        shipperUser.setEmail("shipper@example.com");

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = userService.createUser(shipperUser);
        assertThat(created).isNotNull();
        assertThat(created.getRole()).isEqualTo("SHIPPER");
    }

    @Test
    @DisplayName("Regular user registration with role USER succeeds without admin context")
    void testRegularUserRegistrationSucceeds() {
        User regularUser = new User("customer1", "secret123", "USER");
        regularUser.setEmail("customer1@example.com");

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = userService.createUser(regularUser);
        assertThat(created).isNotNull();
        assertThat(created.getRole()).isEqualTo("USER");
    }
}
