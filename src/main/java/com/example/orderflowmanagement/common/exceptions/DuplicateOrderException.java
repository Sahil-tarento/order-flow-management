package com.example.orderflowmanagement.common.exceptions;

/**
 * Thrown when duplicate order creation is attempted.
 * 
 * Interview Talking Points:
 * - Prevents double-charging customers
 * - Uses idempotency keys from Redis
 * - 409 Conflict response in REST layer
 */
public class DuplicateOrderException extends DomainException {
    public DuplicateOrderException(String idempotencyKey) {
        super("Order already exists for idempotency key: " + idempotencyKey);
    }
}
