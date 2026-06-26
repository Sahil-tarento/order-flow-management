package com.example.orderflowmanagement.kafka.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for audit events.
 * 
 * Interview Talking Points:
 * - Records all events for compliance and debugging
 * - Consumer group: audit-consumer
 * - Immutable audit log for regulations (GDPR, PCI, SOC2)
 * - Enables investigations into issues
 * - Stored separately from transactional system for archival
 */
@Slf4j
@Component
public class AuditConsumer {

    private final ObjectMapper objectMapper;

    public AuditConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "order-events",
            groupId = "audit-consumer",
            concurrency = "1" // Single consumer to maintain order
    )
    public void consumeOrderEvent(
            @Payload String message,
            @Header("eventType") String eventType,
            Acknowledgment acknowledgment
    ) {
        try {
            log.info("Audit consumer received event: eventType={}, payload={}", eventType, message);

            // Store immutable audit record
            persistAuditLog(eventType, message);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to persist audit log: eventType={}", eventType, e);
            throw new RuntimeException("Audit persistence failed", e);
        }
    }

    private void persistAuditLog(String eventType, String message) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String orderId = event.get("orderId").asText();
        long timestamp = System.currentTimeMillis();

        log.info("Persisting audit log: orderId={}, eventType={}, timestamp={}", orderId, eventType, timestamp);

        // In real implementation:
        // INSERT INTO audit_log (aggregate_id, event_type, event_data, created_at)
        // VALUES (?, ?, ?, ?)
        // with retention policy (e.g., 7 years for financial records)
    }
}
