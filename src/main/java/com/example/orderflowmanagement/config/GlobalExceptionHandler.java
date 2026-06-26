package com.example.orderflowmanagement.config;

import com.example.orderflowmanagement.common.dto.ErrorResponse;
import com.example.orderflowmanagement.common.exceptions.DomainException;
import com.example.orderflowmanagement.common.exceptions.DuplicateOrderException;
import com.example.orderflowmanagement.common.exceptions.OrderNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.UUID;

/**
 * <h2>GlobalExceptionHandler - Centralized Exception Handling</h2>
 *
 * <p><b>Architecture Role:</b>
 * GlobalExceptionHandler provides centralized exception handling for all REST controllers
 * via the @RestControllerAdvice annotation. Instead of handling exceptions in each controller
 * method, we define exception handlers here that intercept and convert exceptions into
 * appropriate HTTP responses. This ensures consistent error responses across the entire API.</p>
 *
 * <p><b>Design Pattern: Exception Handler Pattern + Centralized Error Response</b>
 * <ul>
 *   <li><b>Separation of Concerns:</b> Business logic (controllers, services) focuses on
 *       happy path execution. Exception translation (which exceptions become which HTTP status)
 *       is centralized in this handler. This reduces boilerplate code in controllers.</li>
 *   
 *   <li><b>Consistent Error Format:</b> All errors are returned in the same JSON format
 *       (ErrorResponse DTO). Clients can parse all errors uniformly without special cases.</li>
 *   
 *   <li><b>Error Tracing:</b> Each error includes a traceId (UUID). Clients can report this
 *       to support, and logs can be searched by traceId to find the full context of an error.</li>
 *   
 *   <li><b>Hierarchical Exception Handling:</b> We have handlers for specific exception types
 *       (OrderNotFoundException) as well as a generic handler for all Exceptions. Spring invokes
 *       the most specific handler first. If no specific handler matches, it falls back to the
 *       generic handler.</li>
 * </ul></p>
 *
 * <p><b>Production Considerations:</b>
 * <ul>
 *   <li><b>Exception Chaining:</b> Use exception causes (e.getCause()) to log the root cause.
 *       When an OrderNotFoundException occurs due to a database timeout, the cause reveals the
 *       true reason. This helps with debugging in production.</li>
 *   
 *   <li><b>Logging Levels:</b> Different exceptions warrant different log levels:
 *       - ERROR: Unexpected exceptions (NullPointerException, database connection errors)
 *       - WARN: Expected business exceptions (OrderNotFoundException, insufficient funds)
 *       This helps distinguish real issues from expected failures.</li>
 *   
 *   <li><b>Sensitive Information:</b> Never expose sensitive data in error messages returned
 *       to clients. For example, if a database query fails, return a generic "Internal Server Error"
 *       instead of the full SQL error. Log the detailed error internally for debugging.</li>
 *   
 *   <li><b>HTTP Status Codes:</b> Map exceptions to correct HTTP status codes:
 *       - 400 Bad Request: Invalid input (IllegalArgumentException)
 *       - 404 Not Found: Resource doesn't exist (OrderNotFoundException)
 *       - 409 Conflict: Resource state conflict (DuplicateOrderException)
 *       - 500 Internal Server Error: Unexpected errors</li>
 *   
 *   <li><b>Validation Error Handling:</b> @Valid triggers validation, and validation errors
 *       can be handled separately from business logic exceptions. Spring provides automatic
 *       MethodArgumentNotValidException handler, or you can customize it here.</li>
 *   
 *   <li><b>Timeout and Fallback Errors:</b> Circuit breaker failures, Temporal timeouts, and
 *       async operation failures should have clear error messages. They're not bugs but expected
 *       states when external services are degraded. Return appropriate HTTP status (503 Service
 *       Unavailable, 504 Gateway Timeout) rather than 500 Internal Server Error.</li>
 * </ul></p>
 *
 * <p><b>Interview Talking Points:</b>
 * <ul>
 *   <li>Q: "Why centralize exception handling instead of handling exceptions in each controller?"
 *       A: Centralization reduces code duplication and ensures consistent error responses. If we
 *       handle exceptions in 10 controllers, we maintain 10 error responses. Changes to error
 *       format require updates in 10 places. With centralized handling, one place to change.</li>
 *   
 *   <li>Q: "How do you correlate errors across services in a distributed system?"
 *       A: Each error response includes a traceId (UUID). The client includes this traceId in
 *       subsequent API calls (X-Trace-ID header). Each service appends its traceId to logs.
 *       When an error occurs, you can search logs across all services for the traceId.</li>
 *   
 *   <li>Q: "What's the difference between 409 and 400 status codes?"
 *       A: 400 Bad Request means the request is malformed (invalid JSON, missing required field).
 *       409 Conflict means the request is valid but conflicts with the current state (e.g., order
 *       already exists with the given idempotency key). The client's action differs: retry 400
 *       with corrected input, while 409 might mean the operation already succeeded.</li>
 *   
 *   <li>Q: "How do you handle exceptions from external services like payment gateways?"
 *       A: Circuit breakers wrap external calls and throw specific exceptions (e.g., CallNotPermittedException
 *       when circuit is open). We can catch these and return 503 Service Unavailable rather than
 *       500 Internal Server Error. This signals to clients that the issue is external, not our fault.</li>
 * </ul></p>
 *
 * @author OrderFlowX Team
 * @version 2.0
 * @since 1.0
 * @see RestControllerAdvice
 * @see ErrorResponse
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles DomainException - general business logic errors.
     *
     * <p><b>HTTP Status:</b> 400 Bad Request
     * <b>Reason:</b> DomainException indicates a business rule violation or expected error
     * during processing. It's not a bug but an expected condition (e.g., insufficient inventory).</p>
     *
     * @param ex the DomainException
     * @param request the web request context
     * @return ResponseEntity with error response and 400 status
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(
            DomainException ex,
            WebRequest request) {
        
        String traceId = UUID.randomUUID().toString();
        log.warn("DomainException occurred. traceId: {}, message: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .traceId(traceId)
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Domain Error")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles OrderNotFoundException - order not found in the system.
     *
     * <p><b>HTTP Status:</b> 404 Not Found
     * <b>Reason:</b> The requested order doesn't exist in the database or event store.</p>
     *
     * @param ex the OrderNotFoundException
     * @param request the web request context
     * @return ResponseEntity with error response and 404 status
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFoundException(
            OrderNotFoundException ex,
            WebRequest request) {
        
        String traceId = UUID.randomUUID().toString();
        log.warn("OrderNotFoundException occurred. traceId: {}, message: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .traceId(traceId)
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handles DuplicateOrderException - idempotency key already processed.
     *
     * <p><b>HTTP Status:</b> 409 Conflict
     * <b>Reason:</b> The idempotency key for this request was already processed. The order
     * may have already been created. This indicates a conflict between the current request
     * and the system state.</p>
     *
     * @param ex the DuplicateOrderException
     * @param request the web request context
     * @return ResponseEntity with error response and 409 status
     */
    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateOrderException(
            DuplicateOrderException ex,
            WebRequest request) {
        
        String traceId = UUID.randomUUID().toString();
        log.warn("DuplicateOrderException occurred. traceId: {}, message: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .traceId(traceId)
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handles IllegalArgumentException - invalid input parameters.
     *
     * <p><b>HTTP Status:</b> 400 Bad Request
     * <b>Reason:</b> The request contains invalid parameters (null, blank, out of range).
     * This is typically thrown by input validation in business methods.</p>
     *
     * @param ex the IllegalArgumentException
     * @param request the web request context
     * @return ResponseEntity with error response and 400 status
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request) {
        
        String traceId = UUID.randomUUID().toString();
        log.warn("IllegalArgumentException occurred. traceId: {}, message: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .traceId(traceId)
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Invalid Input")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles generic Exception - catch-all for unexpected errors.
     *
     * <p><b>HTTP Status:</b> 500 Internal Server Error
     * <b>Reason:</b> An unexpected exception occurred that wasn't handled by specific handlers.
     * This could indicate a bug or an unforeseen condition.</p>
     *
     * <p><b>Security Note:</b> The error message returned to the client is generic ("An unexpected
     * error occurred") to avoid leaking sensitive information. The detailed error is logged
     * internally with the traceId for debugging.</p>
     *
     * @param ex the Exception
     * @param request the web request context
     * @return ResponseEntity with error response and 500 status
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            WebRequest request) {
        
        String traceId = UUID.randomUUID().toString();
        log.error("Unexpected exception occurred. traceId: {}, exception: {}", traceId, ex.getClass().getSimpleName(), ex);

        ErrorResponse response = ErrorResponse.builder()
                .traceId(traceId)
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please contact support with traceId: " + traceId)
                .timestamp(Instant.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
