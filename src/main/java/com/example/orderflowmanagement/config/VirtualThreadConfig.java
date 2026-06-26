package com.example.orderflowmanagement.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Virtual Threads configuration for Java 21.
 * 
 * Interview Talking Points:
 * - Virtual threads provide efficient concurrency for I/O-bound tasks
 * - newVirtualThreadPerTaskExecutor creates a new virtual thread per task
 * - 10,000+ concurrent connections with minimal memory footprint
 * - Perfect for handling Kafka consumers, HTTP calls, DB queries
 * - No context switching overhead like platform threads
 * 
 * Scale Benefits:
 * - Old (platform threads): 100-1000 concurrent requests
 * - New (virtual threads): 100,000+ concurrent requests
 * - Memory per thread: 1-2MB (platform) vs 1-10KB (virtual)
 */
@Configuration
@EnableAsync
public class VirtualThreadConfig {

    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        return java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(name = "kafkaConsumerExecutor")
    public Executor kafkaConsumerExecutor() {
        // Dedicated executor for Kafka consumers
        return java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    }
}
