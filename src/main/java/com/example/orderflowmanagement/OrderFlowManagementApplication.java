package com.example.orderflowmanagement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class OrderFlowManagementApplication {

    /**
     * Main entry point for the Spring Boot application.
     *
     * <p><b>Startup Sequence:</b>
     * <ol>
     *   <li>Load application properties from application.properties</li>
     *   <li>Initialize Spring context (component scan, bean creation)</li>
     *   <li>Auto-configure Spring Boot features (DataSource, JPA, Kafka, Redis)</li>
     *   <li>Execute custom bean factories (WorkflowClient creation)</li>
     *   <li>Start embedded server (Tomcat on port 8080 by default)</li>
     *   <li>Log application startup completion</li>
     * </ol></p>
     *
     * @param args command-line arguments (e.g., --server.port=9090)
     */
    public static void main(String[] args) {
        log.info("Starting OrderFlowX Application...");
        SpringApplication.run(OrderFlowManagementApplication.class, args);
        log.info("OrderFlowX Application started successfully");
    }
}
