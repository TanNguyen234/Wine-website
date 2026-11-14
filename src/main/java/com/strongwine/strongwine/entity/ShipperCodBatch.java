package com.strongwine.strongwine.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "shipper_cod_batches",
    uniqueConstraints = {
        @UniqueConstraint(name = "UQ_shipper_cod_batches_code", columnNames = {"batch_code"})
    }
)
public class ShipperCodBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipper_id", nullable = false)
    private Shipper shipper;

    @Column(name = "batch_code", nullable = false, unique = true, length = 64)
    private String batchCode;

    @Column(name = "total_expected_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalExpectedAmount;

    @Column(name = "total_submitted_amount", precision = 18, scale = 2)
    private BigDecimal totalSubmittedAmount;

    @Column(name = "order_count", nullable = false)
    private Integer orderCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CodBatchStatus status = CodBatchStatus.PENDING_SETTLEMENT;

    @Column(length = 500)
    private String note;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "verified_by", length = 150)
    private String verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = CodBatchStatus.PENDING_SETTLEMENT;
        }
        if (this.submittedAt == null) {
            this.submittedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Shipper getShipper() {
        return shipper;
    }

    public void setShipper(Shipper shipper) {
        this.shipper = shipper;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
    }

    public BigDecimal getTotalExpectedAmount() {
        return totalExpectedAmount;
    }

    public void setTotalExpectedAmount(BigDecimal totalExpectedAmount) {
        this.totalExpectedAmount = totalExpectedAmount;
    }

    public BigDecimal getTotalSubmittedAmount() {
        return totalSubmittedAmount;
    }

    public void setTotalSubmittedAmount(BigDecimal totalSubmittedAmount) {
        this.totalSubmittedAmount = totalSubmittedAmount;
    }

    public Integer getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }

    public CodBatchStatus getStatus() {
        return status;
    }

    public void setStatus(CodBatchStatus status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(String verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
