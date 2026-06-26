package com.example.orderflowmanagement.eventstore.repository;

import com.example.orderflowmanagement.eventstore.entity.EventStoreEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for event store.
 * 
 * Interview Talking Points:
 * - Only appends new events, never updates/deletes
 * - findByAggregateIdOrderByVersion: retrieves complete event history
 * - Custom queries optimize for event sourcing patterns
 * - Enables efficient event replay and projection
 */
@Repository
public interface EventStoreRepository extends JpaRepository<EventStoreEntity, Long> {

    /**
     * Retrieve all events for an aggregate, ordered by version (event sequence).
     */
    List<EventStoreEntity> findByAggregateIdOrderByVersion(String aggregateId);

    /**
     * Retrieve events for an aggregate starting from a specific version.
     */
    List<EventStoreEntity> findByAggregateIdAndVersionGreaterThanEqualOrderByVersion(
            String aggregateId,
            Long fromVersion
    );

    /**
     * Find all events of a specific type for pagination/reporting.
     */
    Page<EventStoreEntity> findByEventType(String eventType, Pageable pageable);

    /**
     * Find events by correlation ID for saga tracking.
     */
    List<EventStoreEntity> findByCorrelationId(String correlationId);

    /**
     * Find the highest version for an aggregate (current sequence number).
     */
    @Query("SELECT COALESCE(MAX(e.version), 0L) FROM EventStoreEntity e WHERE e.aggregateId = :aggregateId")
    Long findMaxVersionForAggregate(@Param("aggregateId") String aggregateId);
}
