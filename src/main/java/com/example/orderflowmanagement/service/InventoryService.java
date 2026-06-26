package com.example.orderflowmanagement.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h2>InventoryService - External Inventory System Simulator</h2>
 *
 * <p><b>Architecture Role:</b>
 * InventoryService simulates an external inventory management system (or acts as a client adapter
 * if integrating with a real system). In the OrderFlowX architecture, this service represents
 * a boundary to another domain or service. It's wrapped with Resilience4j circuit breaker to
 * handle service degradation gracefully.</p>
 *
 * <p><b>Design Pattern: Circuit Breaker + Service Adapter</b>
 * The circuit breaker pattern prevents cascading failures. If the inventory system is down,
 * the circuit breaker opens and immediately fails new requests without waiting for timeouts.
 * This allows the order system to fail fast and preserve resources. The circuit breaker
 * periodically attempts to half-open and test if the service has recovered.</p>
 *
 * <p><b>Production Considerations:</b>
 * <ul>
 *   <li><b>Circuit Breaker Configuration:</b>
 *       <ul>
 *         <li>Failure Rate Threshold: 50% - If 50% of requests fail within a window, open the circuit</li>
 *         <li>Slow Call Rate Threshold: 50% - If 50% of requests exceed the timeout threshold, open circuit</li>
 *         <li>Wait Duration: 30 seconds - Before attempting to half-open the circuit</li>
 *         <li>Timeout Duration: 3 seconds - If call doesn't complete within 3s, mark as slow/failed</li>
 *       </ul>
 *   </li>
 *   
 *   <li><b>Idempotency:</b> In production, reservations are stored in a database with unique constraints
 *       on (orderId, itemId). If the request times out after the database write but before the response
 *       is sent, a retry will find the existing reservation and return its ID.</li>
 *   
 *   <li><b>External Service Integration:</b> In production, this would make HTTP/gRPC calls to the
 *       actual inventory service. You'd use RestTemplate, WebClient, or gRPC stubs. The circuit breaker
 *       protects these external calls from causing cascading failures.</li>
 *   
 *   <li><b>Fallback Strategy:</b> When the circuit breaker opens, we have options:
 *       <ul>
 *         <li>Fail immediately (current implementation) - Orders fail, customer retries later</li>
 *         <li>Fallback value - Assume inventory is available (risky, can oversell)</li>
 *         <li>Queue for async retry - Store in message queue, process when service recovers</li>
 *       </ul>
 *   </li>
 *   
 *   <li><b>Monitoring and Alerting:</b> Circuit breaker state transitions should trigger alerts.
 *       When the circuit opens, operations team is notified to investigate the inventory service.
 *       Metrics should track: requests, failures, circuit breaker state changes, latencies.</li>
 * </ul></p>
 *
 * <p><b>Interview Talking Points:</b>
 * <ul>
 *   <li>Q: "What happens when the circuit breaker opens?" A: New requests immediately fail without
 *       calling the actual inventory service. This prevents wasting resources on timeouts. The circuit
 *       stays open for a configured duration (e.g., 30 seconds), then transitions to half-open state
 *       and attempts a single request to test if the service has recovered.</li>
 *   
 *   <li>Q: "How do you monitor circuit breaker state?" A: Resilience4j emits metrics (Micrometer integration).
 *       You can query circuit breaker state via /actuator/circuitbreakers endpoint. Dashboard tools
 *       (Prometheus, Grafana) visualize circuit breaker state changes in real-time.</li>
 *   
 *   <li>Q: "Can you configure different circuit breakers for different external services?" A: Yes,
 *       Resilience4j allows instance-level and global configuration. You might have a shorter timeout
 *       for payment service (strict SLA) and longer timeout for inventory service (more tolerant).</li>
 *   
 *   <li>Q: "What if the inventory service is permanently down?" A: The circuit breaker opens and stays
 *       open (or cycles through half-open states). Orders fail consistently. You'd need manual intervention
 *       (fix the service or implement a fallback strategy). In practice, you'd also have a pager alert
 *       to wake up the on-call engineer.</li>
 * </ul></p>
 *
 * @author OrderFlowX Team
 * @version 2.0
 * @since 1.0
 * @see io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
 * @see io.github.resilience4j.circuitbreaker.CircuitBreaker
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    /**
     * In-memory store for active reservations.
     * Format: reservationId -> isActive
     * In production, this would be a database table.
     */
    private static final ConcurrentHashMap<String, Boolean> reservations = new ConcurrentHashMap<>();

    private static final String CIRCUIT_BREAKER_NAME = "inventoryServiceCircuitBreaker";
    private static final long SIMULATED_LATENCY_MS = 100;

    /**
     * Reserves inventory for an order.
     *
     * <p><b>Business Logic:</b>
     * This method simulates calling an external inventory system to block inventory for an order.
     * In production, this would make a REST/gRPC call to the actual inventory service.</p>
     *
     * <p><b>Resilience4j Integration:</b>
     * The {@code @CircuitBreaker} annotation wraps this method with circuit breaker logic.
     * If the method fails (throws exception), the failure count increments. Once failure
     * threshold is reached, the circuit breaker opens and new calls are fast-failed.</p>
     *
     * <p><b>Simulated Behavior:</b>
     * For demo purposes, this method:
     * - Simulates network latency (100ms)
     * - Always succeeds
     * - In production, it would fail intermittently or when inventory is unavailable</p>
     *
     * @param orderId the order identifier
     * @return unique reservation ID
     *
     * @throws RuntimeException if the circuit breaker is open or internal error occurs
     *
     * @see #releaseInventory(String)
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "reserveInventoryFallback")
    public String reserveInventory(String orderId) {
        log.info("InventoryService.reserveInventory called. orderId: {}", orderId);

        // Simulate network latency
        try {
            Thread.sleep(SIMULATED_LATENCY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted during simulated latency");
        }

        // Generate reservation ID
        String reservationId = UUID.randomUUID().toString();
        reservations.put(reservationId, true);

        log.info("Inventory reserved successfully. orderId: {}, reservationId: {}", orderId, reservationId);
        return reservationId;
    }

    /**
     * Releases a reserved inventory (compensating transaction).
     *
     * <p><b>Purpose:</b>
     * This method is called if the order workflow fails after inventory is reserved.
     * It releases the inventory reservation, making the stock available for other orders.</p>
     *
     * <p><b>Idempotency:</b>
     * If called twice with the same reservationId, the second call succeeds silently
     * (the reservation is already released).</p>
     *
     * @param reservationId the reservation ID to release
     *
     * @throws RuntimeException if the release fails
     */
    public void releaseInventory(String reservationId) {
        log.info("InventoryService.releaseInventory called. reservationId: {}", reservationId);

        // Simulate network latency
        try {
            Thread.sleep(SIMULATED_LATENCY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted during simulated latency");
        }

        reservations.remove(reservationId);
        log.info("Inventory released successfully. reservationId: {}", reservationId);
    }

    /**
     * Fallback method for reserveInventory when circuit breaker is open.
     *
     * <p><b>Fallback Strategy:</b>
     * When the circuit breaker is open (service is degraded), this fallback is invoked
     * instead of the actual reserveInventory method. Currently, we fail fast by throwing
     * an exception, but we could implement alternative strategies:
     * <ul>
     *   <li>Return a cached reservation (optimistic, risks overselling)</li>
     *   <li>Queue the request for async processing (queue-based resilience)</li>
     *   <li>Use shadow inventory (separate fallback inventory pool)</li>
     * </ul></p>
     *
     * @param orderId the order identifier
     * @return never returns; always throws exception
     * @throws RuntimeException indicating the service is unavailable
     */
    private String reserveInventoryFallback(String orderId, Exception ex) {
        log.warn("Circuit breaker is OPEN. reserveInventory falling back for orderId: {}", orderId, ex);
        throw new RuntimeException("Inventory service is currently unavailable. Please retry later.", ex);
    }
}
