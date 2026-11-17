package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Shipper;
import com.strongwine.strongwine.entity.ShipperStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.ShipperRepository;
import com.strongwine.strongwine.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class ShipperService {

    private static final int DEFAULT_MAX_CONCURRENT_SHIPMENTS = 5;

    private final ShipperRepository shipperRepository;
    private final UserRepository userRepository;
    private final ShipmentService shipmentService;

    public ShipperService(ShipperRepository shipperRepository,
                          UserRepository userRepository,
                          ShipmentService shipmentService) {
        this.shipperRepository = shipperRepository;
        this.userRepository = userRepository;
        this.shipmentService = shipmentService;
    }

    @Transactional(readOnly = true)
    public List<Shipper> getAllShippers() {
        return shipperRepository.findAllByOrderByCreatedAtDesc();
    }
    
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Shipper> getShippersPage(org.springframework.data.domain.Pageable pageable) {
        return shipperRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Shipper> getAllShippersForSelection() {
        return shipperRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Shipper> getActiveShippers() {
        return shipperRepository.findByStatusOrderByNameAsc(ShipperStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Shipper> getAvailableActiveShippers() {
        return shipperRepository.findByStatusAndIsAvailableTrueOrderByNameAsc(ShipperStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getShipperOverviewStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", shipperRepository.count());
        stats.put("active", shipperRepository.countByStatus(ShipperStatus.ACTIVE));
        stats.put("available", shipperRepository.countByIsAvailableTrue());
        return stats;
    }

    @Transactional(readOnly = true)
    public Shipper getShipperById(Long id) {
        return shipperRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shipper not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<Shipper> findShipperByUsername(String username) {
        String normalized = trimToNull(username);
        if (normalized == null) {
            return Optional.empty();
        }
        return userRepository.findByUsername(normalized)
                .map(User::getId)
                .flatMap(shipperRepository::findByUserId);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> getActiveLoadByShipperId(List<Shipper> shippers) {
        return shipmentService.getActiveLoadByShipperId(shippers);
    }

    @Transactional(readOnly = true)
    public List<Shipper> getAvailableActiveShippersSortedByDistance(Double targetLat, Double targetLng) {
        return shipmentService.sortShippersForDispatch(
                shipperRepository.findByStatusAndIsAvailableTrueOrderByNameAsc(ShipperStatus.ACTIVE),
                targetLat,
                targetLng
        );
    }

    public Shipper updateShipperLocation(String username, Double latitude, Double longitude) {
        Shipper shipper = findShipperByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Shipper profile not found for user: " + username));
        if (latitude != null && Double.isFinite(latitude) && longitude != null && Double.isFinite(longitude)) {
            shipper.setCurrentLatitude(latitude);
            shipper.setCurrentLongitude(longitude);
            shipper.setUpdatedAt(LocalDateTime.now());
            return shipperRepository.save(shipper);
        }
        return shipper;
    }

    @Transactional(readOnly = true)
    public List<User> getAvailableUsersForShipper() {
        return userRepository.findByRole("SHIPPER").stream()
                .filter(u -> !shipperRepository.existsByUserId(u.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<User> getAvailableUsersForShipper(Long shipperId) {
        Long currentUserId = shipperId == null ? null : getShipperById(shipperId).getUser().getId();
        return userRepository.findByRole("SHIPPER").stream()
                .filter(u -> u.getId().equals(currentUserId) || !shipperRepository.existsByUserId(u.getId()))
                .toList();
    }

    public Shipper createShipper(Long userId,
                                 String name,
                                 String phone,
                                 String vehicleType,
                                 ShipperStatus status,
                                 Boolean isAvailable) {
        return createShipper(userId, name, phone, vehicleType, status, isAvailable, DEFAULT_MAX_CONCURRENT_SHIPMENTS);
    }

    public Shipper createShipper(Long userId,
                                 String name,
                                 String phone,
                                 String vehicleType,
                                 ShipperStatus status,
                                 Boolean isAvailable,
                                 Integer maxConcurrentShipments) {
        User user = getShipperUser(userId);
        if (shipperRepository.existsByUserId(user.getId())) {
            throw new IllegalArgumentException("Selected user already has a shipper profile");
        }
        int resolvedCapacity = resolveCapacityForCreate(maxConcurrentShipments);
        ShipperStatus resolvedStatus = status == null ? ShipperStatus.ACTIVE : status;
        boolean effectiveAvailable = Boolean.TRUE.equals(isAvailable) && resolvedStatus == ShipperStatus.ACTIVE;

        Shipper shipper = new Shipper();
        shipper.setUser(user);
        shipper.setName(requireText(name, "Shipper name is required"));
        shipper.setPhone(requireText(phone, "Shipper phone is required"));
        shipper.setVehicleType(trimToNull(vehicleType));
        shipper.setStatus(resolvedStatus);
        shipper.setMaxConcurrentShipments(resolvedCapacity);
        shipper.setIsAvailable(effectiveAvailable);
        shipper.setCreatedAt(LocalDateTime.now());
        shipper.setUpdatedAt(LocalDateTime.now());

        Shipper saved = shipperRepository.save(shipper);
        shipmentService.onShipperProfileUpdated(saved.getId(), effectiveAvailable);
        return saved;
    }

    public Shipper updateShipper(Long id,
                                 Long userId,
                                 String name,
                                 String phone,
                                 String vehicleType,
                                 ShipperStatus status,
                                 Boolean isAvailable) {
        return updateShipper(id, userId, name, phone, vehicleType, status, isAvailable, null);
    }

    public Shipper updateShipper(Long id,
                                 Long userId,
                                 String name,
                                 String phone,
                                 String vehicleType,
                                 ShipperStatus status,
                                 Boolean isAvailable,
                                 Integer maxConcurrentShipments) {
        Shipper shipper = getShipperById(id);
        User user = getShipperUser(userId);

        if (shipperRepository.existsByUserIdAndIdNot(user.getId(), id)) {
            throw new IllegalArgumentException("Selected user already belongs to another shipper profile");
        }

        int existingCap = (shipper.getMaxConcurrentShipments() != null && shipper.getMaxConcurrentShipments() >= 1)
                ? shipper.getMaxConcurrentShipments()
                : DEFAULT_MAX_CONCURRENT_SHIPMENTS;
        int resolvedCapacity;
        if (maxConcurrentShipments != null) {
            if (maxConcurrentShipments < 1) {
                throw new IllegalArgumentException("Số đơn giao đồng thời tối đa phải từ 1 trở lên");
            }
            resolvedCapacity = maxConcurrentShipments;
        } else {
            resolvedCapacity = Math.max(DEFAULT_MAX_CONCURRENT_SHIPMENTS, existingCap);
        }

        ShipperStatus resolvedStatus = status == null ? ShipperStatus.ACTIVE : status;
        boolean requestedAvailable = Boolean.TRUE.equals(isAvailable) && resolvedStatus == ShipperStatus.ACTIVE;

        if (requestedAvailable) {
            int currentActive = shipper.getActiveShipmentCount() != null ? Math.max(0, shipper.getActiveShipmentCount()) : 0;
            Map<Long, Long> liveLoad = shipmentService.getActiveLoadByShipperId(List.of(shipper));
            if (liveLoad != null && liveLoad.containsKey(id) && liveLoad.get(id) != null) {
                currentActive = liveLoad.get(id).intValue();
            }
            if (currentActive >= resolvedCapacity && resolvedCapacity < DEFAULT_MAX_CONCURRENT_SHIPMENTS) {
                resolvedCapacity = Math.max(DEFAULT_MAX_CONCURRENT_SHIPMENTS, currentActive + 1);
            }
            if (currentActive >= resolvedCapacity) {
                throw new IllegalStateException("Shipper đang phụ trách " + currentActive + "/" + resolvedCapacity
                        + " vận đơn tối đa. Vui lòng tăng hạn mức đơn giao đồng thời trước khi bật sẵn sàng.");
            }
        }

        shipper.setUser(user);
        shipper.setName(requireText(name, "Shipper name is required"));
        shipper.setPhone(requireText(phone, "Shipper phone is required"));
        shipper.setVehicleType(trimToNull(vehicleType));
        shipper.setStatus(resolvedStatus);
        shipper.setMaxConcurrentShipments(resolvedCapacity);
        shipper.setIsAvailable(requestedAvailable);
        shipper.setUpdatedAt(LocalDateTime.now());

        Shipper saved = shipperRepository.save(shipper);
        shipmentService.onShipperProfileUpdated(saved.getId(), requestedAvailable);
        return saved;
    }

    public void updateAvailability(Long id, boolean available) {
        if (available) {
            Shipper shipper = getShipperById(id);
            if (shipper.getMaxConcurrentShipments() == null || shipper.getMaxConcurrentShipments() < DEFAULT_MAX_CONCURRENT_SHIPMENTS) {
                shipper.setMaxConcurrentShipments(DEFAULT_MAX_CONCURRENT_SHIPMENTS);
                shipper.setUpdatedAt(LocalDateTime.now());
                shipperRepository.save(shipper);
            }
        }
        shipmentService.setShipperAvailability(id, available);
    }

    private int resolveCapacityForCreate(Integer maxConcurrentShipments) {
        if (maxConcurrentShipments == null) {
            return DEFAULT_MAX_CONCURRENT_SHIPMENTS;
        }
        if (maxConcurrentShipments < 1) {
            throw new IllegalArgumentException("Số đơn giao đồng thời tối đa phải từ 1 trở lên");
        }
        return maxConcurrentShipments;
    }

    public void deleteShipper(Long id) {
        Shipper shipper = getShipperById(id);
        if (shipmentService.hasActiveShipmentsForShipper(id)) {
            throw new IllegalStateException("Không thể xóa shipper đang phụ trách vận đơn chưa hoàn tất");
        }
        shipperRepository.delete(shipper);
    }

    private User getShipperUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User is required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        if (!"SHIPPER".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("Selected user must have SHIPPER role");
        }
        return user;
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
