package com.example.orderflowmanagement.kafka.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for analytics events.
 * 
 * Interview Talking Points:
 * - Aggregates business metrics in real-time
 * - Consumer group: analytics-consumer
 * - Ideal use case for columnar databases (Apache Druid, ClickHouse)
 * - Enables dashboards and reporting without impacting transaction system
 * - Can be replayed if analytics database needs to be rebuilt
 */
@Slf4j
@Component
public class AnalyticsConsumer {

    private final ObjectMapper objectMapper;

    public AnalyticsConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "order-events",
            groupId = "analytics-consumer",
            concurrency = "3"
    )
    public void consumeOrderEvent(
            @Payload String message,
            @Header("eventType") String eventType,
            Acknowledgment acknowledgment
    ) {
        try {
            log.info("Analytics consumer received event: eventType={}", eventType);

            // Process all events for metrics
            recordEventMetric(eventType, message);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process analytics event: eventType={}", eventType, e);
            throw new RuntimeException("Event processing failed", e);
        }
    }

    private void recordEventMetric(String eventType, String message) throws Exception {
        JsonNode event = objectMapper.readTree(message);

        switch (eventType) {
            case "ORDER_CREATED":
                recordOrderCreated(event);
                break;
            case "ORDER_CONFIRMED":
                recordOrderConfirmed(event);
                break;
            case "ORDER_CANCELLED":
                recordOrderCancelled(event);
                break;
            case "PAYMENT_PROCESSED":
                recordPaymentSuccess(event);
                break;
            case "PAYMENT_FAILED":
                recordPaymentFailure(event);
                break;
            case "INVENTORY_RESERVED":
                recordInventoryReserved(event);
                break;
            case "INVENTORY_RELEASED":
                recordInventoryReleased(event);
                break;
        }
    }

    private void recordOrderCreated(JsonNode event) {
        String orderId = event.get("orderId").asText();
        String amount = event.get("amount").asText();
        log.info("Metric: Order created - orderId={}, amount={}", orderId, amount);
        // In real implementation: INSERT into analytics table
        // metrics: orders_created, total_revenue_pending
    }

    private void recordOrderConfirmed(JsonNode event) {
        String orderId = event.get("orderId").asText();
        log.info("Metric: Order confirmed - orderId={}", orderId);
        // metrics: orders_confirmed, conversion_rate
    }

    private void recordOrderCancelled(JsonNode event) {
        String orderId = event.get("orderId").asText();
        String reason = event.get("reason").asText();
        log.info("Metric: Order cancelled - orderId={}, reason={}", orderId, reason);
        // metrics: orders_cancelled, cancellation_reasons
    }

    private void recordPaymentSuccess(JsonNode event) {
        String orderId = event.get("orderId").asText();
        log.info("Metric: Payment successful - orderId={}", orderId);
        // metrics: payments_successful, avg_processing_time
    }

    private void recordPaymentFailure(JsonNode event) {
        String orderId = event.get("orderId").asText();
        String reason = event.get("reason").asText();
        log.info("Metric: Payment failed - orderId={}, reason={}", orderId, reason);
        // metrics: payments_failed, failure_reasons
    }

    private void recordInventoryReserved(JsonNode event) {
        String orderId = event.get("orderId").asText();
        log.info("Metric: Inventory reserved - orderId={}", orderId);
        // metrics: inventory_reserved, current_stock_reserved
    }

    private void recordInventoryReleased(JsonNode event) {
        String orderId = event.get("orderId").asText();
        log.info("Metric: Inventory released - orderId={}", orderId);
        // metrics: inventory_released, release_reasons
    }
}
