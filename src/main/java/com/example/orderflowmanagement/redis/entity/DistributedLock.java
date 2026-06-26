package com.example.orderflowmanagement.redis.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model for Redis-backed distributed locks.
 * 
 * Interview Talking Points:
 * - Prevents concurrent modification of same aggregate
 * - Uses Redis SET with NX and EX flags
 * - Lock value is unique token for safe unlock
 * - TTL prevents deadlocks if holder crashes
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistributedLock {
    private String lockKey;
    private String lockValue; // UUID
    private long acquiredAt;
    private long expiresAt;
    private long ttlMs;
}
