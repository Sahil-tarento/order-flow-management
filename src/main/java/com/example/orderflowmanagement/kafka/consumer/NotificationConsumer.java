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
 * Kafka consumer for notification events.
 * 
 * Interview Talking Points:
 * - Reacts to order events asynchronously
 * - Sends notifications to customers (email, SMS, push)
 * - Decoupled from order processing - can fail independently
 * - Manual acknowledgment ensures idempotent processing
 * - Consumer group: notification-consumer (enables parallelism)
 */
@Slf4j
@Component
public class NotificationConsumer {

    private final ObjectMapper objectMapper;

    public NotificationConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "order-events",
            groupId = "notification-consumer",
            concurrency = "3"
    )
    public void consumeOrderEvent(
            @Payload String message,
            @Header("eventType") String eventType,
            Acknowledgment acknowledgment
    ) {
        try {
            log.info("Notification consumer received event: eventType={}", eventType);

            // Route to appropriate handler based on event type
            switch (eventType) {
                case "ORDER_CREATED":
                    handleOrderCreated(message);
                    break;
                case "ORDER_CONFIRMED":
                    handleOrderConfirmed(message);
                    break;
                case "ORDER_CANCELLED":
                    handleOrderCancelled(message);
                    break;
                case "PAYMENT_FAILED":
                    handlePaymentFailed(message);
                    break;
                default:
                    log.debug("Ignoring event in notification consumer: eventType={}", eventType);
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process notification event: eventType={}", eventType, e);
            // Do NOT acknowledge - message will be retried
            throw new RuntimeException("Event processing failed", e);
        }
    }

    private void handleOrderCreated(String message) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String orderId = event.get("orderId").asText();
        log.info("Sending order confirmation email: orderId={}", orderId);
        // In real implementation: send email via SES/SendGrid
    }

    private void handleOrderConfirmed(String message) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String orderId = event.get("orderId").asText();
        log.info("Sending order ready notification: orderId={}", orderId);
        // In real implementation: send SMS via Twilio
    }

    private void handleOrderCancelled(String message) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String orderId = event.get("orderId").asText();
        String reason = event.get("reason").asText();
        log.info("Sending order cancellation notification: orderId={}, reason={}", orderId, reason);
        // In real implementation: send notification to customer
    }

    private void handlePaymentFailed(String message) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String orderId = event.get("orderId").asText();
        String reason = event.get("reason").asText();
        log.info("Sending payment failed notification: orderId={}, reason={}", orderId, reason);
        // In real implementation: suggest alternative payment method
    }
}
