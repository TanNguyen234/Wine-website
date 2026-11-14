package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.UserAddress;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.repository.UserAddressRepository;
import com.strongwine.strongwine.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserAddressService {

    public static final int MAX_ADDRESSES_PER_USER = 10;

    private final UserAddressRepository userAddressRepository;
    private final UserRepository userRepository;
    private final VietnamGeoValidationService geoValidationService;

    public UserAddressService(UserAddressRepository userAddressRepository,
                              UserRepository userRepository,
                              VietnamGeoValidationService geoValidationService) {
        this.userAddressRepository = userAddressRepository;
        this.userRepository = userRepository;
        this.geoValidationService = geoValidationService;
    }

    public record AddressInput(
            String label,
            String recipientName,
            String phone,
            String addressLine,
            String ward,
            String district,
            String province,
            Double latitude,
            Double longitude,
            Boolean isDefault
    ) {
    }

    @Transactional(readOnly = true)
    public List<UserAddress> getAddressesForUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userAddressRepository.findByUserIdOrderByIsDefaultDescUpdatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Optional<UserAddress> getDefaultAddress(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userAddressRepository.findFirstByUserIdAndIsDefaultTrueOrderByUpdatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public UserAddress getOwnedAddress(Long userId, Long addressId) {
        if (userId == null || addressId == null) {
            throw new IllegalArgumentException("Thông tin địa chỉ không hợp lệ");
        }
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ giao hàng ID: " + addressId));
        if (address.getUser() == null || !userId.equals(address.getUser().getId())) {
            throw new AccessDeniedException("Không có quyền truy cập sổ địa chỉ của người dùng khác");
        }
        return address;
    }

    @Transactional
    public UserAddress createAddress(Long userId, AddressInput input) {
        if (userId == null) {
            throw new IllegalArgumentException("Cần xác thực người dùng để thêm địa chỉ");
        }
        if (input == null) {
            throw new IllegalArgumentException("Dữ liệu địa chỉ không hợp lệ");
        }

        long existingCount = userAddressRepository.countByUserId(userId);
        if (existingCount >= MAX_ADDRESSES_PER_USER) {
            throw new IllegalStateException("Mỗi tài khoản chỉ được lưu tối đa " + MAX_ADDRESSES_PER_USER + " địa chỉ giao hàng");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng ID: " + userId));

        UserAddress address = new UserAddress();
        address.setUser(user);
        applyAndValidateFields(address, input);

        boolean makeDefault = Boolean.TRUE.equals(input.isDefault()) || existingCount == 0;
        if (makeDefault) {
            clearDefaultsForUser(userId, null);
        }
        address.setIsDefault(makeDefault);
        address.setUpdatedAt(LocalDateTime.now());

        return userAddressRepository.save(address);
    }

    @Transactional
    public UserAddress updateAddress(Long userId, Long addressId, AddressInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Dữ liệu địa chỉ không hợp lệ");
        }
        UserAddress existing = getOwnedAddress(userId, addressId);
        applyAndValidateFields(existing, input);

        if (Boolean.TRUE.equals(input.isDefault())) {
            clearDefaultsForUser(userId, existing.getId());
            existing.setIsDefault(true);
        }
        existing.setUpdatedAt(LocalDateTime.now());
        return userAddressRepository.save(existing);
    }

    @Transactional
    public UserAddress setDefaultAddress(Long userId, Long addressId) {
        UserAddress target = getOwnedAddress(userId, addressId);
        clearDefaultsForUser(userId, target.getId());
        target.setIsDefault(true);
        target.setUpdatedAt(LocalDateTime.now());
        return userAddressRepository.save(target);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        UserAddress target = getOwnedAddress(userId, addressId);
        boolean wasDefault = Boolean.TRUE.equals(target.getIsDefault());
        userAddressRepository.delete(target);

        if (wasDefault) {
            List<UserAddress> remaining = userAddressRepository.findByUserIdOrderByIsDefaultDescUpdatedAtDesc(userId);
            if (!remaining.isEmpty()) {
                UserAddress nextDefault = remaining.get(0);
                nextDefault.setIsDefault(true);
                nextDefault.setUpdatedAt(LocalDateTime.now());
                userAddressRepository.save(nextDefault);
            }
        }
    }

    public String formatFullAddress(UserAddress address) {
        if (address == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        addIfNotBlank(parts, address.getAddressLine());
        addIfNotBlank(parts, address.getWard());
        addIfNotBlank(parts, address.getDistrict());
        addIfNotBlank(parts, address.getProvince());
        return String.join(", ", parts);
    }

    private void applyAndValidateFields(UserAddress target, AddressInput input) {
        String recipientName = trimOrEmpty(input.recipientName());
        String phone = trimOrEmpty(input.phone());
        String addressLine = trimOrEmpty(input.addressLine());
        String province = trimOrEmpty(input.province());

        if (recipientName.isEmpty() || phone.isEmpty() || addressLine.isEmpty() || province.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng điền đầy đủ họ tên người nhận, số điện thoại, địa chỉ và tỉnh/thành phố");
        }

        target.setLabel(input.label() != null && !input.label().isBlank() ? input.label().trim() : "Nhà riêng");
        target.setRecipientName(recipientName);
        target.setPhone(phone);
        target.setAddressLine(addressLine);
        target.setWard(trimToNull(input.ward()));
        target.setDistrict(trimToNull(input.district()));
        target.setProvince(province);

        String fullAddress = formatFullAddress(target);
        Double lat = input.latitude();
        Double lng = input.longitude();

        geoValidationService.validateMainlandDeliveryAddress(fullAddress, lat, lng);
        if (lat == null || lng == null) {
            double[] resolved = geoValidationService.resolveDefaultCoordinates(fullAddress);
            if (resolved != null && resolved.length == 2) {
                lat = resolved[0];
                lng = resolved[1];
            }
        }
        target.setLatitude(lat);
        target.setLongitude(lng);
    }

    private void clearDefaultsForUser(Long userId, Long keepAddressId) {
        List<UserAddress> existing = userAddressRepository.findByUserIdOrderByIsDefaultDescUpdatedAtDesc(userId);
        for (UserAddress addr : existing) {
            if (Boolean.TRUE.equals(addr.getIsDefault())
                    && (keepAddressId == null || !keepAddressId.equals(addr.getId()))) {
                addr.setIsDefault(false);
                addr.setUpdatedAt(LocalDateTime.now());
                userAddressRepository.save(addr);
            }
        }
    }

    private void addIfNotBlank(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }

    private String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
