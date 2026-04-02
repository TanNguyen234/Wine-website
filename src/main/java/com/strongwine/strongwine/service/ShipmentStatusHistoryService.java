package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.entity.ShipmentStatusHistory;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.ShipmentStatusHistoryRepository;
import com.strongwine.strongwine.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class ShipmentStatusHistoryService {

    private final ShipmentStatusHistoryRepository shipmentStatusHistoryRepository;
    private final UserRepository userRepository;

    public ShipmentStatusHistoryService(ShipmentStatusHistoryRepository shipmentStatusHistoryRepository,
                                        UserRepository userRepository) {
        this.shipmentStatusHistoryRepository = shipmentStatusHistoryRepository;
        this.userRepository = userRepository;
    }

    public void logTransition(Shipment shipment,
                              ShipmentStatus fromStatus,
                              ShipmentStatus toStatus,
                              String reason,
                              String metadata,
                              String actorUsername) {
        if (shipment == null || shipment.getId() == null || toStatus == null) {
            return;
        }

        ShipmentStatusHistory history = new ShipmentStatusHistory();
        history.setShipment(shipment);
        history.setFromStatus(fromStatus == null ? null : fromStatus.name());
        history.setToStatus(toStatus.name());
        history.setReason(limit(reason, 500));
        history.setMetadata(limit(metadata, 2000));
        history.setActorUsername(limit(actorUsername, 255));

        if (actorUsername != null && !actorUsername.isBlank()) {
            User actor = userRepository.findByUsername(actorUsername.trim()).orElse(null);
            if (actor != null) {
                history.setActorUserId(actor.getId());
            }
        }

        shipmentStatusHistoryRepository.save(history);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
