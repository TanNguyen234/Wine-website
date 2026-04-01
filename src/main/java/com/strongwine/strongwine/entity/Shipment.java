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
