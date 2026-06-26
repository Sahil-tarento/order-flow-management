package com.example.orderflowmanagement.common.exceptions;

/**
 * Thrown when distributed lock acquisition fails.
 * 
 * Interview Talking Points:
 * - Indicates resource contention
 * - 503 Service Unavailable or retry response
 * - Used for pessimistic locking strategy
 */
public class LockAcquisitionException extends DomainException {
    public LockAcquisitionException(String resource) {
        super("Failed to acquire lock for resource: " + resource);
    }
}
