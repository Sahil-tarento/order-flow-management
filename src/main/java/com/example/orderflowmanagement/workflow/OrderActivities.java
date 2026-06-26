package com.example.orderflowmanagement.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.math.BigDecimal;

/**
 * Temporal activities used by OrderWorkflow. Activities are the bridge to external systems
 * and are executed outside the workflow thread.
 *
 * Interview Talking Points:
 * - Activities perform short-lived I/O-bound tasks (reserve inventory, payment)
 * - Activities should be idempotent because Temporal may retry them
 * - Compensation activities reverse the effects (releaseInventory, cancelOrder)
 */
@ActivityInterface
public interface OrderActivities {

    @ActivityMethod
    String reserveInventory(String orderId);

    @ActivityMethod
    String processPayment(String orderId, BigDecimal amount);

    @ActivityMethod
    void confirmOrder(String orderId);

    @ActivityMethod
    void releaseInventory(String orderId, String reservationId);

    @ActivityMethod
    void cancelOrder(String orderId, String reason);
}
