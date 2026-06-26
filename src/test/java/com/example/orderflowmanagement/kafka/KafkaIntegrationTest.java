package com.example.orderflowmanagement.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.orderflowmanagement.common.domain.OrderCreatedEvent;
import com.example.orderflowmanagement.kafka.producer.EventPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
public class KafkaIntegrationTest {

    @Container
    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testPublishOrderEventToKafka() throws Exception {
        String orderId = "itest-order-" + UUID.randomUUID();
        var evt = OrderCreatedEvent.create(orderId, "cust-it", BigDecimal.valueOf(99.99), "USD");

        // Publish via the application's publisher (this serializes JSON and sends to topic)
        eventPublisher.publishOrderEvent(orderId, evt);

        // Create a consumer to read from Kafka and assert message received
        Properties props = new Properties();
        props.put("bootstrap.servers", kafka.getBootstrapServers());
        props.put("group.id", "itest-consumer-" + UUID.randomUUID());
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("auto.offset.reset", "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList("order-events"));

            long deadline = System.currentTimeMillis() + 15000;
            boolean found = false;
            while (System.currentTimeMillis() < deadline && !found) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> r : records) {
                    String payload = r.value();
                    var read = objectMapper.readTree(payload);
                    if (read.has("orderId") && read.get("orderId").asText().equals(orderId)) {
                        found = true;
                        break;
                    }
                }
            }
            Assertions.assertTrue(found, "Published event should be consumed from Kafka");
        }
    }
}
