package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for F-SW-04: checkout creates a PENDING_ASSIGNMENT shipment at once, and that
 * shipment must not block the customer's own cancellation of an unpaid order.
 */
class OrderCancelRuleTest {

    private static Order order(OrderStatus status, PaymentStatus paymentStatus) {
        Order order = new Order();
        order.setStatus(status);
        order.setPaymentStatus(paymentStatus);
        return order;
    }

    private static Optional<Shipment> shipment(ShipmentStatus status) {
        Shipment shipment = new Shipment();
        shipment.setStatus(status);
        return Optional.of(shipment);
    }

    @Test
    void unpaidPendingOrderWithoutShipmentCanBeCancelled() {
        assertTrue(OrderController.isCustomerCancellable(
                order(OrderStatus.PENDING, PaymentStatus.PENDING), Optional.empty()));
    }

    @Test
    void checkoutShipmentStillAwaitingAssignmentDoesNotBlockCancellation() {
        assertTrue(OrderController.isCustomerCancellable(
                order(OrderStatus.PENDING, PaymentStatus.PENDING), shipment(ShipmentStatus.PENDING_ASSIGNMENT)));
    }

    @Test
    void shipmentAssignedToShipperBlocksCancellation() {
        assertFalse(OrderController.isCustomerCancellable(
                order(OrderStatus.PENDING, PaymentStatus.PENDING), shipment(ShipmentStatus.ASSIGNED)));
    }

    @Test
    void shipmentPickedUpOrDeliveringBlocksCancellation() {
        assertFalse(OrderController.isCustomerCancellable(
                order(OrderStatus.PENDING, PaymentStatus.PENDING), shipment(ShipmentStatus.PICKED_UP)));
        assertFalse(OrderController.isCustomerCancellable(
                order(OrderStatus.PENDING, PaymentStatus.PENDING), shipment(ShipmentStatus.DELIVERING)));
    }

    @Test
    void paidOrderCannotBeCancelledByCustomer() {
        assertFalse(OrderController.isCustomerCancellable(
                order(OrderStatus.PAID, PaymentStatus.SUCCESS), Optional.empty()));
    }

    @Test
    void pendingOrderWithSuccessfulPaymentCannotBeCancelledByCustomer() {
        assertFalse(OrderController.isCustomerCancellable(
                order(OrderStatus.PENDING, PaymentStatus.SUCCESS), shipment(ShipmentStatus.PENDING_ASSIGNMENT)));
    }
}
