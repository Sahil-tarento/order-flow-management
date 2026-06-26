package com.example.orderflowmanagement.common.exceptions;

/**
 * Base domain exception for order processing.
 * 
 * Interview Talking Points:
 * - All domain exceptions inherit from this
 * - Allows catch-all handling of domain-specific errors
 * - Separate from technical exceptions (NullPointerException, etc.)
 */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
