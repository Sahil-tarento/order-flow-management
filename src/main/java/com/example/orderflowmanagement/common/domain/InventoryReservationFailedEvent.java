package com.example.orderflowmanagement.common.domain;

import java.time.LocalDateTime;

/**
 * Event fired when inventory reservation fails.
 * 
 * This demonstrates the saga pattern failure flow. When inventory cannot be reserved,
 * the order is immediately cancelled without proceeding to payment.
 * 
 * Interview Talking Points:
 * - Failure handling: System captures failure as an event
 * - Saga compensation: Triggers order cancellation
 * - Atomicity: Either reservation succeeds or order is cancelled
 */
public record InventoryReservationFailedEvent(
        String orderId,
        String reason,
        LocalDateTime occurredAt
) implements OrderEvent {

    public static InventoryReservationFailedEvent create(String orderId, String reason) {
        return new InventoryReservationFailedEvent(orderId, reason, LocalDateTime.now());
    }

    @Override
    public String eventType() {
        return "INVENTORY_RESERVATION_FAILED";
    }
}
