package com.example.orderflowmanagement.common.domain;

import java.time.LocalDateTime;

/**
 * Event fired when payment is successfully processed.
 * 
 * Interview Talking Points:
 * - Depends on: Only published after InventoryReservedEvent
 * - Payment gateway integration: Contains transaction reference
 * - Idempotency: Payment ID ensures duplicate payments are rejected
 */
public record PaymentProcessedEvent(
        String orderId,
        String paymentId,
        String transactionReference,
        LocalDateTime occurredAt
) implements OrderEvent {

    public static PaymentProcessedEvent create(String orderId, String paymentId, String transactionReference) {
        return new PaymentProcessedEvent(orderId, paymentId, transactionReference, LocalDateTime.now());
    }

    @Override
    public String eventType() {
        return "PAYMENT_PROCESSED";
    }
}
