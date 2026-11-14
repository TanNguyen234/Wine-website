package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.UserAddress;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.UserAddressService;
import com.strongwine.strongwine.service.UserAddressService.AddressInput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/profile/addresses")
public class AddressBookApiController {

    private final UserAddressService userAddressService;
    private final UserRepository userRepository;

    public AddressBookApiController(UserAddressService userAddressService,
                                    UserRepository userRepository) {
        this.userAddressService = userAddressService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAddresses(Authentication authentication) {
        Long userId = requireAuthenticatedUserId(authentication);
        List<Map<String, Object>> payload = userAddressService.getAddressesForUser(userId)
                .stream()
                .map(this::toAddressMap)
                .toList();
        return ResponseEntity.ok(payload);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createAddress(@RequestBody AddressInput input,
                                                             Authentication authentication) {
        Long userId = requireAuthenticatedUserId(authentication);
        UserAddress created = userAddressService.createAddress(userId, input);
        return ResponseEntity.status(HttpStatus.CREATED).body(toAddressMap(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateAddress(@PathVariable Long id,
                                                             @RequestBody AddressInput input,
                                                             Authentication authentication) {
        Long userId = requireAuthenticatedUserId(authentication);
        UserAddress updated = userAddressService.updateAddress(userId, id, input);
        return ResponseEntity.ok(toAddressMap(updated));
    }

    @PostMapping("/{id}/default")
    public ResponseEntity<Map<String, Object>> setDefaultAddress(@PathVariable Long id,
                                                                 Authentication authentication) {
        Long userId = requireAuthenticatedUserId(authentication);
        UserAddress updated = userAddressService.setDefaultAddress(userId, id);
        return ResponseEntity.ok(toAddressMap(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteAddress(@PathVariable Long id,
                                                             Authentication authentication) {
        Long userId = requireAuthenticatedUserId(authentication);
        userAddressService.deleteAddress(userId, id);
        return ResponseEntity.ok(Map.of("deleted", true, "id", id));
    }

    private Long requireAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("Vui lòng đăng nhập để quản lý sổ địa chỉ");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không hợp lệ"));
        return user.getId();
    }

    private Map<String, Object> toAddressMap(UserAddress addr) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", addr.getId());
        map.put("label", addr.getLabel());
        map.put("recipientName", addr.getRecipientName());
        map.put("phone", addr.getPhone());
        map.put("addressLine", addr.getAddressLine());
        map.put("ward", addr.getWard());
        map.put("district", addr.getDistrict());
        map.put("province", addr.getProvince());
        map.put("fullAddress", userAddressService.formatFullAddress(addr));
        map.put("latitude", addr.getLatitude());
        map.put("longitude", addr.getLongitude());
        map.put("isDefault", Boolean.TRUE.equals(addr.getIsDefault()));
        return map;
    }
}
