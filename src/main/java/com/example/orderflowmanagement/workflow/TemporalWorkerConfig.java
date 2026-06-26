package com.example.orderflowmanagement.workflow;

import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Temporal worker configuration that registers both workflow and activity implementations.
 * Activities are Spring-managed beans and therefore injected here.
 *
 * Interview Talking Points:
 * - Registers activities to worker so Temporal can execute side-effects
 * - Keeps worker lifecycle tied to Spring application lifecycle
 * - In production, workers are scaled independently and should be idempotent
 */
@Component
public class TemporalWorkerConfig {

    private static final Logger log = LoggerFactory.getLogger(TemporalWorkerConfig.class);
    private static final String TASK_QUEUE = "ORDER_PROCESSING_TASK_QUEUE";

    private final WorkflowClient workflowClient;
    private final OrderActivitiesImpl orderActivitiesImpl;

    private WorkerFactory factory;

    public TemporalWorkerConfig(WorkflowClient workflowClient, OrderActivitiesImpl orderActivitiesImpl) {
        this.workflowClient = workflowClient;
        this.orderActivitiesImpl = orderActivitiesImpl;
    }

    @PostConstruct
    public void start() {
        log.info("Starting Temporal worker for {} and registering activities", TASK_QUEUE);
        this.factory = WorkerFactory.newInstance(workflowClient);
        Worker worker = factory.newWorker(TASK_QUEUE);

        worker.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);
        worker.registerActivitiesImplementations(orderActivitiesImpl);

        factory.start();
        log.info("Temporal worker started and activities registered on queue: {}", TASK_QUEUE);
    }

    @PreDestroy
    public void stop() {
        try {
            if (factory != null) {
                log.info("Shutting down Temporal worker factory");
                factory.shutdown();
            }
        } catch (Exception e) {
            log.warn("Error shutting down Temporal worker: {}", e.getMessage());
        }
    }
}
