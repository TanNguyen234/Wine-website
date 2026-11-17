package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.OtpDeliveryStatus;
import com.strongwine.strongwine.entity.Payment;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.entity.Shipper;
import com.strongwine.strongwine.entity.ShipperStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.event.OrderStatusUpdatedEvent;
import com.strongwine.strongwine.event.ShipmentLiveStatusEvent;
import com.strongwine.strongwine.exception.OtpDeliveryException;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.ShipmentRepository;
import com.strongwine.strongwine.repository.ShipperRepository;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.port.CodSettlementPort;
import com.strongwine.strongwine.service.port.ShipmentOrderLifecyclePort;
import com.strongwine.strongwine.util.AddressTextUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class ShipmentService implements ShipmentOrderLifecyclePort {

    private static final SecureRandom OTP_RANDOM = new SecureRandom();

    private static final int AUTO_DISPATCH_BATCH_LIMIT = 200;
    private static final int OTP_TTL_MINUTES = 1440;
    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final int OTP_LOCK_MINUTES = 15;
    private static final int OTP_RESEND_COOLDOWN_SECONDS = 60;
    private static final int PAID_SHIPMENT_BACKFILL_BATCH_SIZE = 100;
    private static final int PAID_SHIPMENT_BACKFILL_MAX_BATCHES = 20;

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
    private final ShipmentOtpAuditService shipmentOtpAuditService;
    private final ShipmentStatusHistoryService shipmentStatusHistoryService;
    private final ShipmentOtpStateService shipmentOtpStateService;
    private final ShipmentOtpCryptoService shipmentOtpCryptoService;
    private final ShippingFeeService shippingFeeService;
    private final AdminAuditLogService adminAuditLogService;
    private final CodSettlementPort codSettlementPort;
    private final ApplicationEventPublisher applicationEventPublisher;
    private InventoryService inventoryService;
    private com.strongwine.strongwine.service.port.VoucherPricingPort voucherPricingPort;

    public ShipmentService(ShipmentRepository shipmentRepository,
                           ShipperRepository shipperRepository,
                           OrderRepository orderRepository,
                           UserRepository userRepository,
                           ShipmentOtpEmailService shipmentOtpEmailService,
                           ShipmentOtpAuditService shipmentOtpAuditService,
                           ShipmentStatusHistoryService shipmentStatusHistoryService) {
        this(shipmentRepository, shipperRepository, orderRepository, userRepository,
                shipmentOtpEmailService, shipmentOtpAuditService, shipmentStatusHistoryService,
                null, null, null, null, null, null);
    }

    @Autowired
    public ShipmentService(ShipmentRepository shipmentRepository,
                           ShipperRepository shipperRepository,
                           OrderRepository orderRepository,
                           UserRepository userRepository,
                           ShipmentOtpEmailService shipmentOtpEmailService,
                           ShipmentOtpAuditService shipmentOtpAuditService,
                           ShipmentStatusHistoryService shipmentStatusHistoryService,
                           @Autowired(required = false) ShipmentOtpStateService shipmentOtpStateService,
                           @Autowired(required = false) ShipmentOtpCryptoService shipmentOtpCryptoService,
                           @Autowired(required = false) ShippingFeeService shippingFeeService,
                           @Autowired(required = false) AdminAuditLogService adminAuditLogService,
                           @Autowired(required = false) @Lazy CodSettlementPort codSettlementPort,
                           @Autowired(required = false) ApplicationEventPublisher applicationEventPublisher) {
        this.shipmentRepository = shipmentRepository;
        this.shipperRepository = shipperRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.shipmentOtpEmailService = shipmentOtpEmailService;
        this.shipmentOtpAuditService = shipmentOtpAuditService;
        this.shipmentStatusHistoryService = shipmentStatusHistoryService;
        this.shipmentOtpStateService = shipmentOtpStateService;
        this.shipmentOtpCryptoService = shipmentOtpCryptoService != null
                ? shipmentOtpCryptoService
                : new ShipmentOtpCryptoService("");
        this.shippingFeeService = shippingFeeService != null
                ? shippingFeeService
                : new ShippingFeeService();
        this.adminAuditLogService = adminAuditLogService;
        this.codSettlementPort = codSettlementPort;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Autowired(required = false)
    public void setInventoryService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Autowired(required = false)
    public void setVoucherPricingPort(com.strongwine.strongwine.service.port.VoucherPricingPort voucherPricingPort) {
        this.voucherPricingPort = voucherPricingPort;
    }

    public enum EnsureShipmentResult {
        CREATED,
        EXISTED,
        SKIPPED_NOT_PAID
    }

    public record BackfillSummary(int created, int existed, int skipped, int failed) {
    }

    public EnsureShipmentResult ensureShipmentForPaidOrder(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Thiếu mã đơn hàng");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + orderId));
        return ensureShipmentForPaidOrder(order);
    }

    public EnsureShipmentResult ensureShipmentForPaidOrder(Order order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Đơn hàng không hợp lệ để tạo đơn giao hàng");
        }
        boolean isPaid = order.getStatus() == OrderStatus.PAID;
        boolean isCod = order.getPaymentMethod() == PaymentMethod.COD && (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.PAID);
        if (!isPaid && !isCod) {
            return EnsureShipmentResult.SKIPPED_NOT_PAID;
        }

        if (shipmentRepository.findByOrderId(order.getId()).isPresent()) {
            return EnsureShipmentResult.EXISTED;
        }

        createAutoShipmentForPaidOrder(order);
        return EnsureShipmentResult.CREATED;
    }

    public BackfillSummary backfillMissingShipmentsForPaidOrders() {
        int created = 0;
        int existed = 0;
        int skipped = 0;
        int failed = 0;

        Pageable pageable = PageRequest.of(0, PAID_SHIPMENT_BACKFILL_BATCH_SIZE);
        for (int batch = 0; batch < PAID_SHIPMENT_BACKFILL_MAX_BATCHES; batch++) {
            List<Order> candidates = orderRepository.findOldestOrdersWithoutShipmentByStatus(OrderStatus.PAID, pageable);
            if (candidates.isEmpty()) {
                break;
            }

            for (Order order : candidates) {
                if (order.getPaymentStatus() != PaymentStatus.SUCCESS) {
                    skipped += 1;
                    continue;
                }
                try {
                    EnsureShipmentResult result = ensureShipmentForPaidOrder(order);
                    if (result == EnsureShipmentResult.CREATED) {
                        created += 1;
                    } else if (result == EnsureShipmentResult.EXISTED) {
                        existed += 1;
                    } else {
                        skipped += 1;
                    }
                } catch (Exception ex) {
                    failed += 1;
                }
            }
        }

        return new BackfillSummary(created, existed, skipped, failed);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = OtpDeliveryException.class)
    public void handleOrderPaidEvent(Long orderId, String paymentReference) {
        if (orderId == null) {
            return;
        }

        EnsureShipmentResult result = ensureShipmentForPaidOrder(orderId);
        if (result == EnsureShipmentResult.SKIPPED_NOT_PAID) {
            return;
        }

        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("Không thể tạo đơn giao hàng cho đơn: " + orderId));
        if (shipment.getOtpDeliveryStatus() == OtpDeliveryStatus.SENT
                && hasOtpSecret(shipment)
                && Boolean.FALSE.equals(shipment.getOtpVerified())) {
            return;
        }

        sendOtpForShipment(shipment.getId(), "system-order-paid", "ORDER_PAID:" + (paymentReference == null ? "N/A" : paymentReference));
    }

    public Shipment createOrReuseShipment(Order order) {
        return createAutoShipmentForPaidOrder(order);
    }

    public Shipment createAutoShipmentForPaidOrder(Order order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Đơn hàng không hợp lệ để tạo đơn giao hàng tự động");
        }
        ensureOrderReadyForShipment(order);

        Optional<Shipment> existingShipment = shipmentRepository.findByOrderId(order.getId());
        if (existingShipment.isPresent()) {
            return existingShipment.get();
        }

        Double[] targetCoords = resolveTargetCoordinates(order);
        Optional<Shipper> availableShipper = findAssignableShipper(targetCoords[0], targetCoords[1]);

        try {
            return availableShipper
                    .map(shipper -> createShipmentForOrderWithAssignedShipper(order, shipper, false))
                    .orElseGet(() -> createShipmentForOrderWithoutShipper(order, true));
        } catch (DataIntegrityViolationException ex) {
            return shipmentRepository.findByOrderId(order.getId())
                    .orElseThrow(() -> ex);
        }
    }

    public int dispatchAutoShipmentQueue() {
        int processed = 0;
        while (processed < AUTO_DISPATCH_BATCH_LIMIT) {
            Optional<Shipment> pendingShipment = shipmentRepository.findFirstByStatusOrderByCreatedAtAsc(ShipmentStatus.PENDING_ASSIGNMENT);
            if (pendingShipment.isEmpty()) {
                break;
            }
            Double[] targetCoords = resolveTargetCoordinates(pendingShipment.get());
            Optional<Shipper> availableShipper = findAssignableShipper(targetCoords[0], targetCoords[1]);
            if (availableShipper.isEmpty()) {
                break;
            }

            assignShipmentToShipperInternal(pendingShipment.get(), availableShipper.get(), false);
            processed += 1;
        }
        return processed;
    }

    public void onShipperProfileUpdated(Long shipperId, boolean requestedAvailability) {
        refreshShipperAvailability(shipperId);
        if (!requestedAvailability) {
            setShipperUnavailable(shipperId);
        }
        dispatchAutoShipmentQueue();
    }

    public void setShipperAvailability(Long shipperId, boolean requestedAvailability) {
        boolean capacityAvailable = refreshShipperAvailability(shipperId);
        if (!requestedAvailability) {
            setShipperUnavailable(shipperId);
            return;
        }
        if (!capacityAvailable) {
            throw new IllegalStateException("Shipper không hoạt động hoặc đã đạt giới hạn đơn đang giao");
        }
        dispatchAutoShipmentQueue();
    }

    @Transactional(readOnly = true)
    public List<Shipment> getMyShipments(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng: " + username));

        Shipper shipper = shipperRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ shipper cho tài khoản: " + username));
        return shipmentRepository.findByShipperIdOrderByCreatedAtDesc(shipper.getId());
    }

    /**
     * Get all active shipments (not terminal COMPLETED, FAILED, or CANCELLED) for Administrative monitoring.
     */
    @Transactional(readOnly = true)
    public List<Shipment> getActiveShipmentsForAdminView() {
        return shipmentRepository.findAll().stream()
                .filter(s -> !isTerminalStatus(s.getStatus()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Shipment> getShipmentByOrderId(Long orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return shipmentRepository.findByOrderIdWithShipper(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Shipment> getShipmentMapByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Shipment> shipmentByOrderId = new LinkedHashMap<>();
        for (Shipment shipment : shipmentRepository.findByOrderIdInWithShipper(orderIds)) {
            if (shipment.getOrder() != null && shipment.getOrder().getId() != null) {
                shipmentByOrderId.put(shipment.getOrder().getId(), shipment);
            }
        }
        return shipmentByOrderId;
    }

    @Override
    public void cancelShipmentForOrder(Long orderId, String actorUsername, String reason) {
        if (orderId == null) {
            return;
        }
        Optional<Shipment> foundOpt = shipmentRepository.findByOrderId(orderId);
        if (foundOpt.isEmpty()) {
            return;
        }
        Shipment found = foundOpt.get();
        Shipment shipment = found.getId() == null
                ? found
                : shipmentRepository.findByIdForUpdate(found.getId()).orElse(found);

        if (shipment.getStatus() == ShipmentStatus.COMPLETED
                || shipment.getStatus() == ShipmentStatus.CANCELLED) {
            return;
        }

        ShipmentStatus previousStatus = shipment.getStatus();
        Shipper assignedShipper = shipment.getShipper();
        LocalDateTime now = LocalDateTime.now();
        String normalizedReason = trimToNull(reason);
        String resolvedReason = normalizedReason != null ? normalizedReason : "ORDER_CANCELLED";

        shipment.setStatus(ShipmentStatus.CANCELLED);
        shipment.setStatusReason(resolvedReason);
        invalidateOtp(shipment, now, OtpDeliveryStatus.PENDING);

        Shipment saved = shipmentRepository.save(shipment);

        if (assignedShipper != null && assignedShipper.getId() != null) {
            Shipper lockedShipper = shipperRepository.findByIdForUpdate(assignedShipper.getId())
                    .orElse(assignedShipper);
            int currentCount = lockedShipper.getActiveShipmentCount() == null ? 0 : lockedShipper.getActiveShipmentCount();
            boolean hadActiveSlot = previousStatus == ShipmentStatus.ASSIGNED
                    || previousStatus == ShipmentStatus.PICKED_UP
                    || previousStatus == ShipmentStatus.DELIVERING;
            int nextCount = hadActiveSlot ? Math.max(0, currentCount - 1) : Math.max(0, currentCount);
            lockedShipper.setActiveShipmentCount(nextCount);
            lockedShipper.setIsAvailable(lockedShipper.getStatus() == ShipperStatus.ACTIVE
                    && nextCount < normalizeCapacity(lockedShipper));
            lockedShipper.setUpdatedAt(now);
            shipperRepository.save(lockedShipper);
        }

        recordStatusTransition(saved, previousStatus, resolvedReason, "source=order-cancel", actorUsername);
        if (adminAuditLogService != null) {
            String actor = trimToNull(actorUsername) != null ? actorUsername.trim() : "system";
            adminAuditLogService.log(
                    actor,
                    "SHIPMENT_CANCELLED",
                    "SHIPMENT",
                    saved.getId(),
                    Map.of("status", previousStatus == null ? "UNKNOWN" : previousStatus.name(), "orderId", orderId),
                    Map.of("status", ShipmentStatus.CANCELLED.name(), "reason", resolvedReason),
                    null
            );
        }
        publishLiveStatusEvent(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> decryptOtpForOrderView(Shipment shipment) {
        if (shipment == null) {
            return Optional.empty();
        }
        if (shipment.getOtpEncrypted() != null && !shipment.getOtpEncrypted().isBlank()) {
            Optional<String> decrypted = shipmentOtpCryptoService.decrypt(shipment.getOtpEncrypted());
            if (decrypted.isPresent() && !decrypted.get().isBlank()) {
                return decrypted;
            }
            return Optional.empty();
        }
        if (shipment.getOtpCode() != null && !shipment.getOtpCode().isBlank()) {
            return Optional.of(shipment.getOtpCode().trim());
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public boolean hasActiveShipmentsForShipper(Long shipperId) {
        if (shipperId == null) {
            return false;
        }
        return shipmentRepository.existsByShipperIdAndStatusIn(shipperId, IN_PROGRESS_STATUSES);
    }

    @Transactional(readOnly = true)
    public List<ShipmentStatus> getAllowedManualTransitions(ShipmentStatus currentStatus) {
        if (currentStatus == null) {
            return List.of();
        }
        return switch (currentStatus) {
            case PENDING_ASSIGNMENT -> List.of(ShipmentStatus.CANCELLED);
            case ASSIGNED -> List.of(ShipmentStatus.PICKED_UP, ShipmentStatus.FAILED, ShipmentStatus.CANCELLED);
            case PICKED_UP -> List.of(ShipmentStatus.DELIVERING, ShipmentStatus.FAILED, ShipmentStatus.CANCELLED);
            case DELIVERING -> List.of(ShipmentStatus.COMPLETED, ShipmentStatus.FAILED, ShipmentStatus.CANCELLED);
            default -> List.of();
        };
    }

    @Transactional(readOnly = true)
    public Map<Long, List<ShipmentStatus>> buildAllowedTransitionsMap(List<Shipment> shipments) {
        if (shipments == null || shipments.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<ShipmentStatus>> map = new LinkedHashMap<>();
        for (Shipment s : shipments) {
            if (s != null && s.getId() != null) {
                map.put(s.getId(), getAllowedManualTransitions(s.getStatus()));
            }
        }
        return map;
    }

    public Shipment assignShipperToShipment(Long shipmentId, Long shipperId, String actorUsername) {
        if (shipmentId == null || shipperId == null) {
            throw new IllegalArgumentException("Thiếu mã vận đơn hoặc shipper");
        }
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn giao hàng: " + shipmentId));
        Shipper shipper = lockAndValidateAssignableShipper(shipperId)
                .orElseThrow(() -> new IllegalStateException("Shipper không hoạt động hoặc đã đạt giới hạn đơn đang giao"));
        return assignShipmentToShipperInternal(shipment, shipper, true);
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
    public org.springframework.data.domain.Page<Shipment> getShipmentsForAdminPage(Long orderId,
                                               Long shipperId,
                                               ShipmentStatus status,
                                               String keyword,
                                               Pageable pageable) {
        List<Shipment> allFiltered = getShipmentsForAdmin(orderId, shipperId, status, keyword);
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allFiltered.size());
        List<Shipment> pageContent = start > allFiltered.size() ? List.of() : allFiltered.subList(start, end);
        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, allFiltered.size());
    }

    @Transactional(readOnly = true)
    public Shipment getShipmentByIdForAdmin(Long shipmentId) {
        if (shipmentId == null) {
            throw new IllegalArgumentException("Thiếu mã đơn giao hàng");
        }
        return shipmentRepository.findByIdForAdmin(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn giao hàng: " + shipmentId));
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
            throw new IllegalArgumentException("Thiếu mã đơn hàng");
        }
        if (shipmentRepository.existsByOrderId(orderId)) {
            throw new IllegalArgumentException("Đơn hàng đã tồn tại đơn giao hàng: " + orderId);
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + orderId));
        ensureOrderReadyForShipment(order);

        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setShippingName(requireTextOrFallback(shippingName, order.getShippingFullName(), "Vui lòng nhập tên người nhận"));
        shipment.setShippingPhone(requireTextOrFallback(shippingPhone, order.getShippingPhone(), "Vui lòng nhập số điện thoại người nhận"));
        shipment.setShippingEmail(resolveShippingEmail(order));
        shipment.setShippingAddress(requireTextOrFallback(shippingAddress, order.getShippingAddress(), "Vui lòng nhập địa chỉ người nhận"));
        copyCoordinatesFromOrder(order, shipment);
        generateNewOtp(shipment, LocalDateTime.now());

        if (shipperId != null) {
            Shipper assigned = lockAndValidateAssignableShipper(shipperId)
                    .orElseThrow(() -> new IllegalStateException("Shipper không hoạt động hoặc đã đạt giới hạn đơn đang giao"));
            shipment.setShipper(assigned);
            shipment.setStatus(ShipmentStatus.ASSIGNED);
            shipment.setAssignedAt(LocalDateTime.now());
            shipment.setStatusReason("ADMIN_CREATE_ASSIGNED");
        } else {
            shipment.setStatus(ShipmentStatus.PENDING_ASSIGNMENT);
            shipment.setStatusReason("ADMIN_CREATE_PENDING");
        }

        Shipment savedShipment = shipmentRepository.save(shipment);
        recordStatusTransition(savedShipment, null, savedShipment.getStatusReason(), "source=admin-create", null);
        shipmentOtpAuditService.log(savedShipment, "OTP_GENERATED", "SUCCESS", "ADMIN_CREATE_SHIPMENT", "shipment_created", null);

        if (savedShipment.getShipper() != null) {
            markShipperBusy(savedShipment.getShipper());
        } else {
            dispatchAutoShipmentQueue();
        }
        publishLiveStatusEvent(savedShipment);
        return savedShipment;
    }

    public Shipment updateShipmentForAdmin(Long shipmentId,
                                           String shippingName,
                                           String shippingPhone,
                                           String shippingAddress,
                                           ShipmentStatus targetStatus,
                                           String failureNote) {
        return updateShipmentForAdmin(shipmentId, shippingName, shippingPhone, shippingAddress, targetStatus, failureNote, null);
    }

    public Shipment updateShipmentForAdmin(Long shipmentId,
                                           String shippingName,
                                           String shippingPhone,
                                           String shippingAddress,
                                           ShipmentStatus targetStatus,
                                           String failureNote,
                                           String actorUsername) {
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn giao hàng: " + shipmentId));
        Long previousShipperId = shipment.getShipper() == null ? null : shipment.getShipper().getId();
        ShipmentStatus previousStatus = shipment.getStatus();

        shipment.setShippingName(requireText(shippingName, "Vui lòng nhập tên người nhận"));
        shipment.setShippingPhone(requireText(shippingPhone, "Vui lòng nhập số điện thoại người nhận"));
        shipment.setShippingAddress(requireText(shippingAddress, "Vui lòng nhập địa chỉ người nhận"));

        LocalDateTime now = LocalDateTime.now();
        boolean completedNow = false;
        boolean failedNow = false;
        if (targetStatus != null && targetStatus != shipment.getStatus()) {
            if (targetStatus == ShipmentStatus.COMPLETED) {
                applyStrictTransition(shipment, ShipmentStatus.COMPLETED, null);
                applyAdminOverrideState(shipment, resolveAdminOverrideReason(failureNote));
                populateCodCollectedAmountIfApplicable(shipment);
                clearSensitiveOtpAfterCompletion(shipment);
                shipmentOtpAuditService.log(shipment, "ADMIN_OVERRIDE_COMPLETED", "SUCCESS",
                        shipment.getAdminOverrideReason(), "source=admin-edit", actorUsername);
                completedNow = true;
            } else {
                applyStrictTransition(shipment, targetStatus, failureNote);
                if (targetStatus == ShipmentStatus.FAILED) {
                    invalidateOtp(shipment, now, OtpDeliveryStatus.FAILED);
                    failedNow = true;
                }
                shipment.setAdminOverride(false);
                shipment.setAdminOverrideReason(null);
            }
        } else if (targetStatus == ShipmentStatus.FAILED && shipment.getStatus() == ShipmentStatus.FAILED) {
            shipment.setFailureNote(trimToNull(failureNote));
            shipment.setStatusReason(trimToNull(failureNote));
        }

        shipment.setUpdatedAt(now);
        Shipment savedShipment = shipmentRepository.save(shipment);
        if (completedNow) {
            settleCodOrderAfterCompletion(savedShipment, actorUsername, now);
        } else if (failedNow) {
            releasePendingCodOrderOnTerminalFailure(savedShipment, actorUsername, failureNote, now);
        }
        recordStatusTransition(savedShipment, previousStatus,
                targetStatus == ShipmentStatus.FAILED ? trimToNull(failureNote) : savedShipment.getStatusReason(),
                "source=admin-edit",
                actorUsername);
        reconcileShipperAvailabilityAfterMutation(savedShipment, previousStatus, previousShipperId, true);
        publishLiveStatusEvent(savedShipment);
        return savedShipment;
    }

    public Shipment transitionShipmentStatusByAdmin(Long shipmentId, ShipmentStatus targetStatus, String failureNote) {
        return transitionShipmentStatusByAdmin(shipmentId, targetStatus, failureNote, null, null);
    }

    public Shipment transitionShipmentStatusByAdmin(Long shipmentId,
                                                    ShipmentStatus targetStatus,
                                                    String failureNote,
                                                    String actorUsername,
                                                    String overrideReason) {
        if (targetStatus == null) {
            throw new IllegalArgumentException("Thiếu trạng thái cần cập nhật");
        }

        if (targetStatus == ShipmentStatus.COMPLETED) {
            return completeDeliveryByAdminOverride(shipmentId, actorUsername, overrideReason);
        }

        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn giao hàng: " + shipmentId));
        if (shipment.getStatus() == targetStatus) {
            return shipment;
        }

        Long previousShipperId = shipment.getShipper() == null ? null : shipment.getShipper().getId();
        ShipmentStatus previousStatus = shipment.getStatus();
        LocalDateTime now = LocalDateTime.now();
        applyStrictTransition(shipment, targetStatus, failureNote);
        if (targetStatus == ShipmentStatus.FAILED || targetStatus == ShipmentStatus.CANCELLED) {
            invalidateOtp(shipment, now,
                    targetStatus == ShipmentStatus.FAILED ? OtpDeliveryStatus.FAILED : OtpDeliveryStatus.PENDING);
        }
        shipment.setAdminOverride(false);
        shipment.setAdminOverrideReason(null);
        shipment.setUpdatedAt(now);
        Shipment savedShipment = shipmentRepository.save(shipment);
        if (targetStatus == ShipmentStatus.FAILED) {
            releasePendingCodOrderOnTerminalFailure(savedShipment, actorUsername, failureNote, now);
        }
        recordStatusTransition(savedShipment, previousStatus,
            (targetStatus == ShipmentStatus.FAILED || targetStatus == ShipmentStatus.CANCELLED)
                    ? trimToNull(failureNote)
                    : savedShipment.getStatusReason(),
            "source=admin-status",
            actorUsername);
        reconcileShipperAvailabilityAfterMutation(savedShipment, previousStatus, previousShipperId, true);
        publishLiveStatusEvent(savedShipment);
        return savedShipment;
    }

    public Shipment completeDeliveryByAdminOverride(Long shipmentId, String actorUsername, String overrideReason) {
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn giao hàng: " + shipmentId));

        if (shipment.getStatus() != ShipmentStatus.DELIVERING) {
            throw new IllegalStateException("Chỉ đơn đang giao mới được hoàn tất bởi quản trị viên override");
        }

        Long previousShipperId = shipment.getShipper() == null ? null : shipment.getShipper().getId();
        ShipmentStatus previousStatus = shipment.getStatus();
        LocalDateTime now = LocalDateTime.now();

        applyStrictTransition(shipment, ShipmentStatus.COMPLETED, null);
        applyAdminOverrideState(shipment, resolveAdminOverrideReason(overrideReason));
        populateCodCollectedAmountIfApplicable(shipment);
        clearSensitiveOtpAfterCompletion(shipment);
        shipment.setUpdatedAt(now);

        Shipment savedShipment = shipmentRepository.save(shipment);
        settleCodOrderAfterCompletion(savedShipment, actorUsername, now);
        recordStatusTransition(savedShipment, previousStatus, savedShipment.getAdminOverrideReason(), "source=admin-override", actorUsername);
        shipmentOtpAuditService.log(savedShipment, "ADMIN_OVERRIDE_COMPLETED", "SUCCESS",
                savedShipment.getAdminOverrideReason(), "source=admin-status", actorUsername);
        reconcileShipperAvailabilityAfterMutation(savedShipment, previousStatus, previousShipperId, true);
        publishLiveStatusEvent(savedShipment);
        return savedShipment;
    }

    @Transactional(noRollbackFor = OtpDeliveryException.class)
    public Shipment sendOtpForShipment(Long shipmentId, String actorUsername, String reason) {
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn giao hàng: " + shipmentId));

        if (isTerminalStatus(shipment.getStatus())) {
            throw new IllegalStateException("Đơn giao hàng đã kết thúc, không thể gửi OTP");
        }

        if (!hasOtpSecret(shipment)) {
            generateNewOtp(shipment, LocalDateTime.now());
            shipmentRepository.save(shipment);
            shipmentOtpAuditService.log(shipment, "OTP_GENERATED", "SUCCESS", reason, "source=send-otp", actorUsername);
        }

        return sendOtpForShipmentInternal(shipment, actorUsername, reason, false);
    }

    @Transactional(noRollbackFor = OtpDeliveryException.class)
    public Shipment regenerateOtpForShipment(Long shipmentId) {
        return resendOtpForShipmentByAdmin(shipmentId, null, "LEGACY_REGENERATE");
    }

    @Transactional(noRollbackFor = OtpDeliveryException.class)
    public Shipment resendOtpForShipmentByAdmin(Long shipmentId, String actorUsername, String reason) {
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn giao hàng: " + shipmentId));
        return resendOtpInternal(shipment, actorUsername, reason, false);
    }

    @Transactional(noRollbackFor = OtpDeliveryException.class)
    public Shipment resendOtpForShipmentByShipper(Long shipmentId, String username, String reason) {
        Shipment shipment = getShipmentForShipper(shipmentId, username);
        return resendOtpInternal(shipment, username, reason, true);
    }

    private Shipment resendOtpInternal(Shipment shipment,
                                       String actorUsername,
                                       String reason,
                                       boolean validateOwnership) {
        if (shipment == null || shipment.getId() == null) {
            throw new IllegalArgumentException("Thiếu mã đơn giao hàng");
        }
        if (validateOwnership && (shipment.getShipper() == null || shipment.getShipper().getUser() == null)) {
            throw new IllegalStateException("Đơn giao hàng chưa được gán shipper");
        }
        if (isTerminalStatus(shipment.getStatus())) {
            throw new IllegalStateException("Đơn giao hàng đã kết thúc, không thể tạo lại OTP");
        }

        LocalDateTime now = LocalDateTime.now();
        assertResendCooldown(shipment, now);
        generateNewOtp(shipment, now);
        shipment.setUpdatedAt(now);
        Shipment saved = shipmentRepository.save(shipment);
        shipmentOtpAuditService.log(saved, "OTP_REGENERATED", "SUCCESS", reason, "source=resend", actorUsername);
        return sendOtpForShipmentInternal(saved, actorUsername, reason, true);
    }

    private Shipment sendOtpForShipmentInternal(Shipment shipment,
                                                String actorUsername,
                                                String reason,
                                                boolean resend) {
        OtpDeliveryResult result = shipmentOtpEmailService.sendShipmentOtp(shipment);
        LocalDateTime now = LocalDateTime.now();

        if (result.success()) {
            shipment.setOtpSentAt(now);
            shipment.setOtpLastSentAt(now);
            shipment.setOtpDeliveryStatus(OtpDeliveryStatus.SENT);
            shipment.setUpdatedAt(now);
            Shipment saved = shipmentRepository.save(shipment);
            shipmentOtpAuditService.log(saved,
                    resend ? "OTP_RESENT" : "OTP_SENT",
                    "SUCCESS",
                    reason,
                    "recipient=" + result.recipient() + ",attempts=" + result.attempts(),
                    actorUsername);
            return saved;
        }

        shipment.setOtpSentAt(null);
        shipment.setOtpLastSentAt(null);
        shipment.setOtpDeliveryStatus(OtpDeliveryStatus.FAILED);
        shipment.setUpdatedAt(now);
        Shipment saved = shipmentRepository.save(shipment);
        shipmentOtpAuditService.log(saved,
                resend ? "OTP_RESENT" : "OTP_SENT",
                "FAILED",
                reason,
                "recipient=" + result.recipient() + ",attempts=" + result.attempts() + ",error=" + result.errorMessage(),
                actorUsername);
        throw new OtpDeliveryException("Không thể gửi OTP qua email: " + result.errorMessage());
    }

    private void assertResendCooldown(Shipment shipment, LocalDateTime now) {
        if (shipment.getOtpSentAt() == null) {
            return;
        }
        if (shipment.getOtpSentAt().plusSeconds(OTP_RESEND_COOLDOWN_SECONDS).isAfter(now)) {
            throw new IllegalStateException("Vui lòng chờ ít nhất 60 giây trước khi gửi lại OTP");
        }
    }

    public void deletePendingShipment(Long shipmentId) {
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn giao hàng: " + shipmentId));
        if (shipment.getStatus() != ShipmentStatus.PENDING_ASSIGNMENT) {
            throw new IllegalStateException("Chỉ xóa được đơn giao hàng ở trạng thái chờ phân công");
        }
        shipmentOtpAuditService.log(shipment, "SHIPMENT_DELETED", "SUCCESS", "PENDING_ASSIGNMENT_DELETE", null, null);
        shipmentRepository.delete(shipment);
    }

    public void assignShipperRandom() {
        Shipment shipment = shipmentRepository.findFirstByStatusOrderByCreatedAtAsc(ShipmentStatus.PENDING_ASSIGNMENT)
                .orElseThrow(() -> new IllegalStateException("Không có đơn giao hàng chờ phân công"));

        Double[] targetCoords = resolveTargetCoordinates(shipment);
        Shipper shipper = findAssignableShipper(targetCoords[0], targetCoords[1])
                .orElseThrow(() -> new IllegalStateException("Không có shipper đang hoạt động và sẵn sàng"));

        assignShipmentToShipperInternal(shipment, shipper, true);
    }

    public void markPickedUp(Long shipmentId, String username) {
        Shipment shipment = getShipmentForShipper(shipmentId, username);
        Long previousShipperId = shipment.getShipper() == null ? null : shipment.getShipper().getId();
        ShipmentStatus previousStatus = shipment.getStatus();
        applyStrictTransition(shipment, ShipmentStatus.PICKED_UP, null);
        Shipment savedShipment = shipmentRepository.save(shipment);
        recordStatusTransition(savedShipment, previousStatus, null, "source=shipper-pickup", username);
        reconcileShipperAvailabilityAfterMutation(savedShipment, previousStatus, previousShipperId, true);
        publishLiveStatusEvent(savedShipment);
    }

    public void startDelivering(Long shipmentId, String username) {
        Shipment shipment = getShipmentForShipper(shipmentId, username);
        Long previousShipperId = shipment.getShipper() == null ? null : shipment.getShipper().getId();
        ShipmentStatus previousStatus = shipment.getStatus();
        applyStrictTransition(shipment, ShipmentStatus.DELIVERING, null);
        LocalDateTime now = LocalDateTime.now();
        if (!hasOtpSecret(shipment)) {
            generateNewOtp(shipment, now);
        } else if (shipment.getOtpExpiresAt() == null || !shipment.getOtpExpiresAt().isAfter(now.plusMinutes(30))) {
            shipment.setOtpExpiresAt(now.plusHours(24));
        }
        shipment.setUpdatedAt(now);
        Shipment savedShipment = shipmentRepository.save(shipment);
        recordStatusTransition(savedShipment, previousStatus, null, "source=shipper-start", username);
        reconcileShipperAvailabilityAfterMutation(savedShipment, previousStatus, previousShipperId, true);
        publishLiveStatusEvent(savedShipment);
    }

    public Shipment verifyOtp(Long shipmentId, String otp) {
        if (shipmentId == null) {
            throw new IllegalArgumentException("Thiếu mã đơn giao hàng");
        }
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn giao hàng: " + shipmentId));
        if (isTerminalStatus(shipment.getStatus())) {
            throw new IllegalStateException("Đơn giao hàng đã kết thúc, không thể xác thực OTP");
        }

        LocalDateTime now = LocalDateTime.now();
        validateOtpBeforeCompletion(shipment, otp, now);

        Shipment locked = shipmentRepository.findByIdForUpdate(shipmentId)
                .or(() -> shipmentRepository.findById(shipmentId))
                .orElse(shipment);
        locked.setOtpVerified(true);
        locked.setOtpAttemptCount(0);
        locked.setOtpLockedUntil(null);
        locked.setUpdatedAt(now);
        Shipment saved = shipmentRepository.save(locked);
        shipmentOtpAuditService.log(saved, "OTP_VERIFY", "SUCCESS", "OTP_VERIFIED", null, null);
        return saved;
    }

    public Shipment recordProofOfDelivery(Long shipmentId,
                                          String username,
                                          String proofOfDeliveryUrl,
                                          String deliveryNote) {
        Shipment shipment = getShipmentForShipper(shipmentId, username);
        if (shipment.getStatus() != ShipmentStatus.PICKED_UP
                && shipment.getStatus() != ShipmentStatus.DELIVERING
                && shipment.getStatus() != ShipmentStatus.COMPLETED) {
            throw new IllegalStateException("Chỉ đơn đã lấy hàng, đang giao hoặc đã hoàn tất mới tải ảnh giao hàng");
        }
        String normalizedUrl = requireText(proofOfDeliveryUrl, "Vui lòng tải lên ảnh bằng chứng giao hàng");
        shipment.setProofOfDeliveryUrl(normalizedUrl);
        String trimmedNote = trimToNull(deliveryNote);
        if (trimmedNote != null) {
            shipment.setStatusReason("POD_NOTE:" + trimmedNote);
        }
        shipment.setUpdatedAt(LocalDateTime.now());
        Shipment saved = shipmentRepository.save(shipment);
        if (adminAuditLogService != null) {
            adminAuditLogService.log(
                    username,
                    "SHIPMENT_POD_UPLOADED",
                    "SHIPMENT",
                    saved.getId(),
                    null,
                    Map.of("proofOfDeliveryUrl", normalizedUrl),
                    null
            );
        }
        return saved;
    }

    public void completeDelivery(Long shipmentId, String username, String otp) {
        completeDelivery(shipmentId, username, otp, null, null);
    }

    public void completeDelivery(Long shipmentId,
                                 String username,
                                 String otp,
                                 String proofOfDeliveryUrl,
                                 String deliveryNote) {
        Shipment readableShipment = getReadableShipmentForShipper(shipmentId, username);
        if (readableShipment.getStatus() != ShipmentStatus.DELIVERING) {
            throw new IllegalStateException("Đơn giao hàng không ở trạng thái đang giao");
        }

        LocalDateTime now = LocalDateTime.now();
        validateOtpBeforeCompletion(readableShipment, otp, now);

        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .or(() -> shipmentRepository.findById(shipmentId))
                .orElse(readableShipment);
        validateShipperOwnership(shipment, username);
        if (shipment.getStatus() != ShipmentStatus.DELIVERING) {
            throw new IllegalStateException("Đơn giao hàng không ở trạng thái đang giao");
        }

        Long previousShipperId = shipment.getShipper() == null ? null : shipment.getShipper().getId();
        ShipmentStatus previousStatus = shipment.getStatus();
        applyStrictTransition(shipment, ShipmentStatus.COMPLETED, null);
        shipment.setOtpVerified(true);
        shipment.setAdminOverride(false);
        shipment.setAdminOverrideReason(null);
        String normalizedPod = trimToNull(proofOfDeliveryUrl);
        if (normalizedPod != null) {
            shipment.setProofOfDeliveryUrl(normalizedPod);
        }
        String normalizedNote = trimToNull(deliveryNote);
        if (normalizedNote != null) {
            shipment.setStatusReason("POD_NOTE:" + normalizedNote);
        }
        populateCodCollectedAmountIfApplicable(shipment);
        clearSensitiveOtpAfterCompletion(shipment);
        shipment.setUpdatedAt(now);

        Shipment savedShipment = shipmentRepository.save(shipment);
        settleCodOrderAfterCompletion(savedShipment, username, now);
        recordStatusTransition(savedShipment, previousStatus, normalizedNote, "source=shipper-complete", username);
        shipmentOtpAuditService.log(savedShipment, "OTP_VERIFIED_COMPLETED", "SUCCESS", "SHIPPER_COMPLETE", null, username);
        reconcileShipperAvailabilityAfterMutation(savedShipment, previousStatus, previousShipperId, true);
        publishLiveStatusEvent(savedShipment);
    }

    public void markFailed(Long shipmentId, String username, String note) {
        Shipment shipment = getShipmentForShipper(shipmentId, username);
        Long previousShipperId = shipment.getShipper() == null ? null : shipment.getShipper().getId();
        ShipmentStatus previousStatus = shipment.getStatus();
        LocalDateTime now = LocalDateTime.now();
        applyStrictTransition(shipment, ShipmentStatus.FAILED, note);
        invalidateOtp(shipment, now, OtpDeliveryStatus.FAILED);
        Shipment savedShipment = shipmentRepository.save(shipment);
        releasePendingCodOrderOnTerminalFailure(savedShipment, username, note, now);
        recordStatusTransition(savedShipment, previousStatus, trimToNull(note), "source=shipper-fail", username);
        reconcileShipperAvailabilityAfterMutation(savedShipment, previousStatus, previousShipperId, true);
        publishLiveStatusEvent(savedShipment);
    }

    private Shipment getShipmentForShipper(Long shipmentId, String username) {
        if (shipmentId == null) {
            throw new IllegalArgumentException("Thiếu mã đơn giao hàng");
        }
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn giao hàng: " + shipmentId));
        validateShipperOwnership(shipment, username);
        return shipment;
    }

    private Shipment getReadableShipmentForShipper(Long shipmentId, String username) {
        if (shipmentId == null) {
            throw new IllegalArgumentException("Thiếu mã đơn giao hàng");
        }
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn giao hàng: " + shipmentId));
        validateShipperOwnership(shipment, username);
        return shipment;
    }

    private void validateShipperOwnership(Shipment shipment, String username) {
        if (shipment.getShipper() == null || shipment.getShipper().getUser() == null) {
            throw new IllegalStateException("Đơn giao hàng chưa được gán shipper");
        }
        String ownerUsername = shipment.getShipper().getUser().getUsername();
        if (!ownerUsername.equals(username)) {
            throw new IllegalStateException("Đơn giao hàng không thuộc shipper hiện tại");
        }
    }

    private void applyStrictTransition(Shipment shipment, ShipmentStatus targetStatus, String failureNote) {
        ShipmentStatus currentStatus = shipment.getStatus();
        if (currentStatus == targetStatus) {
            return;
        }

        boolean validTransition;
        switch (currentStatus) {
            case PENDING_ASSIGNMENT -> validTransition = targetStatus == ShipmentStatus.ASSIGNED || targetStatus == ShipmentStatus.CANCELLED;
            case ASSIGNED -> validTransition = targetStatus == ShipmentStatus.PICKED_UP || targetStatus == ShipmentStatus.FAILED || targetStatus == ShipmentStatus.CANCELLED;
            case PICKED_UP -> validTransition = targetStatus == ShipmentStatus.DELIVERING || targetStatus == ShipmentStatus.FAILED || targetStatus == ShipmentStatus.CANCELLED;
            case DELIVERING -> validTransition = targetStatus == ShipmentStatus.COMPLETED || targetStatus == ShipmentStatus.FAILED || targetStatus == ShipmentStatus.CANCELLED;
            default -> validTransition = false;
        }

        if (!validTransition) {
            throw new IllegalStateException("Không thể chuyển trạng thái từ " + currentStatus + " sang " + targetStatus);
        }

        if (targetStatus == ShipmentStatus.ASSIGNED && shipment.getShipper() == null) {
            throw new IllegalStateException("Đơn đã phân công phải có shipper");
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
            if (shipment.getDeliveredAt() == null) {
                shipment.setDeliveredAt(now);
            }
            shipment.setFailedAt(null);
            shipment.setStatusReason(null);
        }
        if (targetStatus == ShipmentStatus.ASSIGNED) {
            shipment.setAssignedAt(now);
        }
        if (targetStatus == ShipmentStatus.FAILED) {
            String normalizedFailureNote = trimToNull(failureNote);
            shipment.setFailureNote(normalizedFailureNote);
            shipment.setStatusReason(normalizedFailureNote);
            shipment.setFailedAt(now);
            shipment.setLastDeliveryAttemptAt(now);
            int currentAttemptCount = shipment.getDeliveryAttemptCount() == null ? 0 : shipment.getDeliveryAttemptCount();
            shipment.setDeliveryAttemptCount(currentAttemptCount + 1);
        } else if (targetStatus == ShipmentStatus.CANCELLED) {
            String normalizedCancelReason = trimToNull(failureNote);
            shipment.setFailureNote(null);
            shipment.setFailureCode(null);
            if (normalizedCancelReason != null) {
                shipment.setStatusReason(normalizedCancelReason);
            }
        } else {
            shipment.setFailureNote(null);
            shipment.setFailureCode(null);
            if (targetStatus != ShipmentStatus.ASSIGNED) {
                shipment.setStatusReason(null);
            }
        }

        shipment.setStatus(targetStatus);
    }

    private void ensureOrderReadyForShipment(Order order) {
        boolean isPaid = order.getStatus() == OrderStatus.PAID;
        boolean isCod = order.getPaymentMethod() == PaymentMethod.COD && (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.PAID);
        if (!isPaid && !isCod) {
            throw new IllegalStateException("Chỉ đơn đã thanh toán hoặc đơn COD hợp lệ mới được tạo đơn giao hàng");
        }
    }

    private Shipment createShipmentForOrderWithAssignedShipper(Order order,
                                                               Shipper shipper,
                                                               boolean triggerDispatch) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Đơn hàng không hợp lệ");
        }
        if (shipper == null || shipper.getId() == null) {
            throw new IllegalArgumentException("Thiếu thông tin shipper");
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
        shipment.setAssignedAt(LocalDateTime.now());
        shipment.setStatusReason("AUTO_CREATE_ASSIGNED");
        shipment.setShippingName(requireTextOrFallback(null, order.getShippingFullName(), "Vui lòng nhập tên người nhận"));
        shipment.setShippingPhone(requireTextOrFallback(null, order.getShippingPhone(), "Vui lòng nhập số điện thoại người nhận"));
        shipment.setShippingEmail(resolveShippingEmail(order));
        shipment.setShippingAddress(requireTextOrFallback(null, order.getShippingAddress(), "Vui lòng nhập địa chỉ người nhận"));
        copyCoordinatesFromOrder(order, shipment);
        generateNewOtp(shipment, LocalDateTime.now());

        Shipment savedShipment = shipmentRepository.save(shipment);
        recordStatusTransition(savedShipment, null, "AUTO_CREATE_ASSIGNED", "source=auto-create", "system-dispatch");
        shipmentOtpAuditService.log(savedShipment, "OTP_GENERATED", "SUCCESS", "AUTO_CREATE_ASSIGNED", null, null);
        markShipperBusy(shipper);
        publishLiveStatusEvent(savedShipment);
        return savedShipment;
    }

    private Shipment createShipmentForOrderWithoutShipper(Order order, boolean triggerDispatch) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Đơn hàng không hợp lệ");
        }
        ensureOrderReadyForShipment(order);

        Optional<Shipment> existingShipment = shipmentRepository.findByOrderId(order.getId());
        if (existingShipment.isPresent()) {
            return existingShipment.get();
        }

        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setStatus(ShipmentStatus.PENDING_ASSIGNMENT);
        shipment.setStatusReason("AUTO_CREATE_PENDING");
        shipment.setShippingName(requireTextOrFallback(null, order.getShippingFullName(), "Vui lòng nhập tên người nhận"));
        shipment.setShippingPhone(requireTextOrFallback(null, order.getShippingPhone(), "Vui lòng nhập số điện thoại người nhận"));
        shipment.setShippingEmail(resolveShippingEmail(order));
        shipment.setShippingAddress(requireTextOrFallback(null, order.getShippingAddress(), "Vui lòng nhập địa chỉ người nhận"));
        copyCoordinatesFromOrder(order, shipment);
        generateNewOtp(shipment, LocalDateTime.now());

        Shipment savedShipment = shipmentRepository.save(shipment);
        recordStatusTransition(savedShipment, null, "AUTO_CREATE_PENDING", "source=auto-create", "system-dispatch");
        shipmentOtpAuditService.log(savedShipment, "OTP_GENERATED", "SUCCESS", "AUTO_CREATE_PENDING", null, null);
        if (triggerDispatch) {
            dispatchAutoShipmentQueue();
        }
        return savedShipment;
    }

    private Shipment assignShipmentToShipperInternal(Shipment shipment,
                                                     Shipper shipper,
                                                     boolean triggerDispatch) {
        if (shipment == null || shipment.getId() == null) {
            throw new IllegalArgumentException("Đơn giao hàng không hợp lệ");
        }
        if (shipper == null || shipper.getId() == null) {
            throw new IllegalArgumentException("Thiếu thông tin shipper");
        }
        if (shipment.getStatus() != ShipmentStatus.PENDING_ASSIGNMENT && shipment.getStatus() != ShipmentStatus.ASSIGNED) {
            throw new IllegalStateException("Chỉ đơn ở trạng thái chờ phân công hoặc đã phân công mới được gán shipper");
        }

        Long previousShipperId = shipment.getShipper() == null ? null : shipment.getShipper().getId();
        ShipmentStatus previousStatus = shipment.getStatus();
        shipment.setShipper(shipper);
        shipment.setStatus(ShipmentStatus.ASSIGNED);
        shipment.setAssignedAt(LocalDateTime.now());
        shipment.setStatusReason("AUTO_DISPATCH_ASSIGN");
        shipment.setUpdatedAt(LocalDateTime.now());
        Shipment savedShipment = shipmentRepository.save(shipment);
        recordStatusTransition(savedShipment, previousStatus, "AUTO_DISPATCH_ASSIGN", "source=auto-dispatch", "system-dispatch");
        reconcileShipperAvailabilityAfterMutation(savedShipment, previousStatus, previousShipperId, triggerDispatch);
        publishLiveStatusEvent(savedShipment);
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
        return status == ShipmentStatus.COMPLETED || status == ShipmentStatus.FAILED || status == ShipmentStatus.CANCELLED;
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

        long inProgressShipmentCount = shipmentRepository.countByShipperIdAndStatusIn(lockedShipper.getId(), IN_PROGRESS_STATUSES);
        int normalizedCapacity = normalizeCapacity(lockedShipper);
        boolean shouldBeAvailable = inProgressShipmentCount < normalizedCapacity;

        LocalDateTime now = LocalDateTime.now();
        lockedShipper.setActiveShipmentCount(toSafeInt(inProgressShipmentCount));
        lockedShipper.setIsAvailable(shouldBeAvailable);
        if (inProgressShipmentCount > 0) {
            lockedShipper.setLastAssignmentAt(now);
        }
        lockedShipper.setUpdatedAt(now);
        shipperRepository.save(lockedShipper);
    }

    private Optional<Shipper> findAssignableShipper() {
        return findAssignableShipper(null, null);
    }

    private Optional<Shipper> findAssignableShipper(Double targetLat, Double targetLng) {
        List<Shipper> activeCandidates = shipperRepository.findByStatusOrderByIdAsc(ShipperStatus.ACTIVE);
        if (activeCandidates == null || activeCandidates.isEmpty()) {
            return Optional.empty();
        }

        List<Shipper> sortedCandidates = sortShippersForDispatch(activeCandidates, targetLat, targetLng);
        Set<Long> checkedShipperIds = new HashSet<>();

        for (Shipper candidate : sortedCandidates) {
            if (candidate == null || candidate.getId() == null || checkedShipperIds.contains(candidate.getId())) {
                continue;
            }
            checkedShipperIds.add(candidate.getId());
            Optional<Shipper> validatedCandidate = lockAndValidateAssignableShipper(candidate.getId());
            if (validatedCandidate.isPresent()) {
                return validatedCandidate;
            }
        }

        return Optional.empty();
    }

    public List<Shipper> sortShippersForDispatch(List<Shipper> candidates, Double targetLat, Double targetLng) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        boolean hasTarget = isFiniteCoord(targetLat) && isFiniteCoord(targetLng);
        List<Shipper> mutable = new ArrayList<>(candidates);
        mutable.sort(Comparator.comparing((Shipper s) -> s, (a, b) -> {
            if (hasTarget) {
                Double distA = isFiniteCoord(a.getCurrentLatitude()) && isFiniteCoord(a.getCurrentLongitude())
                        ? shippingFeeService.calculateDistanceKm(targetLat, targetLng, a.getCurrentLatitude(), a.getCurrentLongitude())
                        : null;
                Double distB = isFiniteCoord(b.getCurrentLatitude()) && isFiniteCoord(b.getCurrentLongitude())
                        ? shippingFeeService.calculateDistanceKm(targetLat, targetLng, b.getCurrentLatitude(), b.getCurrentLongitude())
                        : null;
                if (distA != null && distB != null) {
                    int distCmp = Double.compare(distA, distB);
                    if (distCmp != 0) {
                        return distCmp;
                    }
                } else if (distA != null) {
                    return -1;
                } else if (distB != null) {
                    return 1;
                }
            }
            double loadA = safeActiveCount(a) / (double) normalizeCapacity(a);
            double loadB = safeActiveCount(b) / (double) normalizeCapacity(b);
            int loadCmp = Double.compare(loadA, loadB);
            if (loadCmp != 0) {
                return loadCmp;
            }
            int activeCmp = Integer.compare(safeActiveCount(a), safeActiveCount(b));
            if (activeCmp != 0) {
                return activeCmp;
            }
            boolean availA = Boolean.TRUE.equals(a.getIsAvailable());
            boolean availB = Boolean.TRUE.equals(b.getIsAvailable());
            if (availA != availB) {
                return availA ? -1 : 1;
            }
            LocalDateTime lastA = a.getLastAssignmentAt();
            LocalDateTime lastB = b.getLastAssignmentAt();
            if (lastA == null && lastB != null) {
                return -1;
            }
            if (lastA != null && lastB == null) {
                return 1;
            }
            if (lastA != null) {
                int timeCmp = lastA.compareTo(lastB);
                if (timeCmp != 0) {
                    return timeCmp;
                }
            }
            long idA = a.getId() == null ? Long.MAX_VALUE : a.getId();
            long idB = b.getId() == null ? Long.MAX_VALUE : b.getId();
            return Long.compare(idA, idB);
        }));
        return mutable;
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> getActiveLoadByShipperId(List<Shipper> shippers) {
        if (shippers == null || shippers.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> loadMap = new LinkedHashMap<>();
        for (Shipper shipper : shippers) {
            if (shipper != null && shipper.getId() != null) {
                long count = shipmentRepository.countByShipperIdAndStatusIn(shipper.getId(), IN_PROGRESS_STATUSES);
                loadMap.put(shipper.getId(), count);
            }
        }
        return loadMap;
    }

    private Optional<Shipper> lockAndValidateAssignableShipper(Long shipperId) {
        if (shipperId == null) {
            return Optional.empty();
        }

        Shipper lockedShipper = shipperRepository.findByIdForUpdate(shipperId).orElse(null);
        if (lockedShipper == null
                || lockedShipper.getStatus() != ShipperStatus.ACTIVE) {
            return Optional.empty();
        }

        long inProgressShipmentCount = shipmentRepository.countByShipperIdAndStatusIn(shipperId, IN_PROGRESS_STATUSES);
        int normalizedCapacity = normalizeCapacity(lockedShipper);
        boolean canAssign = inProgressShipmentCount < normalizedCapacity;

        LocalDateTime now = LocalDateTime.now();
        lockedShipper.setActiveShipmentCount(toSafeInt(inProgressShipmentCount));
        lockedShipper.setIsAvailable(canAssign);
        if (inProgressShipmentCount > 0) {
            lockedShipper.setLastAssignmentAt(now);
        }
        lockedShipper.setUpdatedAt(now);
        shipperRepository.save(lockedShipper);

        if (!canAssign) {
            return Optional.empty();
        }
        return Optional.of(lockedShipper);
    }

    private boolean refreshShipperAvailability(Long shipperId) {
        if (shipperId == null) {
            return false;
        }
        Shipper shipper = shipperRepository.findByIdForUpdate(shipperId)
                .orElse(null);
        if (shipper == null) {
            return false;
        }

        long inProgressShipmentCount = shipmentRepository.countByShipperIdAndStatusIn(shipperId, IN_PROGRESS_STATUSES);
        int normalizedCapacity = normalizeCapacity(shipper);
        boolean shouldBeAvailable = shipper.getStatus() == ShipperStatus.ACTIVE
                && inProgressShipmentCount < normalizedCapacity;

        shipper.setActiveShipmentCount(toSafeInt(inProgressShipmentCount));
        shipper.setIsAvailable(shouldBeAvailable);
        shipper.setUpdatedAt(LocalDateTime.now());
        shipperRepository.save(shipper);
        return shouldBeAvailable;
    }

    private void setShipperUnavailable(Long shipperId) {
        if (shipperId == null) {
            return;
        }
        shipperRepository.findByIdForUpdate(shipperId).ifPresent(shipper -> {
            shipper.setIsAvailable(false);
            shipper.setUpdatedAt(LocalDateTime.now());
            shipperRepository.save(shipper);
        });
    }

    private Shipper getActiveShipper(Long shipperId) {
        if (shipperId == null) {
            throw new IllegalArgumentException("Thiếu mã shipper");
        }

        Shipper shipper = shipperRepository.findById(shipperId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy shipper: " + shipperId));
        if (shipper.getStatus() != ShipperStatus.ACTIVE) {
            throw new IllegalStateException("Chỉ shipper đang hoạt động mới được nhận đơn");
        }
        return shipper;
    }

    private void copyCoordinatesFromOrder(Order order, Shipment shipment) {
        if (order == null || shipment == null) {
            return;
        }

        Double latitude = order.getShippingLatitude();
        Double longitude = order.getShippingLongitude();
        if (latitude == null || longitude == null) {
            Optional<AddressTextUtils.Coordinates> legacyCoordinates = AddressTextUtils.extractLegacyCoordinates(order.getShippingAddressRaw());
            if (legacyCoordinates.isPresent()) {
                latitude = legacyCoordinates.get().latitude();
                longitude = legacyCoordinates.get().longitude();
            }
        }

        shipment.setShippingLatitude(latitude);
        shipment.setShippingLongitude(longitude);
    }

    private String resolveShippingEmail(Order order) {
        if (order == null) {
            return null;
        }
        String checkoutEmail = trimToNull(order.getShippingEmail());
        if (checkoutEmail != null) {
            return checkoutEmail;
        }
        if (order.getUser() == null) {
            return null;
        }
        return trimToNull(order.getUser().getEmail());
    }

    private void generateNewOtp(Shipment shipment, LocalDateTime now) {
        String plainOtp = generateOtp();
        shipment.setOtpEncrypted(shipmentOtpCryptoService.encrypt(plainOtp));
        shipment.setOtpCode(null);
        shipment.setOtpVerified(false);
        shipment.setAdminOverride(false);
        shipment.setAdminOverrideReason(null);
        shipment.setOtpCreatedAt(now);
        shipment.setOtpExpiresAt(now.plusMinutes(OTP_TTL_MINUTES));
        shipment.setOtpAttemptCount(0);
        shipment.setOtpLockedUntil(null);
        shipment.setOtpSentAt(null);
        shipment.setOtpLastSentAt(null);
        shipment.setOtpDeliveryStatus(OtpDeliveryStatus.PENDING);
        shipment.setOtpUserId(resolveOtpUserId(shipment));
    }

    private void recordStatusTransition(Shipment shipment,
                                        ShipmentStatus previousStatus,
                                        String reason,
                                        String metadata,
                                        String actorUsername) {
        if (shipment == null || shipment.getStatus() == null) {
            return;
        }
        if (previousStatus == shipment.getStatus()) {
            return;
        }

        shipmentStatusHistoryService.logTransition(
                shipment,
                previousStatus,
                shipment.getStatus(),
                reason,
                metadata,
                actorUsername
        );
    }

    private void validateOtpBeforeCompletion(Shipment shipment, String otp, LocalDateTime now) {
        String normalizedOtp = normalizeOtp(otp);
        if (normalizedOtp.length() != 6) {
            throw new IllegalArgumentException("OTP phải gồm đúng 6 chữ số");
        }

        if (shipment.getOtpLockedUntil() != null && shipment.getOtpLockedUntil().isAfter(now)) {
            throw new IllegalStateException("OTP đang bị khóa tạm thời đến " + shipment.getOtpLockedUntil());
        }

        if (shipment.getOtpExpiresAt() == null || shipment.getOtpExpiresAt().isBefore(now)) {
            throw new IllegalStateException("OTP đã hết hạn, vui lòng yêu cầu gửi lại mã mới");
        }

        Long expectedOtpUserId = resolveOtpUserId(shipment);
        if (shipment.getOtpUserId() != null && expectedOtpUserId != null && !shipment.getOtpUserId().equals(expectedOtpUserId)) {
            throw new IllegalStateException("OTP user binding không hợp lệ");
        }

        String storedOtp = resolveStoredOtp(shipment);
        if (storedOtp.length() != 6 || !storedOtp.equals(normalizedOtp)) {
            if (shipmentOtpStateService != null && shipment.getId() != null) {
                Shipment updatedState = shipmentOtpStateService.recordFailedOtpAttempt(shipment.getId());
                if (updatedState != null) {
                    shipment.setOtpAttemptCount(updatedState.getOtpAttemptCount());
                    shipment.setOtpLockedUntil(updatedState.getOtpLockedUntil());
                } else {
                    registerFailedOtpAttempt(shipment, now);
                }
            } else {
                registerFailedOtpAttempt(shipment, now);
                shipmentRepository.save(shipment);
                shipmentOtpAuditService.log(shipment, "OTP_VERIFY", "FAILED", "OTP_MISMATCH", null, null);
            }
            if (shipment.getOtpLockedUntil() != null && shipment.getOtpLockedUntil().isAfter(now)) {
                throw new IllegalStateException("Nhập sai OTP quá số lần cho phép. Mã OTP đã bị khóa 15 phút");
            }
            throw new IllegalArgumentException("OTP không đúng");
        }

        shipment.setOtpAttemptCount(0);
        shipment.setOtpLockedUntil(null);
    }

    private void registerFailedOtpAttempt(Shipment shipment, LocalDateTime now) {
        int currentAttempts = shipment.getOtpAttemptCount() == null ? 0 : shipment.getOtpAttemptCount();
        int nextAttempts = currentAttempts + 1;
        shipment.setOtpAttemptCount(nextAttempts);
        shipment.setUpdatedAt(now);
        if (nextAttempts >= OTP_MAX_ATTEMPTS) {
            shipment.setOtpLockedUntil(now.plusMinutes(OTP_LOCK_MINUTES));
        }
    }

    private void clearSensitiveOtpAfterCompletion(Shipment shipment) {
        shipment.setOtpCode(null);
        shipment.setOtpEncrypted(null);
        shipment.setOtpCreatedAt(null);
        shipment.setOtpExpiresAt(null);
        shipment.setOtpAttemptCount(0);
        shipment.setOtpLockedUntil(null);
        shipment.setOtpSentAt(null);
        shipment.setOtpLastSentAt(null);
        shipment.setOtpDeliveryStatus(OtpDeliveryStatus.PENDING);
    }

    private void invalidateOtp(Shipment shipment, LocalDateTime now, OtpDeliveryStatus status) {
        shipment.setOtpCode(null);
        shipment.setOtpEncrypted(null);
        shipment.setOtpCreatedAt(null);
        shipment.setOtpExpiresAt(null);
        shipment.setOtpAttemptCount(0);
        shipment.setOtpLockedUntil(null);
        shipment.setOtpSentAt(null);
        shipment.setOtpLastSentAt(null);
        shipment.setOtpDeliveryStatus(status == null ? OtpDeliveryStatus.PENDING : status);
        shipment.setOtpVerified(false);
        shipment.setUpdatedAt(now);
    }

    private int normalizeCapacity(Shipper shipper) {
        if (shipper == null || shipper.getMaxConcurrentShipments() == null || shipper.getMaxConcurrentShipments() < 1) {
            return 1;
        }
        return shipper.getMaxConcurrentShipments();
    }

    private int toSafeInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private void applyAdminOverrideState(Shipment shipment, String overrideReason) {
        shipment.setOtpVerified(false);
        shipment.setAdminOverride(true);
        shipment.setAdminOverrideReason(overrideReason);
    }

    private String resolveAdminOverrideReason(String overrideReason) {
        String trimmed = trimToNull(overrideReason);
        if (trimmed != null) {
            return trimmed;
        }
        return "Hoàn tất thủ công bởi quản trị viên không cần OTP";
    }

    private Long resolveOtpUserId(Shipment shipment) {
        if (shipment == null || shipment.getOrder() == null || shipment.getOrder().getUser() == null) {
            return null;
        }
        return shipment.getOrder().getUser().getId();
    }

    private boolean matchesKeyword(Shipment shipment, String normalizedKeyword) {
        if (normalizedKeyword == null) {
            return true;
        }

        return containsIgnoreCase(shipment.getShippingName(), normalizedKeyword)
                || containsIgnoreCase(shipment.getShippingPhone(), normalizedKeyword)
                || containsIgnoreCase(shipment.getShippingAddressRaw(), normalizedKeyword)
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
        return AddressTextUtils.stripLegacyGpsSuffix(value.trim());
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

    private String normalizeOtp(String otp) {
        if (otp == null) {
            return "";
        }
        return otp.replaceAll("\\D", "");
    }

    private boolean hasOtpSecret(Shipment shipment) {
        if (shipment == null) {
            return false;
        }
        return (shipment.getOtpEncrypted() != null && !shipment.getOtpEncrypted().isBlank())
                || (shipment.getOtpCode() != null && !shipment.getOtpCode().isBlank());
    }

    private String resolveStoredOtp(Shipment shipment) {
        if (shipment == null) {
            return "";
        }
        if (shipment.getOtpEncrypted() != null && !shipment.getOtpEncrypted().isBlank()) {
            return shipmentOtpCryptoService.decrypt(shipment.getOtpEncrypted())
                    .map(this::normalizeOtp)
                    .orElse("");
        }
        return normalizeOtp(shipment.getOtpCode());
    }

    private void populateCodCollectedAmountIfApplicable(Shipment shipment) {
        if (shipment == null || shipment.getOrder() == null) {
            return;
        }
        Order order = shipment.getOrder();
        if (order.getPaymentMethod() == PaymentMethod.COD && shipment.getCodCollectedAmount() == null) {
            BigDecimal expected = order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO;
            shipment.setCodCollectedAmount(expected);
        }
    }

    private void settleCodOrderAfterCompletion(Shipment savedShipment, String actorUsername, LocalDateTime now) {
        if (savedShipment == null || savedShipment.getOrder() == null) {
            return;
        }
        Order order = savedShipment.getOrder();
        if (order.getPaymentMethod() != PaymentMethod.COD || order.getPaymentStatus() == PaymentStatus.SUCCESS) {
            return;
        }
        if (codSettlementPort != null && order.getId() != null) {
            String actor = trimToNull(actorUsername);
            codSettlementPort.settleCodOrder(order.getId(), actor != null ? actor : "system");
            return;
        }
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(now);
        order.setUpdatedAt(now);
        orderRepository.save(order);
    }

    private void releasePendingCodOrderOnTerminalFailure(Shipment savedShipment,
                                                         String actorUsername,
                                                         String failureReason,
                                                         LocalDateTime now) {
        if (savedShipment == null || savedShipment.getOrder() == null) {
            return;
        }
        Order order = savedShipment.getOrder();
        if (order.getPaymentMethod() != PaymentMethod.COD
                || order.getStatus() != OrderStatus.PENDING
                || order.getPaymentStatus() != PaymentStatus.PENDING) {
            return;
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.CANCELLED);
        order.setUpdatedAt(now);
        if (order.getPayments() != null) {
            for (Payment payment : order.getPayments()) {
                if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
                    payment.setStatus(PaymentStatus.CANCELLED);
                    payment.setUpdatedAt(now);
                }
            }
        }
        orderRepository.save(order);
        if (inventoryService != null) {
            inventoryService.releaseOrderReservation(order);
        }
        if (voucherPricingPort != null && order.getId() != null) {
            voucherPricingPort.releaseForOrder(order.getId());
        }
        if (applicationEventPublisher != null && order.getId() != null) {
            String trimmedReason = trimToNull(failureReason);
            applicationEventPublisher.publishEvent(new OrderStatusUpdatedEvent(
                    this,
                    order.getId(),
                    OrderStatus.CANCELLED.name(),
                    "Đơn COD #" + order.getId() + " đã hủy và hoàn kho do giao hàng thất bại"
                            + (trimmedReason != null ? " (" + trimmedReason + ")" : "")
            ));
        }
    }

    private void publishLiveStatusEvent(Shipment shipment) {
        if (applicationEventPublisher == null || shipment == null || shipment.getId() == null) {
            return;
        }
        Long orderId = shipment.getOrder() == null ? null : shipment.getOrder().getId();
        String statusName = shipment.getStatus() == null ? null : shipment.getStatus().name();
        Shipper shipper = shipment.getShipper();
        Long shipperId = shipper != null ? shipper.getId() : null;
        String shipperName = shipper != null ? shipper.getName() : null;
        String shipperPhone = shipper != null ? shipper.getPhone() : null;
        String vehiclePlate = shipper != null ? shipper.getVehicleType() : null;
        applicationEventPublisher.publishEvent(
                new ShipmentLiveStatusEvent(this, shipment.getId(), orderId, statusName, shipperId, shipperName, shipperPhone, vehiclePlate)
        );
    }

    private Double[] resolveTargetCoordinates(Order order) {
        if (order == null) {
            return new Double[]{null, null};
        }
        Double lat = order.getShippingLatitude();
        Double lng = order.getShippingLongitude();
        if (!isFiniteCoord(lat) || !isFiniteCoord(lng)) {
            Optional<AddressTextUtils.Coordinates> legacy = AddressTextUtils.extractLegacyCoordinates(order.getShippingAddressRaw());
            if (legacy.isPresent()) {
                lat = legacy.get().latitude();
                lng = legacy.get().longitude();
            }
        }
        if ((!isFiniteCoord(lat) || !isFiniteCoord(lng)) && order.getAllocatedWarehouse() != null) {
            Warehouse warehouse = order.getAllocatedWarehouse();
            lat = warehouse.getLatitude();
            lng = warehouse.getLongitude();
        }
        if (!isFiniteCoord(lat) || !isFiniteCoord(lng)) {
            return new Double[]{null, null};
        }
        return new Double[]{lat, lng};
    }

    private Double[] resolveTargetCoordinates(Shipment shipment) {
        if (shipment == null) {
            return new Double[]{null, null};
        }
        if (isFiniteCoord(shipment.getShippingLatitude()) && isFiniteCoord(shipment.getShippingLongitude())) {
            return new Double[]{shipment.getShippingLatitude(), shipment.getShippingLongitude()};
        }
        return resolveTargetCoordinates(shipment.getOrder());
    }

    private boolean isFiniteCoord(Double value) {
        return value != null && Double.isFinite(value);
    }

    private int safeActiveCount(Shipper shipper) {
        if (shipper == null || shipper.getActiveShipmentCount() == null || shipper.getActiveShipmentCount() < 0) {
            return 0;
        }
        return shipper.getActiveShipmentCount();
    }
}

