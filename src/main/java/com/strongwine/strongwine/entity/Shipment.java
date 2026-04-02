package com.strongwine.strongwine.entity;

import jakarta.persistence.*;
import com.strongwine.strongwine.util.AddressTextUtils;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipper_id")
    private Shipper shipper;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ShipmentStatus status = ShipmentStatus.PENDING_ASSIGNMENT;

    @Column(name = "shipping_name", length = 255)
    private String shippingName;

    @Column(name = "shipping_phone", length = 50)
    private String shippingPhone;

    @Column(name = "shipping_email", length = 255)
    private String shippingEmail;

    @Column(name = "shipping_address", length = 1000)
    private String shippingAddress;

    @Column(name = "shipping_latitude")
    private Double shippingLatitude;

    @Column(name = "shipping_longitude")
    private Double shippingLongitude;

    @Column(name = "otp_code", length = 6)
    private String otpCode;

    @Column(name = "otp_created_at")
    private LocalDateTime otpCreatedAt;

    @Column(name = "otp_expires_at")
    private LocalDateTime otpExpiresAt;

    @Column(name = "otp_attempt_count", nullable = false)
    private Integer otpAttemptCount = 0;

    @Column(name = "otp_locked_until")
    private LocalDateTime otpLockedUntil;

    @Column(name = "otp_last_sent_at")
    private LocalDateTime otpLastSentAt;

    @Column(name = "otp_sent_at")
    private LocalDateTime otpSentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "otp_delivery_status", nullable = false, length = 20)
    private OtpDeliveryStatus otpDeliveryStatus = OtpDeliveryStatus.PENDING;

    @Column(name = "otp_user_id")
    private Long otpUserId;

    @Column(name = "otp_verified", nullable = false)
    private Boolean otpVerified = false;

    @Column(name = "admin_override", nullable = false)
    private Boolean adminOverride = false;

    @Column(name = "admin_override_reason", length = 500)
    private String adminOverrideReason;

    @Column(name = "failure_note", length = 500)
    private String failureNote;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "estimated_delivery_at")
    private LocalDateTime estimatedDeliveryAt;

    @Column(name = "promised_window_start")
    private LocalDateTime promisedWindowStart;

    @Column(name = "promised_window_end")
    private LocalDateTime promisedWindowEnd;

    @Column(name = "delivery_attempt_count", nullable = false)
    private Integer deliveryAttemptCount = 0;

    @Column(name = "last_delivery_attempt_at")
    private LocalDateTime lastDeliveryAttemptAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "delivering_at")
    private LocalDateTime deliveringAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Shipper getShipper() {
        return shipper;
    }

    public void setShipper(Shipper shipper) {
        this.shipper = shipper;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public void setStatus(ShipmentStatus status) {
        this.status = status;
    }

    public String getShippingName() {
        return shippingName;
    }

    public void setShippingName(String shippingName) {
        this.shippingName = shippingName;
    }

    public String getShippingPhone() {
        return shippingPhone;
    }

    public void setShippingPhone(String shippingPhone) {
        this.shippingPhone = shippingPhone;
    }

    public String getShippingEmail() {
        return shippingEmail;
    }

    public void setShippingEmail(String shippingEmail) {
        this.shippingEmail = shippingEmail;
    }

    public String getShippingAddress() {
        return AddressTextUtils.stripLegacyGpsSuffix(shippingAddress);
    }

    public String getShippingAddressRaw() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public Double getShippingLatitude() {
        return shippingLatitude;
    }

    public void setShippingLatitude(Double shippingLatitude) {
        this.shippingLatitude = shippingLatitude;
    }

    public Double getShippingLongitude() {
        return shippingLongitude;
    }

    public void setShippingLongitude(Double shippingLongitude) {
        this.shippingLongitude = shippingLongitude;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }

    public LocalDateTime getOtpCreatedAt() {
        return otpCreatedAt;
    }

    public void setOtpCreatedAt(LocalDateTime otpCreatedAt) {
        this.otpCreatedAt = otpCreatedAt;
    }

    public LocalDateTime getOtpExpiresAt() {
        return otpExpiresAt;
    }

    public void setOtpExpiresAt(LocalDateTime otpExpiresAt) {
        this.otpExpiresAt = otpExpiresAt;
    }

    public Integer getOtpAttemptCount() {
        return otpAttemptCount;
    }

    public void setOtpAttemptCount(Integer otpAttemptCount) {
        this.otpAttemptCount = otpAttemptCount;
    }

    public LocalDateTime getOtpLockedUntil() {
        return otpLockedUntil;
    }

    public void setOtpLockedUntil(LocalDateTime otpLockedUntil) {
        this.otpLockedUntil = otpLockedUntil;
    }

    public LocalDateTime getOtpLastSentAt() {
        return otpLastSentAt;
    }

    public void setOtpLastSentAt(LocalDateTime otpLastSentAt) {
        this.otpLastSentAt = otpLastSentAt;
    }

    public LocalDateTime getOtpSentAt() {
        return otpSentAt;
    }

    public void setOtpSentAt(LocalDateTime otpSentAt) {
        this.otpSentAt = otpSentAt;
    }

    public OtpDeliveryStatus getOtpDeliveryStatus() {
        return otpDeliveryStatus;
    }

    public void setOtpDeliveryStatus(OtpDeliveryStatus otpDeliveryStatus) {
        this.otpDeliveryStatus = otpDeliveryStatus;
    }

    public Long getOtpUserId() {
        return otpUserId;
    }

    public void setOtpUserId(Long otpUserId) {
        this.otpUserId = otpUserId;
    }

    public Boolean getOtpVerified() {
        return otpVerified;
    }

    public void setOtpVerified(Boolean otpVerified) {
        this.otpVerified = otpVerified;
    }

    public Boolean getAdminOverride() {
        return adminOverride;
    }

    public void setAdminOverride(Boolean adminOverride) {
        this.adminOverride = adminOverride;
    }

    public String getAdminOverrideReason() {
        return adminOverrideReason;
    }

    public void setAdminOverrideReason(String adminOverrideReason) {
        this.adminOverrideReason = adminOverrideReason;
    }

    public String getFailureNote() {
        return failureNote;
    }

    public void setFailureNote(String failureNote) {
        this.failureNote = failureNote;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public LocalDateTime getEstimatedDeliveryAt() {
        return estimatedDeliveryAt;
    }

    public void setEstimatedDeliveryAt(LocalDateTime estimatedDeliveryAt) {
        this.estimatedDeliveryAt = estimatedDeliveryAt;
    }

    public LocalDateTime getPromisedWindowStart() {
        return promisedWindowStart;
    }

    public void setPromisedWindowStart(LocalDateTime promisedWindowStart) {
        this.promisedWindowStart = promisedWindowStart;
    }

    public LocalDateTime getPromisedWindowEnd() {
        return promisedWindowEnd;
    }

    public void setPromisedWindowEnd(LocalDateTime promisedWindowEnd) {
        this.promisedWindowEnd = promisedWindowEnd;
    }

    public Integer getDeliveryAttemptCount() {
        return deliveryAttemptCount;
    }

    public void setDeliveryAttemptCount(Integer deliveryAttemptCount) {
        this.deliveryAttemptCount = deliveryAttemptCount;
    }

    public LocalDateTime getLastDeliveryAttemptAt() {
        return lastDeliveryAttemptAt;
    }

    public void setLastDeliveryAttemptAt(LocalDateTime lastDeliveryAttemptAt) {
        this.lastDeliveryAttemptAt = lastDeliveryAttemptAt;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(LocalDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(LocalDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }

    public LocalDateTime getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(LocalDateTime returnedAt) {
        this.returnedAt = returnedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getPickedUpAt() {
        return pickedUpAt;
    }

    public void setPickedUpAt(LocalDateTime pickedUpAt) {
        this.pickedUpAt = pickedUpAt;
    }

    public LocalDateTime getDeliveringAt() {
        return deliveringAt;
    }

    public void setDeliveringAt(LocalDateTime deliveringAt) {
        this.deliveringAt = deliveringAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
