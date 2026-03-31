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

@Service
@Transactional
public class ShipperService {

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
        User user = getShipperUser(userId);
        if (shipperRepository.existsByUserId(user.getId())) {
            throw new IllegalArgumentException("Selected user already has a shipper profile");
        }

        Shipper shipper = new Shipper();
        shipper.setUser(user);
        shipper.setName(requireText(name, "Shipper name is required"));
        shipper.setPhone(requireText(phone, "Shipper phone is required"));
        shipper.setVehicleType(trimToNull(vehicleType));
        shipper.setStatus(status == null ? ShipperStatus.ACTIVE : status);
        shipper.setIsAvailable(isAvailable != null && isAvailable);
        shipper.setCreatedAt(LocalDateTime.now());
        shipper.setUpdatedAt(LocalDateTime.now());

        Shipper saved = shipperRepository.save(shipper);
        shipmentService.onShipperProfileUpdated(saved.getId());
        return saved;
    }

    public Shipper updateShipper(Long id,
                                 Long userId,
                                 String name,
                                 String phone,
                                 String vehicleType,
                                 ShipperStatus status,
                                 Boolean isAvailable) {
        Shipper shipper = getShipperById(id);
        User user = getShipperUser(userId);

        if (shipperRepository.existsByUserIdAndIdNot(user.getId(), id)) {
            throw new IllegalArgumentException("Selected user already belongs to another shipper profile");
        }

        shipper.setUser(user);
        shipper.setName(requireText(name, "Shipper name is required"));
        shipper.setPhone(requireText(phone, "Shipper phone is required"));
        shipper.setVehicleType(trimToNull(vehicleType));
        shipper.setStatus(status == null ? ShipperStatus.ACTIVE : status);
        shipper.setIsAvailable(isAvailable != null && isAvailable);
        shipper.setUpdatedAt(LocalDateTime.now());

        Shipper saved = shipperRepository.save(shipper);
        shipmentService.onShipperProfileUpdated(saved.getId());
        return saved;
    }

    public void deleteShipper(Long id) {
        Shipper shipper = getShipperById(id);
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
