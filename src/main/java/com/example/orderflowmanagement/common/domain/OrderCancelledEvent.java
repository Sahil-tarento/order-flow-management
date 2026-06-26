package com.example.orderflowmanagement.common.domain;

import java.time.LocalDateTime;

/**
 * Event fired when order is cancelled.
 * 
 * Can occur in two scenarios:
 * 1. Inventory reservation fails (no compensation needed)
 * 2. Payment fails after inventory reservation (triggers inventory release)
 * 
 * Interview Talking Points:
 * - Reason field: Allows understanding why order was cancelled
 * - Compensation: May trigger InventoryReleasedEvent
 * - Final state: Order processing stops after this event
 */
public record OrderCancelledEvent(
        String orderId,
        String reason,
        LocalDateTime occurredAt
) implements OrderEvent {

    public static OrderCancelledEvent create(String orderId, String reason) {
        return new OrderCancelledEvent(orderId, reason, LocalDateTime.now());
    }

    @Override
    public String eventType() {
        return "ORDER_CANCELLED";
    }
}
