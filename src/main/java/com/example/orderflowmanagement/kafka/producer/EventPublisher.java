package com.example.orderflowmanagement.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.orderflowmanagement.common.domain.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import static com.example.orderflowmanagement.config.KafkaConfig.ORDER_EVENTS_TOPIC;

/**
 * Kafka producer for order events.
 * 
 * Interview Talking Points:
 * - Published to Kafka after event is persisted (ensures consistency)
 * - Serialized as JSON for language-agnostic consumption
 * - Messages are retained for 7 days (configurable)
 * - Enables eventual consistency between services
 * - Multiple consumers process same event independently
 */
@Slf4j
@Component
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish an order event to Kafka topic.
     * 
     * @param orderId the order ID (used as Kafka message key for partitioning)
     * @param event the domain event
     */
    public void publishOrderEvent(String orderId, OrderEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);

            Message<String> message = MessageBuilder
                    .withPayload(eventJson)
                    .setHeader(KafkaHeaders.TOPIC, ORDER_EVENTS_TOPIC)
                    .setHeader(KafkaHeaders.KEY, orderId)
                    .setHeader("eventType", event.eventType())
                    .build();

            kafkaTemplate.send(message);
            log.info("Event published to Kafka: orderId={}, eventType={}", orderId, event.eventType());

        } catch (Exception e) {
            log.error("Failed to publish event to Kafka: orderId={}, eventType={}", orderId, event.eventType(), e);
            // Note: In production, this should trigger alerts and possibly fallback persistence
            throw new RuntimeException("Event publication failed", e);
        }
    }
}
