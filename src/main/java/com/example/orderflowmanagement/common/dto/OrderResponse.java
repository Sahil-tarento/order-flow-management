package com.example.orderflowmanagement.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * HTTP Response DTO for order operations.
 * 
 * Interview Talking Points:
 * - Contains current state and location of newly created order
 * - Enables client to track order progress
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private String orderId;
    private String status;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private String failureReason;
    private String message;
}
