package com.example.orderflowmanagement.eventstore;

import com.example.orderflowmanagement.eventstore.service.EventStoreService;
import com.example.orderflowmanagement.common.domain.OrderCreatedEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

@SpringBootTest
@Testcontainers
public class EventStoreIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("orderflowx")
            .withUsername("orderflowx_user")
            .withPassword("orderflowx_pass");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private EventStoreService eventStoreService;

    @Test
    public void testAppendAndReadEvent() {
        String orderId = "itest-order-" + System.currentTimeMillis();
        var evt = OrderCreatedEvent.create(orderId, "cust-it", BigDecimal.valueOf(12.34), "USD");

        eventStoreService.appendEvent(orderId, evt, "corr-1", null);

        List<?> events = eventStoreService.getEventsByAggregateId(orderId);
        Assertions.assertFalse(events.isEmpty());
        Assertions.assertEquals(1, events.size());
    }
}
