package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.entity.Shipper;
import com.strongwine.strongwine.entity.ShipperStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.ShipmentRepository;
import com.strongwine.strongwine.repository.ShipperRepository;
import com.strongwine.strongwine.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);
    private static final SecureRandom OTP_RANDOM = new SecureRandom();
    private static final int AUTO_DISPATCH_BATCH_LIMIT = 200;
    private static final List<ShipmentStatus> IN_PROGRESS_STATUSES = List.of(
            ShipmentStatus.ASSIGNED,
            ShipmentStatus.PICKED_UP,
            ShipmentStatus.DELIVERING
    );

    private final ShipmentRepository shipmentRepository;
    private final ShipperRepository shipperRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ShipmentOtpEmailService shipmentOtpEmailService;

    public ShipmentService(ShipmentRepository shipmentRepository,
                           ShipperRepository shipperRepository,
                           OrderRepository orderRepository,
                           UserRepository userRepository,
                           ShipmentOtpEmailService shipmentOtpEmailService) {
        this.shipmentRepository = shipmentRepository;
        this.shipperRepository = shipperRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.shipmentOtpEmailService = shipmentOtpEmailService;
    }

    public Shipment createAutoShipmentForPaidOrder(Order order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Order is required for auto shipment creation");
        }
        ensureOrderReadyForShipment(order);

        Optional<Shipment> existingShipment = shipmentRepository.findByOrderId(order.getId());
        if (existingShipment.isPresent()) {
            return existingShipment.get();
        }

        Optional<Shipper> availableShipper = shipperRepository.findFirstByStatusAndIsAvailableTrueOrderByIdAsc(ShipperStatus.ACTIVE);
        if (availableShipper.isEmpty()) {
            // Keep order in paid state without shipment and let dispatcher create it when a shipper is free.
            return null;
        }

        Shipment savedShipment = createShipmentForOrderWithAssignedShipper(order, availableShipper.get(), true);
        try {
            shipmentOtpEmailService.sendShipmentOtp(savedShipment);
        } catch (Exception ex) {
            log.warn("Auto shipment OTP email failed for shipment {}: {}", savedShipment.getId(), ex.getMessage());
        }
        return savedShipment;
    }

    public void dispatchAutoShipmentQueue() {
        int processed = 0;
        while (processed < AUTO_DISPATCH_BATCH_LIMIT) {
            Optional<Shipper> availableShipper = shipperRepository.findFirstByStatusAndIsAvailableTrueOrderByIdAsc(ShipperStatus.ACTIVE);
            if (availableShipper.isEmpty()) {
                break;
            }

            Optional<Shipment> pendingShipment = shipmentRepository.findFirstByStatusOrderByCreatedAtAsc(ShipmentStatus.PENDING_ASSIGNMENT);
            if (pendingShipment.isPresent()) {
                assignShipmentToShipperInternal(pendingShipment.get(), availableShipper.get(), false);
                processed += 1;
                continue;
            }

            Optional<Order> oldestPaidOrder = findOldestPaidOrderWithoutShipment();
            if (oldestPaidOrder.isEmpty()) {
                break;
            }

            Shipment createdShipment = createShipmentForOrderWithAssignedShipper(oldestPaidOrder.get(), availableShipper.get(), false);
            try {
                shipmentOtpEmailService.sendShipmentOtp(createdShipment);
            } catch (Exception ex) {
                log.warn("Auto shipment OTP email failed for shipment {}: {}", createdShipment.getId(), ex.getMessage());
            }
            processed += 1;
        }
    }

    public void onShipperProfileUpdated(Long shipperId) {
        refreshShipperAvailability(shipperId);
        dispatchAutoShipmentQueue();
    }

    @Transactional(readOnly = true)
    public List<Shipment> getMyShipments(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            return shipmentRepository.findAllForAdminOrderByCreatedAtDesc();
        }

        Shipper shipper = shipperRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Shipper profile not found for user: " + username));
        return shipmentRepository.findByShipperIdOrderByCreatedAtDesc(shipper.getId());
    }

    @Transactional(readOnly = true)
    public List<Shipment> getShipmentsForAdmin(Long orderId,
                                               Long shipperId,
                                               ShipmentStatus status,
                                               String keyword) {
        String normalizedKeyword = normalize(keyword);
        return shipmentRepository.findAllForAdminOrderByCreatedAtDesc().stream()
                .filter(s -> orderId == null || (s.getOrder() != null && orderId.equals(s.getOrder().getId())))
                .filter(s -> shipperId == null || (s.getShipper() != null && shipperId.equals(s.getShipper().getId())))
                .filter(s -> status == null || status == s.getStatus())
                .filter(s -> matchesKeyword(s, normalizedKeyword))
                .toList();
    }

    @Transactional(readOnly = true)
    public Shipment getShipmentByIdForAdmin(Long shipmentId) {
        if (shipmentId == null) {
            throw new IllegalArgumentException("Shipment id is required");
        }
        return shipmentRepository.findByIdForAdmin(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + shipmentId));
    }

    @Transactional(readOnly = true)
    public List<Order> getEligibleOrdersForShipment() {
        return orderRepository.findOrdersWithoutShipmentByStatus(OrderStatus.PAID);
    }

    @Transactional(readOnly = true)
    public Map<ShipmentStatus, Long> getShipmentStatusStats() {
        Map<ShipmentStatus, Long> stats = new EnumMap<>(ShipmentStatus.class);
        for (ShipmentStatus shipmentStatus : ShipmentStatus.values()) {
            stats.put(shipmentStatus, shipmentRepository.countByStatus(shipmentStatus));
        }
        return stats;
    }

    public Shipment createShipment(Long orderId) {
        return createShipmentForAdmin(orderId, null, null, null, null);
    }

    public Shipment createShipmentForAdmin(Long orderId,
                                           Long shipperId,
                                           String shippingName,
                                           String shippingPhone,
                                           String shippingAddress) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order id is required");
        }
        if (shipmentRepository.existsByOrderId(orderId)) {
            throw new IllegalArgumentException("Order already has a shipment: " + orderId);
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        ensureOrderReadyForShipment(order);

        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setShippingName(requireTextOrFallback(shippingName, order.getShippingFullName(), "Recipient name is required"));
        shipment.setShippingPhone(requireTextOrFallback(shippingPhone, order.getShippingPhone(), "Recipient phone is required"));
        shipment.setShippingAddress(requireTextOrFallback(shippingAddress, order.getShippingAddress(), "Recipient address is required"));
        shipment.setOtpCode(generateOtp());
        shipment.setOtpVerified(false);

        if (shipperId != null) {
            shipment.setShipper(getActiveShipper(shipperId));
            shipment.setStatus(ShipmentStatus.ASSIGNED);
        } else {
            shipment.setStatus(ShipmentStatus.PENDING_ASSIGNMENT);
        }

        Shipment savedShipment = shipmentRepository.save(shipment);
        if (savedShipment.getShipper() != null) {
            markShipperBusy(savedShipment.getShipper());
        } else {
            dispatchAutoShipmentQueue();
        }
        return savedShipment;
    }

    public Shipment updateShipmentForAdmin(Long shipmentId,
                                           Long shipperId,
                                           String shippingName,
                                           String shippingPhone,
                                           String shippingAddress,
                                           ShipmentStatus targetStatus,
                                           String failureNote) {
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + shipmentId));
        Long previousShipperId = shipment.getShipper() == null ? null : shipment.getShipper().getId();
        ShipmentStatus previousStatus = shipment.getStatus();

        shipment.setShippingName(requireText(shippingName, "Recipient name is required"));
        shipment.setShippingPhone(requireText(shippingPhone, "Recipient phone is required"));
        shipment.setShippingAddress(requireText(shippingAddress, "Recipient address is required"));

        if (shipperId != null) {
            shipment.setShipper(getActiveShipper(shipperId));
            if (shipment.getStatus() == ShipmentStatus.PENDING_ASSIGNMENT) {
                shipment.setStatus(ShipmentStatus.ASSIGNED);
            }
        }

        if (targetStatus != null && targetStatus != shipment.getStatus()) {
            applyStrictTransition(shipment, targetStatus, failureNote);
        } else if (targetStatus == ShipmentStatus.FAILED && shipment.getStatus() == ShipmentStatus.FAILED) {
            shipment.setFailureNote(trimToNull(failureNote));
        }

        shipment.setUpdatedAt(LocalDateTime.now());
        Shipment savedShipment = shipmentRepository.save(shipment);
        reconcileShipperAvailabilityAfterMutation(savedShipment, previousStatus, previousShipperId, true);
        return savedShipment;
    }

    public Shipment assignShipperByAdmin(Long shipmentId, Long shipperId) {
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + shipmentId));
        if (shipment.getStatus() != ShipmentStatus.PENDING_ASSIGNMENT && shipment.getStatus() != ShipmentStatus.ASSIGNED) {
            throw new IllegalStateException("Only pending/assigned shipments can be assigned or reassigned");
        }

        Long previousShipperId = shipment.getShipper() == null ? null : shipment.getShipper().getId();
        ShipmentStatus previousStatus = shipment.getStatus();
        shipment.setShipper(getActiveShipper(shipperId));
        shipment.setStatus(ShipmentStatus.ASSIGNED);
        shipment.setUpdatedAt(LocalDateTime.now());
        Shipment savedShipment = shipmentRepository.save(shipment);
        reconcileShipperAvailabilityAfterMutation(savedShipment, previousStatus, previousShipperId, true);
        return savedShipment;
    }

    public Shipment transitionShipmentStatusByAdmin(Long shipmentId, ShipmentStatus targetStatus, String failureNote) {
        if (targetStatus == null) {
            throw new IllegalArgumentException("Target status is required");
        }

        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + shipmentId));
        if (shipment.getStatus() == targetStatus) {
            return shipment;
        }

        Long previousShipperId = shipment.getShipper() == null ? null : shipment.getShipper().getId();
        ShipmentStatus previousStatus = shipment.getStatus();
        applyStrictTransition(shipment, targetStatus, failureNote);
        shipment.setUpdatedAt(LocalDateTime.now());
        Shipment savedShipment = shipmentRepository.save(shipment);
        reconcileShipperAvailabilityAfterMutation(savedShipment, previousStatus, previousShipperId, true);
        return savedShipment;
    }

    public Shipment regenerateOtpForShipment(Long shipmentId) {
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + shipmentId));
        if (shipment.getStatus() == ShipmentStatus.COMPLETED) {
            throw new IllegalStateException("Completed shipment does not need OTP regeneration");
        }

        shipment.setOtpCode(generateOtp());
        shipment.setOtpVerified(false);
        shipment.setUpdatedAt(LocalDateTime.now());
        return shipmentRepository.save(shipment);
    }

    public void deletePendingShipment(Long shipmentId) {
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + shipmentId));
        if (shipment.getStatus() != ShipmentStatus.PENDING_ASSIGNMENT) {
            throw new IllegalStateException("Only pending shipment can be deleted");
        }
        shipmentRepository.delete(shipment);
    }

    public void assignShipperRandom() {
        Shipment shipment = shipmentRepository.findFirstByStatusOrderByCreatedAtAsc(ShipmentStatus.PENDING_ASSIGNMENT)
                .orElseThrow(() -> new IllegalStateException("No pending shipment to assign"));

        Shipper shipper = shipperRepository.findFirstByStatusAndIsAvailableTrueOrderByIdAsc(ShipperStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active available shipper"));

        assignShipmentToShipperInternal(shipment, shipper, true);
    }

    public void markPickedUp(Long shipmentId, String username) {
        Shipment shipment = getShipmentForShipper(shipmentId, username);
        Long previousShipperId = shipment.getShipper() == null ? null : shipment.getShipper().getId();
        ShipmentStatus previousStatus = shipment.getStatus();
        applyStrictTransition(shipment, ShipmentStatus.PICKED_UP, null);
        Shipment savedShipment = shipmentRepository.save(shipment);
        reconcileShipperAvailabilityAfterMutation(savedShipment, previousStatus, previousShipperId, true);
    }

    public void startDelivering(Long shipmentId, String username) {
        Shipment shipment = getShipmentForShipper(shipmentId, username);
        Long previousShipperId = shipment.getShipper() == null ? null : shipment.getShipper().getId();
        ShipmentStatus previousStatus = shipment.getStatus();
        applyStrictTransition(shipment, ShipmentStatus.DELIVERING, null);
        Shipment savedShipment = shipmentRepository.save(shipment);
        reconcileShipperAvailabilityAfterMutation(savedShipment, previousStatus, previousShipperId, true);
    }

    public void completeDelivery(Long shipmentId, String username, String otp) {
        Shipment shipment = getShipmentForShipper(shipmentId, username);
        if (shipment.getStatus() != ShipmentStatus.DELIVERING) {
            throw new IllegalStateException("Shipment is not in DELIVERING state");
        }
        if (otp == null || !otp.equals(shipment.getOtpCode())) {
            throw new IllegalArgumentException("Invalid OTP");
        }

        Long previousShipperId = shipment.getShipper() == null ? null : shipment.getShipper().getId();
        ShipmentStatus previousStatus = shipment.getStatus();
        applyStrictTransition(shipment, ShipmentStatus.COMPLETED, null);
        Shipment savedShipment = shipmentRepository.save(shipment);
        reconcileShipperAvailabilityAfterMutation(savedShipment, previousStatus, previousShipperId, true);
    }

    public void markFailed(Long shipmentId, String username, String note) {
        Shipment shipment = getShipmentForShipper(shipmentId, username);
        Long previousShipperId = shipment.getShipper() == null ? null : shipment.getShipper().getId();
        ShipmentStatus previousStatus = shipment.getStatus();
        applyStrictTransition(shipment, ShipmentStatus.FAILED, note);
        Shipment savedShipment = shipmentRepository.save(shipment);
        reconcileShipperAvailabilityAfterMutation(savedShipment, previousStatus, previousShipperId, true);
    }

    private Shipment getShipmentForShipper(Long shipmentId, String username) {
        if (shipmentId == null) {
            throw new IllegalArgumentException("Shipment id is required");
        }
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + shipmentId));
        if (shipment.getShipper() == null || shipment.getShipper().getUser() == null) {
            throw new IllegalStateException("Shipment has not been assigned to a shipper");
        }
        String ownerUsername = shipment.getShipper().getUser().getUsername();
        if (!ownerUsername.equals(username)) {
            throw new IllegalStateException("Shipment does not belong to current shipper");
        }
        return shipment;
    }

    private void applyStrictTransition(Shipment shipment, ShipmentStatus targetStatus, String failureNote) {
        ShipmentStatus currentStatus = shipment.getStatus();
        if (currentStatus == targetStatus) {
            return;
        }

        boolean validTransition;
        switch (currentStatus) {
            case PENDING_ASSIGNMENT -> validTransition = targetStatus == ShipmentStatus.ASSIGNED;
            case ASSIGNED -> validTransition = targetStatus == ShipmentStatus.PICKED_UP || targetStatus == ShipmentStatus.FAILED;
            case PICKED_UP -> validTransition = targetStatus == ShipmentStatus.DELIVERING || targetStatus == ShipmentStatus.FAILED;
            case DELIVERING -> validTransition = targetStatus == ShipmentStatus.COMPLETED || targetStatus == ShipmentStatus.FAILED;
            default -> validTransition = false;
        }

        if (!validTransition) {
            throw new IllegalStateException("Invalid shipment transition: " + currentStatus + " -> " + targetStatus);
        }

        if (targetStatus == ShipmentStatus.ASSIGNED && shipment.getShipper() == null) {
            throw new IllegalStateException("Assigned shipment must have a shipper");
        }

        LocalDateTime now = LocalDateTime.now();
        if (targetStatus == ShipmentStatus.PICKED_UP) {
            shipment.setPickedUpAt(now);
        }
        if (targetStatus == ShipmentStatus.DELIVERING) {
            shipment.setDeliveringAt(now);
        }
        if (targetStatus == ShipmentStatus.COMPLETED) {
            shipment.setCompletedAt(now);
            shipment.setOtpVerified(true);
        }
        if (targetStatus == ShipmentStatus.FAILED) {
            shipment.setFailureNote(trimToNull(failureNote));
        } else {
            shipment.setFailureNote(null);
        }

        shipment.setStatus(targetStatus);
    }

    private void ensureOrderReadyForShipment(Order order) {
        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("Only PAID orders can be shipped");
        }
    }

    private Optional<Order> findOldestPaidOrderWithoutShipment() {
        return orderRepository.findOldestOrdersWithoutShipmentByStatus(OrderStatus.PAID, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    private Shipment createShipmentForOrderWithAssignedShipper(Order order,
                                                               Shipper shipper,
                                                               boolean triggerDispatch) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Order is required");
        }
        if (shipper == null || shipper.getId() == null) {
            throw new IllegalArgumentException("Shipper is required");
        }
        ensureOrderReadyForShipment(order);

        Optional<Shipment> existingShipment = shipmentRepository.findByOrderId(order.getId());
        if (existingShipment.isPresent()) {
            Shipment current = existingShipment.get();
            if (current.getStatus() == ShipmentStatus.PENDING_ASSIGNMENT) {
                assignShipmentToShipperInternal(current, shipper, triggerDispatch);
            }
            return current;
        }

        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setShipper(shipper);
        shipment.setStatus(ShipmentStatus.ASSIGNED);
        shipment.setShippingName(requireTextOrFallback(null, order.getShippingFullName(), "Recipient name is required"));
        shipment.setShippingPhone(requireTextOrFallback(null, order.getShippingPhone(), "Recipient phone is required"));
        shipment.setShippingAddress(requireTextOrFallback(null, order.getShippingAddress(), "Recipient address is required"));
        shipment.setOtpCode(generateOtp());
        shipment.setOtpVerified(false);

        Shipment savedShipment = shipmentRepository.save(shipment);
        markShipperBusy(shipper);
        return savedShipment;
    }

    private Shipment assignShipmentToShipperInternal(Shipment shipment,
                                                     Shipper shipper,
                                                     boolean triggerDispatch) {
        if (shipment == null || shipment.getId() == null) {
            throw new IllegalArgumentException("Shipment is required");
        }
        if (shipper == null || shipper.getId() == null) {
            throw new IllegalArgumentException("Shipper is required");
        }
        if (shipment.getStatus() != ShipmentStatus.PENDING_ASSIGNMENT && shipment.getStatus() != ShipmentStatus.ASSIGNED) {
            throw new IllegalStateException("Only pending/assigned shipments can be assigned");
        }

        Long previousShipperId = shipment.getShipper() == null ? null : shipment.getShipper().getId();
        ShipmentStatus previousStatus = shipment.getStatus();
        shipment.setShipper(shipper);
        shipment.setStatus(ShipmentStatus.ASSIGNED);
        shipment.setUpdatedAt(LocalDateTime.now());
        Shipment savedShipment = shipmentRepository.save(shipment);
        reconcileShipperAvailabilityAfterMutation(savedShipment, previousStatus, previousShipperId, triggerDispatch);
        return savedShipment;
    }

    private void reconcileShipperAvailabilityAfterMutation(Shipment shipment,
                                                           ShipmentStatus previousStatus,
                                                           Long previousShipperId,
                                                           boolean triggerDispatch) {
        if (shipment != null && shipment.getShipper() != null && IN_PROGRESS_STATUSES.contains(shipment.getStatus())) {
            markShipperBusy(shipment.getShipper());
        }

        boolean shipperChanged = previousShipperId != null
                && (shipment == null || shipment.getShipper() == null || !previousShipperId.equals(shipment.getShipper().getId()));

        if (previousShipperId != null
                && (shipment == null || shipment.getShipper() == null || !previousShipperId.equals(shipment.getShipper().getId()))) {
            refreshShipperAvailability(previousShipperId);
        }

        if (shipment != null && shipment.getShipper() != null
                && isTerminalStatus(shipment.getStatus())) {
            refreshShipperAvailability(shipment.getShipper().getId());
        }

        if (triggerDispatch && ((shipment != null && isTerminalStatus(shipment.getStatus())) || shipperChanged)) {
            dispatchAutoShipmentQueue();
        }
    }

    private boolean isTerminalStatus(ShipmentStatus status) {
        return status == ShipmentStatus.COMPLETED || status == ShipmentStatus.FAILED;
    }

    private void markShipperBusy(Shipper shipper) {
        if (shipper == null || shipper.getId() == null) {
            return;
        }
        Shipper lockedShipper = shipperRepository.findByIdForUpdate(shipper.getId())
                .orElse(null);
        if (lockedShipper == null) {
            return;
        }
        if (lockedShipper.getStatus() != ShipperStatus.ACTIVE) {
            return;
        }
        if (Boolean.FALSE.equals(lockedShipper.getIsAvailable())) {
            return;
        }
        lockedShipper.setIsAvailable(false);
        lockedShipper.setUpdatedAt(LocalDateTime.now());
        shipperRepository.save(lockedShipper);
    }

    private void refreshShipperAvailability(Long shipperId) {
        if (shipperId == null) {
            return;
        }
        Shipper shipper = shipperRepository.findByIdForUpdate(shipperId)
                .orElse(null);
        if (shipper == null) {
            return;
        }

        boolean hasInProgressShipment = shipmentRepository.existsByShipperIdAndStatusIn(shipperId, IN_PROGRESS_STATUSES);
        boolean shouldBeAvailable = shipper.getStatus() == ShipperStatus.ACTIVE && !hasInProgressShipment;
        if (Boolean.valueOf(shouldBeAvailable).equals(shipper.getIsAvailable())) {
            return;
        }

        shipper.setIsAvailable(shouldBeAvailable);
        shipper.setUpdatedAt(LocalDateTime.now());
        shipperRepository.save(shipper);
    }

    private Shipper getActiveShipper(Long shipperId) {
        if (shipperId == null) {
            throw new IllegalArgumentException("Shipper id is required");
        }

        Shipper shipper = shipperRepository.findById(shipperId)
                .orElseThrow(() -> new IllegalArgumentException("Shipper not found: " + shipperId));
        if (shipper.getStatus() != ShipperStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE shipper can receive shipments");
        }
        return shipper;
    }

    private boolean matchesKeyword(Shipment shipment, String normalizedKeyword) {
        if (normalizedKeyword == null) {
            return true;
        }

        return containsIgnoreCase(shipment.getShippingName(), normalizedKeyword)
                || containsIgnoreCase(shipment.getShippingPhone(), normalizedKeyword)
                || containsIgnoreCase(shipment.getShippingAddress(), normalizedKeyword)
                || (shipment.getOrder() != null && containsIgnoreCase(String.valueOf(shipment.getOrder().getId()), normalizedKeyword))
                || (shipment.getOrder() != null && shipment.getOrder().getUser() != null
                && containsIgnoreCase(shipment.getOrder().getUser().getUsername(), normalizedKeyword))
                || (shipment.getShipper() != null && shipment.getShipper().getUser() != null
                && containsIgnoreCase(shipment.getShipper().getUser().getUsername(), normalizedKeyword));
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }

    private String requireTextOrFallback(String value, String fallback, String message) {
        String selected = value;
        if (selected == null || selected.isBlank()) {
            selected = fallback;
        }
        return requireText(selected, message);
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
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String generateOtp() {
        return String.format("%06d", OTP_RANDOM.nextInt(1_000_000));
    }
}
