package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.exception.DeliveryAddressNotSupportedException;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserShippingProfileServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    private VietnamGeoValidationService geoValidationService;
    private UserShippingProfileService profileService;

    @BeforeEach
    void setUp() {
        new java.io.File("uploads/profiles/user-888.json").delete();
        new java.io.File("uploads/profiles/user-999.json").delete();
        geoValidationService = new VietnamGeoValidationService();
        profileService = new UserShippingProfileService(orderRepository, userRepository, geoValidationService);
    }

    @Test
    @DisplayName("Should save and retrieve shipping profile for user")
    void testSaveAndGetProfile() {
        Long userId = 999L;
        profileService.saveProfile(userId, "Trần Văn B", "0909123456", "72 Lê Thánh Tôn, Quận 1, TP. Hồ Chí Minh", 10.7769, 106.7009);

        UserShippingProfileService.ShippingProfileData profile = profileService.getProfile(userId);
        assertThat(profile).isNotNull();
        assertThat(profile.fullName()).isEqualTo("Trần Văn B");
        assertThat(profile.phone()).isEqualTo("0909123456");
        assertThat(profile.address()).isEqualTo("72 Lê Thánh Tôn, Quận 1, TP. Hồ Chí Minh");
        assertThat(profile.deliveryLat()).isEqualTo(10.7769);
        assertThat(profile.deliveryLng()).isEqualTo(106.7009);
    }

    @Test
    @DisplayName("Saving profile with remote island address should throw DeliveryAddressNotSupportedException")
    void testSaveProfileWithIslandAddressThrows() {
        Long userId = 999L;
        assertThatThrownBy(() -> profileService.saveProfile(userId, "Khách", "0900000000", "Đặc khu Côn Đảo, Bà Rịa Vũng Tàu", null, null))
                .isInstanceOf(DeliveryAddressNotSupportedException.class);
    }

    @Test
    @DisplayName("Fallback to previous orders when profile has not been explicitly saved")
    void testFallbackToPreviousOrders() {
        Long userId = 888L;
        Order order = new Order();
        order.setShippingFullName("Lê Thị C");
        order.setShippingPhone("0912345678");
        order.setShippingEmail("lethic@example.com");
        order.setShippingAddress("123 Phố Huế, Hai Bà Trưng, Hà Nội");
        order.setShippingLatitude(21.0123);
        order.setShippingLongitude(105.8500);

        org.mockito.Mockito.lenient().when(orderRepository.findByUserIdOrderByOrderDateDesc(userId)).thenReturn(List.of(order));

        UserShippingProfileService.ShippingProfileData profile = profileService.getProfile(userId);
        assertThat(profile.fullName()).isEqualTo("Lê Thị C");
        assertThat(profile.phone()).isEqualTo("0912345678");
        assertThat(profile.address()).isEqualTo("123 Phố Huế, Hai Bà Trưng, Hà Nội");
    }
}
