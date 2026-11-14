package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.UserAddress;
import com.strongwine.strongwine.exception.DeliveryAddressNotSupportedException;
import com.strongwine.strongwine.repository.UserAddressRepository;
import com.strongwine.strongwine.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAddressServiceTest {

    @Mock
    private UserAddressRepository userAddressRepository;

    @Mock
    private UserRepository userRepository;

    private VietnamGeoValidationService geoValidationService;
    private UserAddressService userAddressService;
    private User owner;

    @BeforeEach
    void setUp() {
        geoValidationService = new VietnamGeoValidationService();
        userAddressService = new UserAddressService(userAddressRepository, userRepository, geoValidationService);
        owner = new User("buyer", "pass", "USER");
        owner.setId(10L);
        lenient().when(userRepository.findById(10L)).thenReturn(Optional.of(owner));
    }

    @Test
    @DisplayName("First created address automatically becomes default and resolves mainland coordinates")
    void createAddress_firstAddressBecomesDefault() {
        when(userAddressRepository.countByUserId(10L)).thenReturn(0L);
        when(userAddressRepository.findByUserIdOrderByIsDefaultDescUpdatedAtDesc(10L)).thenReturn(List.of());
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAddressService.AddressInput input = new UserAddressService.AddressInput(
                "Nhà riêng",
                "Nguyễn Văn A",
                "0901234567",
                "72 Lê Thánh Tôn",
                "Bến Nghé",
                "Quận 1",
                "TP. Hồ Chí Minh",
                null,
                null,
                false
        );

        UserAddress saved = userAddressService.createAddress(10L, input);

        assertThat(saved.getIsDefault()).isTrue();
        assertThat(saved.getLatitude()).isNotNull();
        assertThat(saved.getLongitude()).isNotNull();
        assertThat(saved.getUser()).isSameAs(owner);
    }

    @Test
    @DisplayName("Creating more than 10 addresses per user is rejected")
    void createAddress_enforcesMaxTenAddressesPerUser() {
        when(userAddressRepository.countByUserId(10L)).thenReturn(10L);

        UserAddressService.AddressInput input = new UserAddressService.AddressInput(
                "Văn phòng", "Nguyễn Văn A", "0901234567",
                "15 Tràng Tiền", "Tràng Tiền", "Hoàn Kiếm", "Hà Nội",
                21.0245, 105.8533, false
        );

        assertThatThrownBy(() -> userAddressService.createAddress(10L, input))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("10");

        verify(userAddressRepository, never()).save(any());
    }

    @Test
    @DisplayName("Remote island address is rejected on address creation")
    void createAddress_rejectsRemoteIslandAddress() {
        when(userAddressRepository.countByUserId(10L)).thenReturn(0L);

        UserAddressService.AddressInput input = new UserAddressService.AddressInput(
                "Đảo", "Nguyễn Văn A", "0901234567",
                "Khu dân cư số 1", null, "Huyện Côn Đảo", "Bà Rịa - Vũng Tàu",
                null, null, false
        );

        assertThatThrownBy(() -> userAddressService.createAddress(10L, input))
                .isInstanceOf(DeliveryAddressNotSupportedException.class);

        verify(userAddressRepository, never()).save(any());
    }

    @Test
    @DisplayName("setDefaultAddress atomically clears previous default so at most one default remains")
    void setDefaultAddress_clearsPreviousDefaultAtomically() {
        UserAddress addr1 = new UserAddress();
        addr1.setId(1L);
        addr1.setUser(owner);
        addr1.setIsDefault(true);

        UserAddress addr2 = new UserAddress();
        addr2.setId(2L);
        addr2.setUser(owner);
        addr2.setIsDefault(false);

        when(userAddressRepository.findById(2L)).thenReturn(Optional.of(addr2));
        when(userAddressRepository.findByUserIdOrderByIsDefaultDescUpdatedAtDesc(10L)).thenReturn(List.of(addr1, addr2));
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAddress updated = userAddressService.setDefaultAddress(10L, 2L);

        assertThat(addr1.getIsDefault()).isFalse();
        assertThat(updated.getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("Accessing or deleting another user's address throws AccessDeniedException")
    void getOwnedAddress_rejectsDifferentOwner() {
        User stranger = new User("stranger", "pass", "USER");
        stranger.setId(99L);

        UserAddress foreign = new UserAddress();
        foreign.setId(55L);
        foreign.setUser(stranger);

        when(userAddressRepository.findById(55L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> userAddressService.getOwnedAddress(10L, 55L))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> userAddressService.deleteAddress(10L, 55L))
                .isInstanceOf(AccessDeniedException.class);

        verify(userAddressRepository, never()).delete(any());
    }
}
