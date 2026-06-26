package com.example.orderflowmanagement.common.domain;

import java.time.LocalDateTime;

/**
 * Event fired when inventory is successfully reserved for an order.
 * 
 * Interview Talking Points:
 * - Separates concerns: Inventory system publishes this event
 * - Event-driven: Other services react to this event (e.g., payment processing)
 * - Asynchronous: Services don't need to wait for synchronous responses
 */
public record InventoryReservedEvent(
        String orderId,
        String reservationId,
        LocalDateTime occurredAt
) implements OrderEvent {

    public static InventoryReservedEvent create(String orderId, String reservationId) {
        return new InventoryReservedEvent(orderId, reservationId, LocalDateTime.now());
    }

    @Override
    public String eventType() {
        return "INVENTORY_RESERVED";
    }
}
