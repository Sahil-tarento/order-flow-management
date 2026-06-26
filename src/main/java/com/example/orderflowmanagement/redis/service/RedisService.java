package com.example.orderflowmanagement.redis.service;

import com.example.orderflowmanagement.common.exceptions.DuplicateOrderException;
import com.example.orderflowmanagement.common.exceptions.LockAcquisitionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for Redis-backed distributed patterns.
 * 
 * Interview Talking Points:
 * - Idempotency prevents duplicate order creation from retries
 * - Distributed locks prevent concurrent modification of same aggregate
 * - TTL automatically cleans up expired keys
 * - Redis ensures atomic operations (SET NX for locks)
 */
@Slf4j
@Service
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final String LOCK_PREFIX = "lock:";
    private static final long IDEMPOTENCY_TTL_HOURS = 24;
    private static final long LOCK_TTL_SECONDS = 30;

    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ─── Simple get/set/delete operations ───────────────────────────────

    /**
     * Get a value from Redis by key.
     *
     * @param key the Redis key
     * @return the value as String, or null if not found
     */
    public String get(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Set a value in Redis with TTL.
     *
     * @param key the Redis key
     * @param value the value to store
     * @param ttlSeconds TTL in seconds
     */
    public void set(String key, String value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        log.debug("Redis SET: key={}, ttl={}s", key, ttlSeconds);
    }

    /**
     * Set a value only if the key does not already exist (atomic).
     *
     * @param key the Redis key
     * @param value the value to store
     * @param ttlSeconds TTL in seconds
     * @return true if the key was set, false if it already existed
     */
    public boolean setIfAbsent(String key, String value, long ttlSeconds) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    /**
     * Delete a key from Redis.
     *
     * @param key the Redis key
     */
    public void delete(String key) {
        redisTemplate.delete(key);
        log.debug("Redis DEL: key={}", key);
    }

    // ─── Idempotency helpers ────────────────────────────────────────────

    /**
     * Check if idempotency key exists; if so, return cached order ID.
     * Otherwise, store the new order ID for future requests.
     * 
     * @param idempotencyKey unique request identifier
     * @param orderId the order to cache
     * @return true if this is a new request, false if duplicate
     * @throws DuplicateOrderException if key exists with different order ID
     */
    public boolean storeIdempotencyKey(String idempotencyKey, String orderId) {
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        // Attempt atomic set only if not exists
        Boolean isSet = redisTemplate.opsForValue().setIfAbsent(
                redisKey,
                orderId,
                IDEMPOTENCY_TTL_HOURS,
                TimeUnit.HOURS
        );

        if (Boolean.FALSE.equals(isSet)) {
            // Key exists, check if order ID matches
            Object existingOrderId = redisTemplate.opsForValue().get(redisKey);
            if (!orderId.equals(existingOrderId)) {
                throw new DuplicateOrderException(idempotencyKey);
            }
            log.info("Duplicate request detected for idempotency key: {}", idempotencyKey);
            return false;
        }

        log.info("Idempotency key stored: {}", idempotencyKey);
        return true;
    }

    /**
     * Get cached order ID for idempotency key (for duplicate request handling).
     */
    public String getIdempotencyKeyOrderId(String idempotencyKey) {
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        Object value = redisTemplate.opsForValue().get(redisKey);
        return value != null ? value.toString() : null;
    }

    // ─── Distributed lock helpers ───────────────────────────────────────

    /**
     * Acquire a distributed lock for exclusive access to a resource.
     * 
     * @param resourceId the resource to lock
     * @return lock token for releasing; null if lock acquisition failed
     */
    public String acquireLock(String resourceId) {
        String lockKey = LOCK_PREFIX + resourceId;
        String lockToken = UUID.randomUUID().toString();

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockToken,
                LOCK_TTL_SECONDS,
                TimeUnit.SECONDS
        );

        if (Boolean.TRUE.equals(acquired)) {
            log.info("Lock acquired: resourceId={}, token={}", resourceId, lockToken);
            return lockToken;
        }

        log.warn("Lock acquisition failed: resourceId={}", resourceId);
        throw new LockAcquisitionException(resourceId);
    }

    /**
     * Release a distributed lock (only if token matches).
     * 
     * @param resourceId the resource to unlock
     * @param lockToken the token received from acquireLock
     * @return true if lock was released, false if token mismatch
     */
    public boolean releaseLock(String resourceId, String lockToken) {
        String lockKey = LOCK_PREFIX + resourceId;
        Object storedToken = redisTemplate.opsForValue().get(lockKey);

        if (storedToken != null && storedToken.toString().equals(lockToken)) {
            redisTemplate.delete(lockKey);
            log.info("Lock released: resourceId={}", resourceId);
            return true;
        }

        log.warn("Lock release failed: token mismatch or lock not found, resourceId={}", resourceId);
        return false;
    }

    /**
     * Check if a lock is currently held.
     */
    public boolean isLocked(String resourceId) {
        String lockKey = LOCK_PREFIX + resourceId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }
}
