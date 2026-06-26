package com.example.orderflowmanagement.eventstore.service;

import com.example.orderflowmanagement.common.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.orderflowmanagement.eventstore.entity.EventStoreEntity;
import com.example.orderflowmanagement.eventstore.repository.EventStoreRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for event store persistence and replay.
 * 
 * Interview Talking Points:
 * - Handles immutable event storage (append-only)
 * - Serializes domain events to JSON
 * - Supports event replay for aggregate reconstruction
 * - Implements optimistic locking via version numbers
 * - Correlation ID enables saga tracing
 */
@Slf4j
@Service
@Transactional
public class EventStoreService {

    private final EventStoreRepository eventStoreRepository;
    private final ObjectMapper objectMapper;

    public EventStoreService(EventStoreRepository eventStoreRepository, ObjectMapper objectMapper) {
        this.eventStoreRepository = eventStoreRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Append a new event to the event store.
     * 
     * @param orderId the aggregate root ID (order ID)
     * @param event the domain event to persist
     * @param correlationId for saga tracing
     * @param causationId the event that triggered this one
     */
    public void appendEvent(String orderId, OrderEvent event, String correlationId, String causationId) {
        try {
            Long nextVersion = eventStoreRepository.findMaxVersionForAggregate(orderId) + 1;

            String eventData = objectMapper.writeValueAsString(event);
            String eventMetadata = objectMapper.writeValueAsString(createMetadata(correlationId, causationId));

            EventStoreEntity entity = EventStoreEntity.builder()
                    .aggregateId(orderId)
                    .aggregateType("Order")
                    .eventType(event.eventType())
                    .version(nextVersion)
                    .eventData(eventData)
                    .eventMetadata(eventMetadata)
                    .eventVersion(1) // For schema evolution
                    .correlationId(correlationId)
                    .causationId(causationId)
                    .createdAt(LocalDateTime.now())
                    .build();

            eventStoreRepository.save(entity);
            log.info("Event appended: orderId={}, eventType={}, version={}", orderId, event.eventType(), nextVersion);

        } catch (Exception e) {
            log.error("Failed to append event for orderId: {}", orderId, e);
            throw new RuntimeException("Event store append failed", e);
        }
    }

    /**
     * Retrieve all events for an aggregate in order.
     * 
     * @param orderId the aggregate root ID
     * @return list of events ordered by version (creation sequence)
     */
    public List<OrderEvent> getEventsByAggregateId(String orderId) {
        return eventStoreRepository.findByAggregateIdOrderByVersion(orderId)
                .stream()
                .map(this::deserializeEvent)
                .toList();
    }

    /**
     * Replay events from a specific version (useful for rebuilding projections).
     * 
     * @param orderId the aggregate root ID
     * @param fromVersion start replaying from this version
     * @return events from that version onwards
     */
    public List<OrderEvent> replayEventsFrom(String orderId, Long fromVersion) {
        return eventStoreRepository.findByAggregateIdAndVersionGreaterThanEqualOrderByVersion(orderId, fromVersion)
                .stream()
                .map(this::deserializeEvent)
                .toList();
    }

    /**
     * Deserialize event data from JSON back to domain event object.
     * 
     * Interview Talking Points:
     * - Pattern matching on eventType ensures type safety
     * - Handles JSON deserialization with proper error handling
     * - Enables polymorphic deserialization of sealed event interface
     */
    private OrderEvent deserializeEvent(EventStoreEntity entity) {
        try {
            return switch (entity.getEventType()) {
                case "ORDER_CREATED" -> objectMapper.readValue(entity.getEventData(), OrderCreatedEvent.class);
                case "INVENTORY_RESERVED" -> objectMapper.readValue(entity.getEventData(), InventoryReservedEvent.class);
                case "INVENTORY_RESERVATION_FAILED" -> objectMapper.readValue(entity.getEventData(), InventoryReservationFailedEvent.class);
                case "PAYMENT_PROCESSED" -> objectMapper.readValue(entity.getEventData(), PaymentProcessedEvent.class);
                case "PAYMENT_FAILED" -> objectMapper.readValue(entity.getEventData(), PaymentFailedEvent.class);
                case "ORDER_CONFIRMED" -> objectMapper.readValue(entity.getEventData(), OrderConfirmedEvent.class);
                case "ORDER_CANCELLED" -> objectMapper.readValue(entity.getEventData(), OrderCancelledEvent.class);
                case "INVENTORY_RELEASED" -> objectMapper.readValue(entity.getEventData(), InventoryReleasedEvent.class);
                default -> throw new IllegalArgumentException("Unknown event type: " + entity.getEventType());
            };
        } catch (Exception e) {
            log.error("Failed to deserialize event: eventType={}", entity.getEventType(), e);
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    private EventMetadata createMetadata(String correlationId, String causationId) {
        return new EventMetadata(
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                causationId,
                System.currentTimeMillis()
        );
    }

    private record EventMetadata(String correlationId, String causationId, long timestamp) {}
}
