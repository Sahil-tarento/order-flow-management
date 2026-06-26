package com.example.orderflowmanagement.common.exceptions;

/**
 * Thrown when an order is not found in the event store.
 * 
 * Interview Talking Points:
 * - 404 response in REST layer
 * - Indicates invalid order ID
 * - Can suggest orders might be expired or deleted
 */
public class OrderNotFoundException extends DomainException {
    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
    }
}
