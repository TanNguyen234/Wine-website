package com.strongwine.strongwine.event;

import org.springframework.context.ApplicationEvent;

public class ShipmentLiveStatusEvent extends ApplicationEvent {
    private final Long shipmentId;
    private final Long orderId;
    private final String status;
    private final String shipperName;
    private final String shipperPhone;
    private final String vehiclePlate;

    public ShipmentLiveStatusEvent(Object source, Long shipmentId, Long orderId, String status,
                                   String shipperName, String shipperPhone, String vehiclePlate) {
        super(source);
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.status = status;
        this.shipperName = shipperName;
        this.shipperPhone = shipperPhone;
        this.vehiclePlate = vehiclePlate;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public String getShipperName() {
        return shipperName;
    }

    public String getShipperPhone() {
        return shipperPhone;
    }

    public String getVehiclePlate() {
        return vehiclePlate;
    }
}
