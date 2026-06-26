package com.example.orderflowmanagement.common.domain;

import java.time.LocalDateTime;

/**
 * Event fired when order is confirmed (happy path completion).
 * 
 * This represents the final state of the order in the happy path. Only published
 * after both InventoryReservedEvent and PaymentProcessedEvent.
 * 
 * Interview Talking Points:
 * - Success state: Order is now locked and cannot be modified
 * - Event sourcing: Can be replayed to reconstruct order state
 * - Notification trigger: Other services notify customer
 */
public record OrderConfirmedEvent(
        String orderId,
        LocalDateTime occurredAt
) implements OrderEvent {

    public static OrderConfirmedEvent create(String orderId) {
        return new OrderConfirmedEvent(orderId, LocalDateTime.now());
    }

    @Override
    public String eventType() {
        return "ORDER_CONFIRMED";
    }
}
