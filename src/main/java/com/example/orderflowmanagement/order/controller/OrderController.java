package com.example.orderflowmanagement.order.controller;

import com.example.orderflowmanagement.common.domain.OrderEvent;
import com.example.orderflowmanagement.common.dto.CreateOrderRequest;
import com.example.orderflowmanagement.common.dto.EventResponse;
import com.example.orderflowmanagement.common.dto.OrderResponse;
import com.example.orderflowmanagement.order.entity.OrderEntity;
import com.example.orderflowmanagement.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <h2>OrderController - REST API for Order Operations</h2>
 *
 * <p><b>Architecture Role:</b>
 * OrderController exposes the order management functionality via REST endpoints. It acts as the
 * API boundary between clients (web, mobile, third-party integrations) and the order processing
 * business logic.</p>
 *
 * @author OrderFlowX Team
 * @version 2.0
 * @since 1.0
 * @see OrderService
 * @see CreateOrderRequest
 * @see OrderResponse
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
@Tag(name = "Orders", description = "Order management endpoints")
public class OrderController {

    private final OrderService orderService;

    /**
     * Creates a new order and initiates workflow processing.
     */
    @PostMapping
    @Operation(summary = "Create a new order", description = "Creates a new order and initiates the order processing workflow asynchronously")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Order accepted for processing",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid order request"),
            @ApiResponse(responseCode = "409", description = "Order already exists for the given idempotency key"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {

        log.info("POST /api/orders - Creating order for customer: {}, amount: {}, currency: {}",
                 request.getCustomerId(), request.getAmount(), request.getCurrency());

        String orderId = orderService.createOrder(
                request.getCustomerId(),
                request.getAmount(),
                request.getCurrency(),
                request.getIdempotencyKey()
        );

        OrderResponse response = OrderResponse.builder()
                .orderId(orderId)
                .status("PENDING")
                .message("Order created successfully. Processing initiated.")
                .build();

        log.info("Order created successfully. orderId: {}", orderId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Retrieves the current state of an order.
     */
    @GetMapping("/{orderId}")
    @Operation(summary = "Get order details", description = "Retrieves the current state of an order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable
            @Parameter(description = "The unique order identifier", example = "123e4567-e89b-12d3-a456-426614174000")
            String orderId) {

        log.info("GET /api/orders/{} - Retrieving order", orderId);

        OrderEntity order = orderService.getOrder(orderId);

        OrderResponse response = OrderResponse.builder()
                .orderId(order.getOrderId())
                .customerId(order.getCustomerId())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .confirmedAt(order.getConfirmedAt())
                .failureReason(order.getFailureReason())
                .build();

        log.info("Order retrieved successfully. orderId: {}, status: {}", orderId, order.getStatus());
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the complete event history for an order.
     */
    @GetMapping("/{orderId}/events")
    @Operation(summary = "Get order event history", description = "Retrieves the complete audit trail of events for an order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<EventResponse>> getOrderEvents(
            @PathVariable
            @Parameter(description = "The unique order identifier", example = "123e4567-e89b-12d3-a456-426614174000")
            String orderId) {

        log.info("GET /api/orders/{}/events - Retrieving event history", orderId);

        List<OrderEvent> events = orderService.getOrderEvents(orderId);

        List<EventResponse> responses = events.stream()
                .map(event -> EventResponse.builder()
                        .orderId(event.orderId())
                        .eventType(event.eventType())
                        .occurredAt(event.occurredAt())
                        .build())
                .collect(Collectors.toList());

        log.info("Event history retrieved. orderId: {}, eventCount: {}", orderId, responses.size());
        return ResponseEntity.ok(responses);
    }

    /**
     * Replays events to rebuild the order state.
     */
    @GetMapping("/{orderId}/replay")
    @Operation(summary = "Replay order events", description = "Replays all events for an order to rebuild its final state (admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Replay successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrderResponse> replayOrderEvents(
            @PathVariable
            @Parameter(description = "The unique order identifier", example = "123e4567-e89b-12d3-a456-426614174000")
            String orderId) {

        log.info("GET /api/orders/{}/replay - Replaying events for order", orderId);

        OrderEntity replayed = orderService.replayOrderEvents(orderId);

        OrderResponse response = OrderResponse.builder()
                .orderId(replayed.getOrderId())
                .customerId(replayed.getCustomerId())
                .amount(replayed.getAmount())
                .currency(replayed.getCurrency())
                .status(replayed.getStatus())
                .message("Event replay completed successfully")
                .build();

        log.info("Event replay completed. orderId: {}, finalStatus: {}", orderId, replayed.getStatus());
        return ResponseEntity.ok(response);
    }
}
