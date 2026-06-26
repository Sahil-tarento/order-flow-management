package com.example.orderflowmanagement.common.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event fired when a customer creates an order.
 * 
 * This is the first event in the order lifecycle. Using Java 21 records provides:
 * - Immutability: Event cannot be modified after creation
 * - Automatic equals/hashCode: Ensures event comparison works correctly
 * - Compact syntax: Reduces boilerplate for value objects
 * 
 * Interview Talking Points:
 * - Immutable records ensure events cannot be corrupted
 * - All order data at creation time is captured
 * - Future events reference this originating event
 */
public record OrderCreatedEvent(
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        LocalDateTime occurredAt
) implements OrderEvent {

    public static OrderCreatedEvent create(String orderId, String customerId, BigDecimal amount, String currency) {
        return new OrderCreatedEvent(orderId, customerId, amount, currency, LocalDateTime.now());
    }

    @Override
    public String eventType() {
        return "ORDER_CREATED";
    }
}
