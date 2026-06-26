package com.example.orderflowmanagement.config;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Temporal Workflow configuration.
 * 
 * Interview Talking Points:
 * - Temporal provides reliable workflow orchestration for sagas
 * - Handles retries, timeouts, and compensation automatically
 * - Decouples from Kafka for long-running workflows
 * - Enables viewing workflow state in Temporal UI
 */
@Configuration
public class TemporalConfig {

    @Value("${temporal.host:localhost}")
    private String temporalHost;

    @Value("${temporal.port:7233}")
    private int temporalPort;

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(temporalHost + ":" + temporalPort)
                        .build()
        );
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs workflowServiceStubs) {
        return WorkflowClient.newInstance(workflowServiceStubs);
    }
}
