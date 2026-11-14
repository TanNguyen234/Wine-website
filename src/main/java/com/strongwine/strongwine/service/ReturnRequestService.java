package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderItem;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.Payment;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.PaymentTransaction;
import com.strongwine.strongwine.entity.ReturnRequest;
import com.strongwine.strongwine.entity.ReturnStatus;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.event.AdminAlertEvent;
import com.strongwine.strongwine.event.OrderStatusUpdatedEvent;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.PaymentRepository;
import com.strongwine.strongwine.repository.PaymentTransactionRepository;
import com.strongwine.strongwine.repository.ReturnRequestRepository;
import com.strongwine.strongwine.service.port.ShipmentOrderLifecyclePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class ReturnRequestService {

    private static final List<ReturnStatus> OPEN_RETURN_STATUSES = List.of(
            ReturnStatus.REQUESTED,
            ReturnStatus.APPROVED
    );

    @Autowired
    private ReturnRequestRepository returnRequestRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired(required = false)
    private PaymentRepository paymentRepository;

    @Autowired(required = false)
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private InventoryService inventoryService;

    @Lazy
    @Autowired(required = false)
    private ShipmentOrderLifecyclePort shipmentOrderLifecyclePort;

    @Autowired(required = false)
    private AdminAuditLogService adminAuditLogService;

    @Autowired(required = false)
    private ApplicationEventPublisher applicationEventPublisher;

    public ReturnRequest requestReturn(Long orderId, Long userId, String reason) {
        if (orderId == null || userId == null) {
            throw new IllegalArgumentException("Thông tin đơn hàng hoặc người dùng không hợp lệ");
        }
        String normalizedReason = reason != null ? reason.trim() : "";
        if (normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập lý do trả hàng");
        }

        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + orderId));

        if (order.getUser() == null || !userId.equals(order.getUser().getId())) {
            throw new SecurityException("Bạn không có quyền gửi yêu cầu trả hàng cho đơn này");
        }

        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("Chỉ đơn hàng đã thanh toán/hoàn tất giao mới có thể yêu cầu trả hàng");
        }

        if (shipmentOrderLifecyclePort != null) {
            Optional<Shipment> shipmentOpt = shipmentOrderLifecyclePort.getShipmentByOrderId(orderId);
            if (shipmentOpt.isEmpty() || shipmentOpt.get().getStatus() != ShipmentStatus.COMPLETED) {
                throw new IllegalStateException("Đơn hàng chưa giao hoàn tất nên chưa thể yêu cầu trả hàng");
            }
        }

        if (returnRequestRepository.existsByOrderIdAndStatusIn(orderId, OPEN_RETURN_STATUSES)) {
            throw new IllegalStateException("Đơn hàng #" + orderId + " đã có yêu cầu trả hàng đang xử lý");
        }

        ReturnRequest returnRequest = new ReturnRequest();
        returnRequest.setOrder(order);
        returnRequest.setUser(order.getUser());
        returnRequest.setReason(normalizedReason);
        returnRequest.setStatus(ReturnStatus.REQUESTED);
        returnRequest.setRefundAmount(order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO);

        ReturnRequest saved = returnRequestRepository.save(returnRequest);

        Map<String, Object> beforeState = statusSnapshot(order.getStatus().name(), null);
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        if (adminAuditLogService != null) {
            String actor = order.getUser().getUsername() != null ? order.getUser().getUsername() : "user-" + userId;
            adminAuditLogService.log(
                    actor,
                    "REQUEST_RETURN",
                    "RETURN_REQUEST",
                    saved.getId(),
                    beforeState,
                    statusSnapshot(OrderStatus.RETURN_REQUESTED.name(), ReturnStatus.REQUESTED.name()),
                    null
            );
        }

        if (applicationEventPublisher != null) {
            String customerName = order.getUser().getUsername() != null ? order.getUser().getUsername() : "Khách hàng #" + userId;
            applicationEventPublisher.publishEvent(new AdminAlertEvent(
                    this,
                    "Yêu cầu trả hàng mới #" + order.getId(),
                    customerName + " yêu cầu trả hàng đơn #" + order.getId() + ": " + normalizedReason,
                    "/admin/returns",
                    "WARNING",
                    order.getId()
            ));
        }

        return saved;
    }

    public ReturnRequest approveReturn(Long returnRequestId, String actorUsername, String adminNote) {
        ReturnRequest request = returnRequestRepository.findById(returnRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu trả hàng #" + returnRequestId));

        if (request.getStatus() != ReturnStatus.REQUESTED) {
            throw new IllegalStateException("Chỉ yêu cầu đang ở trạng thái REQUESTED mới có thể duyệt");
        }

        String actor = normalizeActor(actorUsername);
        Map<String, Object> beforeState = statusSnapshot(
                request.getOrder() != null && request.getOrder().getStatus() != null ? request.getOrder().getStatus().name() : null,
                request.getStatus().name()
        );

        request.setStatus(ReturnStatus.APPROVED);
        if (adminNote != null && !adminNote.isBlank()) {
            request.setAdminNote(adminNote.trim());
        }
        request.setResolvedBy(actor);
        request.setResolvedAt(LocalDateTime.now());
        ReturnRequest saved = returnRequestRepository.save(request);

        if (adminAuditLogService != null) {
            adminAuditLogService.log(
                    actor,
                    "APPROVE_RETURN",
                    "RETURN_REQUEST",
                    saved.getId(),
                    beforeState,
                    statusSnapshot(
                            request.getOrder() != null && request.getOrder().getStatus() != null ? request.getOrder().getStatus().name() : null,
                            ReturnStatus.APPROVED.name()
                    ),
                    null
            );
        }

        return saved;
    }

    public ReturnRequest rejectReturn(Long returnRequestId, String actorUsername, String adminNote) {
        ReturnRequest request = returnRequestRepository.findById(returnRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu trả hàng #" + returnRequestId));

        if (request.getStatus() != ReturnStatus.REQUESTED && request.getStatus() != ReturnStatus.APPROVED) {
            throw new IllegalStateException("Yêu cầu trả hàng đã kết thúc và không thể từ chối");
        }

        String actor = normalizeActor(actorUsername);
        Order order = orderRepository.findByIdWithPessimisticLock(request.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng liên kết"));

        Map<String, Object> beforeState = statusSnapshot(order.getStatus().name(), request.getStatus().name());

        request.setStatus(ReturnStatus.REJECTED);
        if (adminNote != null && !adminNote.isBlank()) {
            request.setAdminNote(adminNote.trim());
        }
        request.setResolvedBy(actor);
        request.setResolvedAt(LocalDateTime.now());

        if (order.getStatus() == OrderStatus.RETURN_REQUESTED) {
            order.setStatus(OrderStatus.PAID);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        }

        ReturnRequest saved = returnRequestRepository.save(request);

        if (adminAuditLogService != null) {
            adminAuditLogService.log(
                    actor,
                    "REJECT_RETURN",
                    "RETURN_REQUEST",
                    saved.getId(),
                    beforeState,
                    statusSnapshot(order.getStatus().name(), ReturnStatus.REJECTED.name()),
                    null
            );
        }

        return saved;
    }

    public ReturnRequest completeRefund(Long returnRequestId, String actorUsername, String adminNote) {
        ReturnRequest request = returnRequestRepository.findById(returnRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu trả hàng #" + returnRequestId));

        if (request.getStatus() != ReturnStatus.REQUESTED && request.getStatus() != ReturnStatus.APPROVED) {
            throw new IllegalStateException("Yêu cầu trả hàng ở trạng thái " + request.getStatus() + " không thể hoàn tiền");
        }

        String actor = normalizeActor(actorUsername);
        LocalDateTime now = LocalDateTime.now();

        Order order = orderRepository.findByIdWithPessimisticLock(request.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng liên kết"));

        Map<String, Object> beforeState = statusSnapshot(order.getStatus().name(), request.getStatus().name());
        beforeState.put("paymentStatus", order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null);

        restockOrderItems(order, actor);
        recordRefundPaymentTransaction(order, request, actor, now);

        order.setPaymentStatus(PaymentStatus.REFUNDED);
        order.setStatus(OrderStatus.RETURNED);
        order.setUpdatedAt(now);
        orderRepository.save(order);

        request.setStatus(ReturnStatus.REFUNDED);
        if (adminNote != null && !adminNote.isBlank()) {
            request.setAdminNote(adminNote.trim());
        }
        request.setResolvedBy(actor);
        request.setResolvedAt(now);
        ReturnRequest saved = returnRequestRepository.save(request);

        if (adminAuditLogService != null) {
            Map<String, Object> afterState = statusSnapshot(order.getStatus().name(), ReturnStatus.REFUNDED.name());
            afterState.put("paymentStatus", PaymentStatus.REFUNDED.name());
            adminAuditLogService.log(actor, "REFUND_RETURN", "RETURN_REQUEST", saved.getId(), beforeState, afterState, null);
        }

        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new OrderStatusUpdatedEvent(
                    this,
                    order.getId(),
                    OrderStatus.RETURNED.name(),
                    "Đã hoàn tiền và nhập lại kho cho đơn #" + order.getId()
            ));
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ReturnRequest> getAllReturnRequests() {
        return returnRequestRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<ReturnRequest> getReturnRequestsByStatus(ReturnStatus status) {
        if (status == null) {
            return getAllReturnRequests();
        }
        return returnRequestRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public List<ReturnRequest> getReturnRequestsForOrder(Long orderId) {
        return returnRequestRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    @Transactional(readOnly = true)
    public List<ReturnRequest> getReturnRequestsForUser(Long userId) {
        return returnRequestRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Optional<ReturnRequest> getReturnRequestById(Long id) {
        return returnRequestRepository.findById(id);
    }

    private void restockOrderItems(Order order, String actor) {
        if (order.getOrderItems() == null) {
            return;
        }
        Long warehouseId = order.getAllocatedWarehouse() != null ? order.getAllocatedWarehouse().getId() : null;
        String note = "Hoàn kho trả hàng đơn #" + order.getId();
        for (OrderItem item : order.getOrderItems()) {
            if (item == null || item.getWine() == null || item.getWine().getId() == null
                    || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }
            if (warehouseId != null) {
                inventoryService.importStock(item.getWine().getId(), warehouseId, item.getQuantity(), actor, note);
            } else {
                inventoryService.importStock(item.getWine().getId(), item.getQuantity(), null, note);
            }
        }
    }

    private void recordRefundPaymentTransaction(Order order, ReturnRequest request, String actor, LocalDateTime now) {
        if (paymentRepository == null) {
            return;
        }
        List<Payment> payments = paymentRepository.findByOrderIdForUpdate(order.getId());
        if (payments == null || payments.isEmpty()) {
            return;
        }
        Payment targetPayment = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst()
                .orElse(payments.get(0));
        targetPayment.setStatus(PaymentStatus.REFUNDED);
        targetPayment.setUpdatedAt(now);
        Payment savedPayment = paymentRepository.save(targetPayment);

        if (paymentTransactionRepository != null) {
            PaymentTransaction tx = new PaymentTransaction();
            tx.setPayment(savedPayment);
            tx.setTransactionType("REFUND");
            tx.setStatus("SUCCESS");
            tx.setPayload("actor=" + actor + ";returnRequestId=" + request.getId() + ";amount=" + request.getRefundAmount());
            tx.setCreatedAt(now);
            paymentTransactionRepository.save(tx);
        }
    }

    private Map<String, Object> statusSnapshot(String orderStatus, String returnStatus) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderStatus", orderStatus);
        if (returnStatus != null) {
            map.put("returnStatus", returnStatus);
        }
        return map;
    }

    private String normalizeActor(String actorUsername) {
        if (actorUsername == null || actorUsername.isBlank()) {
            return "admin";
        }
        return actorUsername.trim();
    }
}
