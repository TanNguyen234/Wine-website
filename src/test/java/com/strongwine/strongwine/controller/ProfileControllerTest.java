package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.exception.DeliveryAddressNotSupportedException;
import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.UserService;
import com.strongwine.strongwine.service.UserShippingProfileService;
import com.strongwine.strongwine.service.UserShippingProfileService.ShippingProfileData;
import com.strongwine.strongwine.service.VietnamGeoValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private OrderService orderService;

    @Mock
    private UserShippingProfileService userShippingProfileService;

    @Mock
    private VietnamGeoValidationService geoValidationService;

    @InjectMocks
    private ProfileController profileController;

    private User currentUser;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        currentUser = new User("test_user", "password123", "USER");
        currentUser.setId(100L);
        currentUser.setEmail("test@example.com");

        org.springframework.security.core.userdetails.UserDetails userDetails =
                org.springframework.security.core.userdetails.User.withUsername("test_user")
                        .password("password123")
                        .authorities("ROLE_USER")
                        .build();
        auth = new UsernamePasswordAuthenticationToken(userDetails, "password123", userDetails.getAuthorities());
        when(userService.getUserByUsername("test_user")).thenReturn(Optional.of(currentUser));
    }

    @Test
    @DisplayName("showProfile should populate shipping fields from UserShippingProfileService")
    void testShowProfilePrefillsShippingInfo() {
        ShippingProfileData profileData = new ShippingProfileData(
                "Nguyễn Văn A",
                "0901234567",
                "test@example.com",
                "72 Lê Thánh Tôn, Bến Nghé, Quận 1, TP. Hồ Chí Minh",
                10.7769,
                106.7009
        );
        when(userShippingProfileService.getProfile(100L)).thenReturn(profileData);
        when(orderService.getOrdersByUserId(100L)).thenReturn(Collections.emptyList());

        Model model = new ConcurrentModel();
        String view = profileController.showProfile(auth, model);

        assertThat(view).isEqualTo("user-profile");
        assertThat(model.getAttribute("latestFullName")).isEqualTo("Nguyễn Văn A");
        assertThat(model.getAttribute("latestPhone")).isEqualTo("0901234567");
        assertThat(model.getAttribute("latestAddress")).isEqualTo("72 Lê Thánh Tôn, Bến Nghé, Quận 1, TP. Hồ Chí Minh");
    }

    @Test
    @DisplayName("updateShippingAddress should reject remote islands and flash error")
    void testUpdateShippingAddressRejectsIsland() {
        String islandAddress = "Huyện đảo Côn Đảo, Bà Rịa - Vũng Tàu";
        doThrow(new DeliveryAddressNotSupportedException("Không hỗ trợ đảo xa"))
                .when(geoValidationService).validateMainlandDeliveryAddress(eq(islandAddress), any(), any());

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        String redirect = profileController.updateShippingAddress(auth, "Nguyễn Văn A", "0901234567", islandAddress, redirectAttributes);

        assertThat(redirect).isEqualTo("redirect:/profile");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("errorMessage");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage").toString()).contains("Không hỗ trợ đảo xa");
    }

    @Test
    @DisplayName("updateShippingAddress should save valid mainland address")
    void testUpdateShippingAddressSavesValid() {
        String mainlandAddress = "15 Tràng Tiền, Hoàn Kiếm, Hà Nội";
        doNothing().when(geoValidationService).validateMainlandDeliveryAddress(eq(mainlandAddress), any(), any());

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        String redirect = profileController.updateShippingAddress(auth, "Nguyễn Văn A", "0901234567", mainlandAddress, redirectAttributes);

        assertThat(redirect).isEqualTo("redirect:/profile");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("successMessage");
        verify(userShippingProfileService, times(1)).saveProfile(eq(100L), eq("Nguyễn Văn A"), eq("0901234567"), eq(mainlandAddress), isNull(), isNull());
    }
}
