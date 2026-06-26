package com.example.orderflowmanagement.common.domain;

import java.time.LocalDateTime;

/**
 * Event fired when payment fails.
 * 
 * This demonstrates saga compensation. When payment fails after inventory reservation,
 * we must release the reserved inventory to maintain consistency.
 * 
 * Interview Talking Points:
 * - Saga pattern: Triggers compensation workflow
 * - Two-phase flow: Inventory release happens after payment failure
 * - Eventual consistency: Final state is guaranteed through compensation
 */
public record PaymentFailedEvent(
        String orderId,
        String reason,
        LocalDateTime occurredAt
) implements OrderEvent {

    public static PaymentFailedEvent create(String orderId, String reason) {
        return new PaymentFailedEvent(orderId, reason, LocalDateTime.now());
    }

    @Override
    public String eventType() {
        return "PAYMENT_FAILED";
    }
}
