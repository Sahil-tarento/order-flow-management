package com.example.orderflowmanagement.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.orderflowmanagement.common.domain.OrderCreatedEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

@SpringBootTest
public class OrderEventSerializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testOrderCreatedEventSerialization() throws Exception {
        var evt = OrderCreatedEvent.create("order-1", "cust-1", BigDecimal.valueOf(123.45), "USD");
        String json = objectMapper.writeValueAsString(evt);
        OrderCreatedEvent deserialized = objectMapper.readValue(json, OrderCreatedEvent.class);
        Assertions.assertEquals(evt.orderId(), deserialized.orderId());
        Assertions.assertEquals(evt.customerId(), deserialized.customerId());
        Assertions.assertEquals(evt.amount(), deserialized.amount());
    }
}
