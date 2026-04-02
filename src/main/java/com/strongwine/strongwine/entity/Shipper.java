package com.strongwine.strongwine.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "shippers")
public class Shipper {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 50)
    private String phone;

    @Column(name = "vehicle_type", length = 100)
    private String vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ShipperStatus status = ShipperStatus.ACTIVE;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = false;

    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    @Column(name = "location_updated_at")
    private LocalDateTime locationUpdatedAt;

    @Column(name = "max_concurrent_shipments", nullable = false)
    private Integer maxConcurrentShipments = 1;

    @Column(name = "active_shipment_count", nullable = false)
    private Integer activeShipmentCount = 0;

    @Column(name = "last_assignment_at")
    private LocalDateTime lastAssignmentAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public ShipperStatus getStatus() { return status; }
    public void setStatus(ShipperStatus status) { this.status = status; }
    public Boolean getIsAvailable() { return isAvailable; }
    public void setIsAvailable(Boolean available) { isAvailable = available; }
    public Double getCurrentLatitude() { return currentLatitude; }
    public void setCurrentLatitude(Double currentLatitude) { this.currentLatitude = currentLatitude; }
    public Double getCurrentLongitude() { return currentLongitude; }
    public void setCurrentLongitude(Double currentLongitude) { this.currentLongitude = currentLongitude; }
    public LocalDateTime getLocationUpdatedAt() { return locationUpdatedAt; }
    public void setLocationUpdatedAt(LocalDateTime locationUpdatedAt) { this.locationUpdatedAt = locationUpdatedAt; }
    public Integer getMaxConcurrentShipments() { return maxConcurrentShipments; }
    public void setMaxConcurrentShipments(Integer maxConcurrentShipments) { this.maxConcurrentShipments = maxConcurrentShipments; }
    public Integer getActiveShipmentCount() { return activeShipmentCount; }
    public void setActiveShipmentCount(Integer activeShipmentCount) { this.activeShipmentCount = activeShipmentCount; }
    public LocalDateTime getLastAssignmentAt() { return lastAssignmentAt; }
    public void setLastAssignmentAt(LocalDateTime lastAssignmentAt) { this.lastAssignmentAt = lastAssignmentAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

