package com.example.orderflowmanagement.common.domain;

import java.time.LocalDateTime;

/**
 * Event fired when reserved inventory is released (compensation action).
 * 
 * This is a compensation action triggered during saga rollback. It releases inventory
 * that was previously reserved when payment fails.
 * 
 * Interview Talking Points:
 * - Compensation: Reverses the effect of InventoryReservedEvent
 * - Idempotent: Releasing twice has no additional effect
 * - Audit trail: Records when inventory was released and why
 */
public record InventoryReleasedEvent(
        String orderId,
        String reservationId,
        String reason,
        LocalDateTime occurredAt
) implements OrderEvent {

    public static InventoryReleasedEvent create(String orderId, String reservationId, String reason) {
        return new InventoryReleasedEvent(orderId, reservationId, reason, LocalDateTime.now());
    }

    @Override
    public String eventType() {
        return "INVENTORY_RELEASED";
    }
}
