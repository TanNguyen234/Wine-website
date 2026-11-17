package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.CodBatchStatus;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.entity.Shipper;
import com.strongwine.strongwine.entity.ShipperCodBatch;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.event.AdminAlertEvent;
import com.strongwine.strongwine.repository.ShipmentRepository;
import com.strongwine.strongwine.repository.ShipperCodBatchRepository;
import com.strongwine.strongwine.repository.ShipperRepository;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.port.CodSettlementPort;
import com.strongwine.strongwine.service.realtime.SseNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages Shipper COD settlement batches and admin verification/dispute reconciliation.
 */
@Service
@Transactional
public class ShipperCodBatchService {

    private static final DateTimeFormatter BATCH_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ShipperCodBatchRepository shipperCodBatchRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipperRepository shipperRepository;
    private final UserRepository userRepository;
    private final CodSettlementPort codSettlementPort;
    private final AdminAuditLogService adminAuditLogService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private SseNotificationService sseNotificationService;

    @Autowired
    public ShipperCodBatchService(ShipperCodBatchRepository shipperCodBatchRepository,
                                  ShipmentRepository shipmentRepository,
                                  ShipperRepository shipperRepository,
                                  UserRepository userRepository,
                                  @Lazy CodSettlementPort codSettlementPort,
                                  @Autowired(required = false) AdminAuditLogService adminAuditLogService,
                                  @Autowired(required = false) ApplicationEventPublisher applicationEventPublisher) {
        this.shipperCodBatchRepository = shipperCodBatchRepository;
        this.shipmentRepository = shipmentRepository;
        this.shipperRepository = shipperRepository;
        this.userRepository = userRepository;
        this.codSettlementPort = codSettlementPort;
        this.adminAuditLogService = adminAuditLogService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Autowired(required = false)
    public void setSseNotificationService(SseNotificationService sseNotificationService) {
        this.sseNotificationService = sseNotificationService;
    }

    @Transactional(readOnly = true)
    public List<Shipment> getUnbatchedCompletedCodShipments(Long shipperId) {
        if (shipperId == null) {
            return List.of();
        }
        return shipmentRepository.findByShipperIdAndStatusAndCodBatchIsNullOrderByDeliveredAtAsc(
                        shipperId, ShipmentStatus.COMPLETED)
                .stream()
                .filter(s -> s.getCodBatch() == null)
                .filter(s -> s.getOrder() != null && s.getOrder().getPaymentMethod() == PaymentMethod.COD)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Shipment> getUnbatchedCompletedCodShipmentsByUsername(String username) {
        return resolveShipperByUsername(username)
                .map(shipper -> getUnbatchedCompletedCodShipments(shipper.getId()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<ShipperCodBatch> getBatchesForShipper(Long shipperId) {
        if (shipperId == null) {
            return List.of();
        }
        return shipperCodBatchRepository.findByShipperIdOrderBySubmittedAtDesc(shipperId);
    }

    @Transactional(readOnly = true)
    public List<ShipperCodBatch> getBatchesForShipperUsername(String username) {
        return resolveShipperByUsername(username)
                .map(shipper -> getBatchesForShipper(shipper.getId()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<ShipperCodBatch> getAllBatches() {
        return shipperCodBatchRepository.findAllByOrderBySubmittedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<ShipperCodBatch> getBatchesByStatus(CodBatchStatus status) {
        if (status == null) {
            return getAllBatches();
        }
        return shipperCodBatchRepository.findByStatusOrderBySubmittedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public ShipperCodBatch getBatchById(Long batchId) {
        if (batchId == null) {
            throw new IllegalArgumentException("Thiếu mã bảng kê COD");
        }
        return shipperCodBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bảng kê COD: " + batchId));
    }

    @Transactional(readOnly = true)
    public List<Shipment> getShipmentsInBatch(Long batchId) {
        if (batchId == null) {
            return List.of();
        }
        return shipmentRepository.findByCodBatchId(batchId);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateCodAmountForShipment(Shipment shipment) {
        if (shipment == null) {
            return BigDecimal.ZERO;
        }
        if (shipment.getCodCollectedAmount() != null) {
            return shipment.getCodCollectedAmount();
        }
        Order order = shipment.getOrder();
        if (order != null && order.getTotalPrice() != null) {
            return order.getTotalPrice();
        }
        return BigDecimal.ZERO;
    }

    public ShipperCodBatch createBatchForShipperByUsername(String username,
                                                           List<Long> shipmentIds,
                                                           BigDecimal submittedAmount,
                                                           String note) {
        Shipper shipper = resolveShipperByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy hồ sơ shipper cho tài khoản: " + username));
        return createBatchForShipper(shipper.getId(), shipmentIds, submittedAmount, note, username);
    }

    public ShipperCodBatch createBatchForShipper(Long shipperId,
                                                 List<Long> shipmentIds,
                                                 BigDecimal submittedAmount,
                                                 String note,
                                                 String actorUsername) {
        if (shipperId == null) {
            throw new IllegalArgumentException("Thiếu mã shipper");
        }
        Shipper shipper = shipperRepository.findById(shipperId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy shipper: " + shipperId));

        List<Shipment> eligibleShipments = resolveAndValidateEligibleShipments(shipperId, shipmentIds);
        if (eligibleShipments.isEmpty()) {
            throw new IllegalStateException("Không có vận đơn COD hợp lệ để tạo bảng kê");
        }

        BigDecimal totalExpected = BigDecimal.ZERO;
        for (Shipment shipment : eligibleShipments) {
            BigDecimal amount = calculateCodAmountForShipment(shipment);
            if (shipment.getCodCollectedAmount() == null) {
                shipment.setCodCollectedAmount(amount);
            }
            totalExpected = totalExpected.add(amount);
        }

        BigDecimal normalizedSubmitted = submittedAmount != null ? submittedAmount : totalExpected;
        if (normalizedSubmitted.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Số tiền nộp không hợp lệ");
        }

        LocalDateTime now = LocalDateTime.now();
        ShipperCodBatch batch = new ShipperCodBatch();
        batch.setShipper(shipper);
        batch.setBatchCode(generateBatchCode(now));
        batch.setTotalExpectedAmount(totalExpected);
        batch.setTotalSubmittedAmount(normalizedSubmitted);
        batch.setOrderCount(eligibleShipments.size());
        batch.setStatus(CodBatchStatus.PENDING_SETTLEMENT);
        batch.setNote(trimToNull(note));
        batch.setSubmittedAt(now);

        ShipperCodBatch savedBatch = shipperCodBatchRepository.save(batch);
        for (Shipment shipment : eligibleShipments) {
            shipment.setCodBatch(savedBatch);
            shipment.setUpdatedAt(now);
        }
        shipmentRepository.saveAll(eligibleShipments);

        if (adminAuditLogService != null) {
            Map<String, Object> afterState = new LinkedHashMap<>();
            afterState.put("batchCode", savedBatch.getBatchCode());
            afterState.put("shipperId", shipperId);
            afterState.put("orderCount", savedBatch.getOrderCount());
            afterState.put("totalExpectedAmount", savedBatch.getTotalExpectedAmount());
            afterState.put("totalSubmittedAmount", savedBatch.getTotalSubmittedAmount());
            afterState.put("status", savedBatch.getStatus().name());
            adminAuditLogService.log(resolveActor(actorUsername, shipper), "COD_BATCH_CREATED", "COD_BATCH",
                    savedBatch.getId(), null, afterState, null);
        }
        publishAdminAlert(
                "Bảng kê COD mới " + savedBatch.getBatchCode(),
                "Shipper #" + shipperId + " vừa nộp bảng kê COD (" + savedBatch.getOrderCount() + " đơn).",
                "INFO"
        );
        return savedBatch;
    }

    public ShipperCodBatch verifyBatch(Long batchId, String actorUsername, String note) {
        ShipperCodBatch batch = getBatchById(batchId);
        if (batch.getStatus() == CodBatchStatus.VERIFIED) {
            return batch;
        }
        if (batch.getStatus() == CodBatchStatus.DISPUTED) {
            throw new IllegalStateException("Không thể xác nhận bảng kê đang ở trạng thái tranh chấp");
        }

        String beforeStatus = batch.getStatus() == null ? null : batch.getStatus().name();
        List<Shipment> batchShipments = shipmentRepository.findByCodBatchId(batchId);
        if (codSettlementPort != null) {
            for (Shipment shipment : batchShipments) {
                Order order = shipment.getOrder();
                if (order != null && order.getId() != null && order.getPaymentStatus() == PaymentStatus.PENDING) {
                    codSettlementPort.settleCodOrder(order.getId(), actorUsername);
                }
            }
        }

        LocalDateTime now = LocalDateTime.now();
        batch.setStatus(CodBatchStatus.VERIFIED);
        batch.setVerifiedBy(resolveActorName(actorUsername));
        batch.setVerifiedAt(now);
        String trimmedNote = trimToNull(note);
        if (trimmedNote != null) {
            batch.setNote(trimmedNote);
        }

        ShipperCodBatch saved = shipperCodBatchRepository.save(batch);
        if (adminAuditLogService != null) {
            adminAuditLogService.log(
                    resolveActorName(actorUsername),
                    "COD_BATCH_VERIFIED",
                    "COD_BATCH",
                    saved.getId(),
                    Map.of("status", beforeStatus == null ? "UNKNOWN" : beforeStatus),
                    Map.of("status", CodBatchStatus.VERIFIED.name(), "orderCount", batchShipments.size()),
                    null
            );
        }
        broadcastCodBatchUpdateToShipper(saved,
                "Bảng kê COD " + (saved.getBatchCode() != null ? saved.getBatchCode() : "#" + saved.getId()) + " đã được xác nhận quyết toán.");
        return saved;
    }

    public ShipperCodBatch disputeBatch(Long batchId, String actorUsername, String reason) {
        String normalizedReason = requireText(reason, "Vui lòng nhập lý do đối soát sai lệch");
        ShipperCodBatch batch = getBatchById(batchId);
        if (batch.getStatus() == CodBatchStatus.VERIFIED) {
            throw new IllegalStateException("Bảng kê đã được xác nhận quyết toán, không thể tranh chấp");
        }

        String beforeStatus = batch.getStatus() == null ? null : batch.getStatus().name();
        LocalDateTime now = LocalDateTime.now();
        batch.setStatus(CodBatchStatus.DISPUTED);
        batch.setNote(normalizedReason);
        batch.setVerifiedBy(resolveActorName(actorUsername));
        batch.setVerifiedAt(now);

        ShipperCodBatch saved = shipperCodBatchRepository.save(batch);
        if (adminAuditLogService != null) {
            adminAuditLogService.log(
                    resolveActorName(actorUsername),
                    "COD_BATCH_DISPUTED",
                    "COD_BATCH",
                    saved.getId(),
                    Map.of("status", beforeStatus == null ? "UNKNOWN" : beforeStatus),
                    Map.of("status", CodBatchStatus.DISPUTED.name(), "reason", normalizedReason),
                    null
            );
        }
        publishAdminAlert(
                "Tranh chấp bảng kê COD " + saved.getBatchCode(),
                "Lý do: " + normalizedReason,
                "WARN"
        );
        broadcastCodBatchUpdateToShipper(saved,
                "Bảng kê COD " + (saved.getBatchCode() != null ? saved.getBatchCode() : "#" + saved.getId()) + " cần đối soát lại: " + normalizedReason);
        return saved;
    }

    private void broadcastCodBatchUpdateToShipper(ShipperCodBatch batch, String message) {
        if (sseNotificationService == null || batch == null || batch.getShipper() == null || batch.getShipper().getId() == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchId", batch.getId());
        payload.put("batchCode", batch.getBatchCode() != null ? batch.getBatchCode() : "");
        payload.put("status", batch.getStatus() != null ? batch.getStatus().name() : "");
        payload.put("message", message != null ? message : "");
        payload.put("timestamp", System.currentTimeMillis());
        sseNotificationService.broadcastToShipper(batch.getShipper().getId(), "cod_batch_updated", payload);
    }

    private void publishAdminAlert(String title, String message, String severity) {
        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(
                    new AdminAlertEvent(this, title, message, "/admin/shippers/cod-batches", severity)
            );
        }
    }

    private List<Shipment> resolveAndValidateEligibleShipments(Long shipperId, List<Long> shipmentIds) {
        if (shipmentIds == null || shipmentIds.isEmpty()) {
            return getUnbatchedCompletedCodShipments(shipperId);
        }

        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long shipmentId : shipmentIds) {
            if (shipmentId != null) {
                uniqueIds.add(shipmentId);
            }
        }
        if (uniqueIds.isEmpty()) {
            return getUnbatchedCompletedCodShipments(shipperId);
        }

        List<Shipment> found = new ArrayList<>();
        for (Shipment s : shipmentRepository.findAllById(new ArrayList<>(uniqueIds))) {
            found.add(s);
        }
        if (found.size() != uniqueIds.size()) {
            throw new IllegalArgumentException("Một hoặc nhiều vận đơn không tồn tại");
        }

        for (Shipment shipment : found) {
            if (shipment.getShipper() == null || !shipperId.equals(shipment.getShipper().getId())) {
                throw new IllegalStateException("Vận đơn #" + shipment.getId() + " không thuộc shipper này");
            }
            if (shipment.getStatus() != ShipmentStatus.COMPLETED) {
                throw new IllegalStateException("Vận đơn #" + shipment.getId() + " chưa ở trạng thái giao thành công");
            }
            if (shipment.getOrder() == null || shipment.getOrder().getPaymentMethod() != PaymentMethod.COD) {
                throw new IllegalStateException("Vận đơn #" + shipment.getId() + " không phải đơn thanh toán COD");
            }
            if (shipment.getCodBatch() != null) {
                throw new IllegalStateException("Vận đơn #" + shipment.getId() + " đã thuộc bảng kê COD khác");
            }
        }
        return found;
    }

    private java.util.Optional<Shipper> resolveShipperByUsername(String username) {
        String normalized = trimToNull(username);
        if (normalized == null || userRepository == null) {
            return java.util.Optional.empty();
        }
        return userRepository.findByUsername(normalized)
                .map(User::getId)
                .flatMap(shipperRepository::findByUserId);
    }

    private String generateBatchCode(LocalDateTime now) {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "COD-" + now.format(BATCH_TIME_FORMAT) + "-" + suffix;
    }

    private String resolveActor(String actorUsername, Shipper shipper) {
        String trimmed = trimToNull(actorUsername);
        if (trimmed != null) {
            return trimmed;
        }
        if (shipper != null && shipper.getUser() != null && shipper.getUser().getUsername() != null) {
            return shipper.getUser().getUsername();
        }
        return "shipper";
    }

    private String resolveActorName(String actorUsername) {
        String trimmed = trimToNull(actorUsername);
        return trimmed == null ? "system" : trimmed;
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
