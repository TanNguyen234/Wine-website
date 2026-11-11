package com.strongwine.strongwine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserShippingProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserShippingProfileService.class);
    private static final String PROFILE_DIR = "uploads/profiles";

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final VietnamGeoValidationService geoValidationService;
    private final ObjectMapper objectMapper;
    private final Map<Long, ShippingProfileData> cache = new ConcurrentHashMap<>();

    public UserShippingProfileService(OrderRepository orderRepository,
                                     UserRepository userRepository,
                                     VietnamGeoValidationService geoValidationService) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.geoValidationService = geoValidationService;
        this.objectMapper = new ObjectMapper();
        ensureStorageDir();
    }

    public ShippingProfileData getProfile(Long userId) {
        if (userId == null) {
            return new ShippingProfileData("", "", "", "", null, null);
        }

        // 1. Check in-memory cache
        ShippingProfileData cached = cache.get(userId);
        if (cached != null) {
            return cached;
        }

        // 2. Check persistent disk file
        ShippingProfileData fromDisk = loadFromDisk(userId);
        if (fromDisk != null) {
            cache.put(userId, fromDisk);
            return fromDisk;
        }

        // 3. Fallback to latest Order
        List<Order> orders = orderRepository.findByUserIdOrderByOrderDateDesc(userId);
        if (!orders.isEmpty()) {
            Order latest = orders.get(0);
            String fullName = latest.getShippingFullName() != null ? latest.getShippingFullName() : "";
            String phone = latest.getShippingPhone() != null ? latest.getShippingPhone() : "";
            String email = latest.getShippingEmail() != null ? latest.getShippingEmail() : "";
            String address = latest.getShippingAddress() != null ? latest.getShippingAddress() : "";
            Double lat = latest.getShippingLatitude();
            Double lng = latest.getShippingLongitude();

            ShippingProfileData derived = new ShippingProfileData(fullName, phone, email, address, lat, lng);
            cache.put(userId, derived);
            saveToDisk(userId, derived);
            return derived;
        }

        // 4. Fallback to User entity email
        String email = userRepository.findById(userId).map(User::getEmail).orElse("");
        ShippingProfileData empty = new ShippingProfileData("", "", email, "", null, null);
        cache.put(userId, empty);
        return empty;
    }

    public ShippingProfileData saveProfile(Long userId, String fullName, String phone, String address, Double lat, Double lng) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        String safeFullName = fullName == null ? "" : fullName.trim();
        String safePhone = phone == null ? "" : phone.trim();
        String safeAddress = address == null ? "" : address.trim();

        Double safeLat = lat;
        Double safeLng = lng;

        // If address is provided, validate strictly for Vietnam mainland (exception on remote islands)
        if (!safeAddress.isEmpty()) {
            geoValidationService.validateMainlandDeliveryAddress(safeAddress, safeLat, safeLng);

            // Auto-resolve fallback coordinates if not provided on map
            if (safeLat == null || safeLng == null) {
                double[] defaultCoords = geoValidationService.resolveDefaultCoordinates(safeAddress);
                safeLat = defaultCoords[0];
                safeLng = defaultCoords[1];
            }
        }

        String email = userRepository.findById(userId).map(User::getEmail).orElse("");
        ShippingProfileData profile = new ShippingProfileData(safeFullName, safePhone, email, safeAddress, safeLat, safeLng);

        cache.put(userId, profile);
        saveToDisk(userId, profile);

        return profile;
    }

    private void ensureStorageDir() {
        try {
            File dir = new File(PROFILE_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        } catch (Exception e) {
            log.warn("Failed to create profile directory {}: {}", PROFILE_DIR, e.getMessage());
        }
    }

    private void saveToDisk(Long userId, ShippingProfileData data) {
        try {
            ensureStorageDir();
            File file = new File(PROFILE_DIR, "user-" + userId + ".json");
            objectMapper.writeValue(file, data);
        } catch (Exception e) {
            log.warn("Could not persist shipping profile for user {}: {}", userId, e.getMessage());
        }
    }

    private ShippingProfileData loadFromDisk(Long userId) {
        try {
            File file = new File(PROFILE_DIR, "user-" + userId + ".json");
            if (file.exists() && file.isFile()) {
                return objectMapper.readValue(file, ShippingProfileData.class);
            }
        } catch (Exception e) {
            log.warn("Could not read shipping profile for user {}: {}", userId, e.getMessage());
        }
        return null;
    }

    public record ShippingProfileData(
            String fullName,
            String phone,
            String email,
            String address,
            Double latitude,
            Double longitude
    ) {
        public Double deliveryLat() {
            return latitude;
        }

        public Double deliveryLng() {
            return longitude;
        }
    }
}
