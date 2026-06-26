package com.example.orderflowmanagement.common.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base sealed interface for all order domain events.
 * 
 * This demonstrates Java 21 sealed interface pattern for type-safe event hierarchy.
 * Every event in the system must implement this interface, ensuring compile-time safety
 * for event handling and pattern matching.
 * 
 * Interview Talking Points:
 * - Type safety: Sealed interfaces ensure all event types are known at compile time
 * - Pattern matching: Enables exhaustive pattern matching in Java 21
 * - Event sourcing: Immutable events are the single source of truth for aggregate state
 * - Audit trail: Every state change is captured as an event, providing complete history
 */
public sealed interface OrderEvent extends Serializable
        permits OrderCreatedEvent,
                InventoryReservedEvent,
                InventoryReservationFailedEvent,
                PaymentProcessedEvent,
                PaymentFailedEvent,
                OrderConfirmedEvent,
                OrderCancelledEvent,
                InventoryReleasedEvent {

    /**
     * @return unique identifier for the order aggregate
     */
    String orderId();

    /**
     * @return timestamp when event occurred
     */
    LocalDateTime occurredAt();

    /**
     * @return event type discriminator for serialization/routing
     */
    String eventType();
}
