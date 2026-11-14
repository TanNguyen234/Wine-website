package com.strongwine.strongwine.service.port;

import com.strongwine.strongwine.entity.Shipment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ShipmentOrderLifecyclePort {
    void cancelShipmentForOrder(Long orderId, String actorUsername, String reason);

    Optional<String> decryptOtpForOrderView(Shipment shipment);

    Map<Long, Shipment> getShipmentMapByOrderIds(List<Long> orderIds);

    Optional<Shipment> getShipmentByOrderId(Long orderId);
}
