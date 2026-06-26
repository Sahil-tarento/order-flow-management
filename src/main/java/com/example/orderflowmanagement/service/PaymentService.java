package com.example.orderflowmanagement.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h2>PaymentService - External Payment Gateway Simulator</h2>
 *
 * <p><b>Architecture Role:</b>
 * PaymentService simulates an external payment gateway (Stripe, PayPal, Square, etc.).
 * In a real system, this would handle credit card processing, fraud detection, currency conversion,
 * and settlement. This service boundary is wrapped with Resilience4j circuit breaker to handle
 * payment gateway failures gracefully.</p>
 *
 * <p><b>Design Pattern: Circuit Breaker + Retry Logic</b>
 * Payment operations are critical to business operations but inherently risky (external dependencies).
 * The circuit breaker protects against cascading failures while Temporal's retry mechanism handles
 * transient failures. Together, they provide robust payment processing even when the payment
 * gateway is degraded or temporarily unavailable.</p>
 *
 * <p><b>Why Payment Failures Matter for Testing:</b>
 * <ul>
 *   <li><b>Saga Compensation Testing:</b> The 30% simulated failure rate allows testing the Saga
 *       compensation logic. When payment fails after inventory is reserved, the workflow must
 *       compensate by releasing inventory. This is critical to verify end-to-end.</li>
 *   
 *   <li><b>Idempotency Testing:</b> If a payment request times out after charging but before
 *       returning a payment ID, the retry must not charge twice. The idempotency mechanism
 *       (database unique constraint or payment gateway idempotency key) must work correctly.</li>
 *   
 *   <li><b>Circuit Breaker Testing:</b> Once the failure rate triggers the circuit breaker,
 *       new payment requests immediately fail. This tests the system's resilience under
 *       payment gateway outages.</li>
 * </ul></p>
 *
 * <p><b>Production Considerations:</b>
 * <ul>
 *   <li><b>PCI Compliance:</b> Real payment processing must not handle raw credit card data
 *       (PCI DSS violation). Use payment gateways (Stripe, PayPal) that tokenize cards and
 *       return tokens for storage. This service would call the gateway API with the token,
 *       not the card itself.</li>
 *   
 *   <li><b>Idempotency Keys:</b> Payment gateways require idempotency keys to prevent duplicate
 *       charges. Generate a unique key per payment request (e.g., orderId + timestamp hash) and
 *       store it in the database. If the request times out, retry with the same key. The gateway
 *       ensures the same key is charged only once.</li>
 *   
 *   <li><b>Settlement and Reconciliation:</b> Payment gateways don't settle immediately. Payments
 *       go through batches (daily settlement). This service should store payment status (pending,
 *       completed, failed) and reconciliation jobs should verify that payment status matches
 *       the gateway's records.</li>
 *   
 *   <li><b>Webhook Handling:</b> Payment gateways send webhooks to notify about payment events
 *       (payment.completed, payment.failed, refund.processed). You need a separate endpoint to
 *       handle these webhooks and update order status accordingly. This allows the gateway to
 *       update you even if the original request times out.</li>
 *   
 *   <li><b>Circuit Breaker Configuration:</b>
 *       <ul>
 *         <li>Failure Rate: 50% - Open circuit if > 50% requests fail</li>
 *         <li>Wait Duration: 30 seconds - Try half-open after 30 seconds</li>
 *         <li>Timeout: 5 seconds - Payment should complete within 5 seconds</li>
 *       </ul>
 *       Payment operations are critical, so we tolerate longer timeouts than inventory.</li>
 * </ul></p>
 *
 * <p><b>Interview Talking Points:</b>
 * <ul>
 *   <li>Q: "How do you prevent double-charging if the payment request times out?" A: We use
 *       idempotency keys. Each payment attempt has a unique idempotency key (e.g., orderId + nonce).
 *       If the request times out after the gateway processes it, the retry uses the same key.
 *       The gateway ensures it charges the customer only once per key.</li>
 *   
 *   <li>Q: "What if the payment fails and we don't know why?" A: We log the failure and let
 *       Temporal retry the activity. If all retries are exhausted, the workflow fails and
 *       compensates (releases inventory). The customer is notified of the failure. A separate
 *       reconciliation job can query the payment gateway to verify the actual payment status.</li>
 *   
 *   <li>Q: "How do you test payment failures in production?" A: We use the 30% failure rate
 *       to simulate real failures in staging/test environments. In production, payment failures
 *       are rare but do happen (network issues, gateway degradation). The 30% rate in demo
 *       helps us verify the Saga compensation logic works end-to-end.</li>
 *   
 *   <li>Q: "Can you process payments asynchronously?" A: Not entirely—you must confirm payment
 *       synchronously with the customer (to show success/failure). However, post-payment
 *       operations (reconciliation, settlement) can be asynchronous. Temporal's async workflow
 *       model supports this well: payment is done in an activity, then confirmation happens
 *       in a separate activity.</li>
 * </ul></p>
 *
 * @author OrderFlowX Team
 * @version 2.0
 * @since 1.0
 * @see io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /**
     * In-memory store for processed payments.
     * Format: paymentId -> orderId
     * In production, this would be a database table with payment status, amount, etc.
     */
    private static final ConcurrentHashMap<String, String> payments = new ConcurrentHashMap<>();

    private static final String CIRCUIT_BREAKER_NAME = "paymentServiceCircuitBreaker";
    private static final long SIMULATED_LATENCY_MS = 500;
    private static final double SIMULATED_FAILURE_RATE = 0.30; // 30% failure rate

    private static final Random random = new Random();

    /**
     * Processes payment for an order.
     *
     * <p><b>Business Logic:</b>
     * This method simulates calling a payment gateway to charge the customer's payment method.
     * In production, this would:
     * <ul>
     *   <li>Call the payment gateway API (Stripe, PayPal, etc.)</li>
     *   <li>Use an idempotency key to prevent duplicate charges</li>
     *   <li>Verify the payment with 3D Secure (if required)</li>
     *   <li>Store the payment ID in the database</li>
     *   <li>Publish payment.created event to Kafka</li>
     * </ul></p>
     *
     * <p><b>Failure Simulation:</b>
     * This method fails 30% of the time to simulate real payment gateway issues:
     * - Network timeouts
     * - Insufficient funds
     * - Card declined
     * - Gateway temporary errors (50x)</p>
     *
     * <p><b>Resilience4j Integration:</b>
     * The {@code @CircuitBreaker} annotation wraps this method. If failures exceed the
     * threshold (50%), the circuit breaker opens and new calls are fast-failed.</p>
     *
     * @param orderId the order identifier
     * @param amount the amount to charge (e.g., 99.99)
     * @return unique payment ID from the payment gateway
     *
     * @throws RuntimeException if payment processing fails or circuit breaker is open
     *
     * @see #processPaymentFallback(String, BigDecimal, Exception)
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "processPaymentFallback")
    public String processPayment(String orderId, BigDecimal amount) {
        log.info("PaymentService.processPayment called. orderId: {}, amount: {}", orderId, amount);

        // Simulate network latency
        try {
            Thread.sleep(SIMULATED_LATENCY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted during simulated latency");
        }

        // Simulate 30% failure rate
        if (random.nextDouble() < SIMULATED_FAILURE_RATE) {
            log.warn("Simulated payment failure for orderId: {}, amount: {}", orderId, amount);
            throw new RuntimeException("Payment declined by gateway. Reason: Simulated failure for testing.");
        }

        // Generate payment ID
        String paymentId = UUID.randomUUID().toString();
        payments.put(paymentId, orderId);

        log.info("Payment processed successfully. orderId: {}, amount: {}, paymentId: {}", orderId, amount, paymentId);
        return paymentId;
    }

    /**
     * Fallback method for processPayment when circuit breaker is open.
     *
     * <p><b>Fallback Strategy:</b>
     * When the circuit breaker is open (payment gateway is degraded), this fallback is invoked.
     * Currently, we fail immediately to signal that payment processing is unavailable.
     * In production, you might:
     * <ul>
     *   <li>Queue the payment for async retry (message queue)</li>
     *   <li>Route to a backup payment processor</li>
     *   <li>Offer the customer to retry later (send email with retry link)</li>
     * </ul></p>
     *
     * @param orderId the order identifier
     * @param amount the amount to charge
     * @param ex the circuit breaker exception
     * @return never returns; always throws exception
     * @throws RuntimeException indicating payment service is unavailable
     */
    private String processPaymentFallback(String orderId, BigDecimal amount, Exception ex) {
        log.warn("Circuit breaker is OPEN. processPayment falling back for orderId: {}, amount: {}", orderId, amount, ex);
        throw new RuntimeException("Payment service is currently unavailable. Please retry later.", ex);
    }
}
