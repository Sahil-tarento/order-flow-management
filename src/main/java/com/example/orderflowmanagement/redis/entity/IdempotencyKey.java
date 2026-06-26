package com.example.orderflowmanagement.redis.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model for Redis-backed idempotency keys.
 * 
 * Interview Talking Points:
 * - Prevents duplicate order creation from retries
 * - Stored in Redis with TTL (e.g., 24 hours)
 * - Lookup by idempotency key returns cached order ID
 * - Enables safe client retries without side effects
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {
    private String key;
    private String orderId;
    private long createdAt;
    private long expiresAt;
}
