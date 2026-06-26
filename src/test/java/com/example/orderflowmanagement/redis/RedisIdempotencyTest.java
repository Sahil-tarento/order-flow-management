package com.example.orderflowmanagement.redis;

import com.example.orderflowmanagement.redis.service.RedisService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
public class RedisIdempotencyTest {

    @Container
    public static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> redis.getHost());
        registry.add("spring.data.redis.port", () -> redis.getFirstMappedPort());
    }

    @Autowired
    private RedisService redisService;

    @Test
    public void testIdempotencyKeyBehavior() {
        String key = "idem-test-" + System.currentTimeMillis();
        String orderId = "order-it-1";

        boolean first = redisService.storeIdempotencyKey(key, orderId);
        Assertions.assertTrue(first, "First call should store idempotency key");

        boolean second = redisService.storeIdempotencyKey(key, orderId);
        Assertions.assertFalse(second, "Second call with same orderId should be detected as duplicate and return false");

        String cached = redisService.getIdempotencyKeyOrderId(key);
        Assertions.assertEquals(orderId, cached, "Cached orderId should match original");
    }
}
