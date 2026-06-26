package com.example.orderflowmanagement.eventstore.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * JPA Entity for immutable event store.
 * 
 * Interview Talking Points:
 * - Append-only table: no updates or deletes
 * - Single source of truth for order state
 * - Events can be replayed to reconstruct any past state
 * - Includes event versioning for schema evolution
 * - Indexes optimize event retrieval by aggregate ID
 */
@Entity
@Table(name = "event_store", indexes = {
        @Index(name = "idx_event_store_aggregate", columnList = "aggregate_id"),
        @Index(name = "idx_event_store_event_type", columnList = "event_type"),
        @Index(name = "idx_event_store_created_at", columnList = "created_at"),
        @Index(name = "ux_event_store_aggregate_version", columnList = "aggregate_id, version", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventStoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false, length = 50)
    private String aggregateId; // orderId

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType; // "Order"

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType; // e.g., "ORDER_CREATED"

    @Column(name = "version", nullable = false)
    private Long version; // Event sequence number for this aggregate

    @Column(name = "event_data", nullable = false, columnDefinition = "TEXT")
    private String eventData; // JSON serialized event

    @Column(name = "event_metadata", columnDefinition = "TEXT")
    private String eventMetadata; // JSON with trace ID, user, etc.

    @Column(name = "event_version", nullable = false)
    private Integer eventVersion; // For schema evolution: v1, v2, etc.

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "correlation_id", length = 100)
    private String correlationId; // Links related events (e.g., saga steps)

    @Column(name = "causation_id", length = 100)
    private String causationId; // Event that caused this event

    @Version
    @Column(name = "record_version")
    private Long recordVersion;
}
