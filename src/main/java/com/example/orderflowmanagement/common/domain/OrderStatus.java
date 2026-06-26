package com.example.orderflowmanagement.common.domain;

/**
 * Order status enumeration representing the various states of an order.
 * 
 * Interview Talking Points:
 * - Enum ensures type safety: only valid states are possible
 * - Prevents stringly-typed errors common in legacy systems
 * - Natural fit for saga pattern state machines
 */
public enum OrderStatus {
    PENDING,
    INVENTORY_RESERVED,
    INVENTORY_RESERVATION_FAILED,
    PAYMENT_PROCESSED,
    PAYMENT_FAILED,
    CONFIRMED,
    FAILED,
    CANCELLED
}
