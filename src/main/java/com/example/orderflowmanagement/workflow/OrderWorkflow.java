package com.example.orderflowmanagement.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.math.BigDecimal;

/**
 * Temporal workflow interface for order processing. Defines orchestrated saga steps.
 *
 * Interview Talking Points:
 * - Workflow represents the long-running saga orchestration
 * - Durable state and retries handled by Temporal without custom code
 * - Keeps compensation and business orchestration explicit
 */
@WorkflowInterface
public interface OrderWorkflow {

    @WorkflowMethod
    void processOrder(String orderId, String customerId, BigDecimal amount, String currency);
}
