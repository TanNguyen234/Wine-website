package com.strongwine.strongwine.service.port;

public interface CodSettlementPort {
    void settleCodOrder(Long orderId, String actorUsername);
}
