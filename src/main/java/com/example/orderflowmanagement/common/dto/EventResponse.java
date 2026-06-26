package com.example.orderflowmanagement.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * HTTP Response DTO for event history endpoint.
 * 
 * Interview Talking Points:
 * - Shows complete audit trail of order
 * - Demonstrates event sourcing benefits
 * - Can be replayed to reconstruct order state
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventResponse {
    private String orderId;
    private String eventType;
    private String eventData;
    private LocalDateTime occurredAt;
}
