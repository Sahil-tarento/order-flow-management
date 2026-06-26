package com.example.orderflowmanagement.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Implementation of Temporal workflow that orchestrates reserveInventory -> processPayment -> confirmOrder
 * with compensation steps on failure.
 *
 * Interview Talking Points:
 * - Uses Activity stubs to call external systems via activities
 * - Handles compensation explicitly by calling compensation activities
 * - Temporal provides visibility and retries for robust orchestration
 * - Must use Workflow.getLogger() instead of SLF4J for deterministic replay
 */
public class OrderWorkflowImpl implements OrderWorkflow {

    // IMPORTANT: Temporal workflows MUST use Workflow.getLogger() for deterministic replay
    private static final Logger log = Workflow.getLogger(OrderWorkflowImpl.class);

    private final ActivityOptions activityOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setScheduleToCloseTimeout(Duration.ofMinutes(5))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofMillis(500))
                    .setBackoffCoefficient(2.0)
                    .setMaximumAttempts(3)
                    .build())
            .build();

    private final OrderActivities activities = Workflow.newActivityStub(OrderActivities.class, activityOptions);

    @Override
    public void processOrder(String orderId, String customerId, BigDecimal amount, String currency) {
        String reservationId = null;
        String paymentId = null;
        try {
            reservationId = activities.reserveInventory(orderId);
            paymentId = activities.processPayment(orderId, amount);
            activities.confirmOrder(orderId);
        } catch (Exception e) {
            log.error("Workflow failure for orderId={}: {}", orderId, e.getMessage());
            // Compensation: release inventory if reserved
            try {
                if (reservationId != null) {
                    activities.releaseInventory(orderId, reservationId);
                }
            } catch (Exception ex) {
                log.error("Compensation releaseInventory failed for orderId={}: {}", orderId, ex.getMessage());
            }

            // Cancel order aggregate
            try {
                activities.cancelOrder(orderId, e.getMessage());
            } catch (Exception ex) {
                log.error("Compensation cancelOrder failed for orderId={}: {}", orderId, ex.getMessage());
            }
            // Rethrow to mark workflow as failed
            throw e;
        }
    }
}
