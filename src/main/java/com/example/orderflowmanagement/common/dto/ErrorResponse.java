package com.example.orderflowmanagement.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * HTTP Response DTO for error scenarios.
 * 
 * Interview Talking Points:
 * - Standardized error format across API
 * - Includes timestamp and trace ID for debugging
 * - Helps clients implement proper error handling
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {
    private String traceId;
    private int status;
    private String error;
    private String message;
    private Instant timestamp;
    private String path;
}
