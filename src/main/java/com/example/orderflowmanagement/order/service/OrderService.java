package com.example.orderflowmanagement.order.service;

import com.example.orderflowmanagement.common.domain.*;
import com.example.orderflowmanagement.common.exceptions.DomainException;
import com.example.orderflowmanagement.common.exceptions.OrderNotFoundException;
import com.example.orderflowmanagement.eventstore.service.EventStoreService;
import com.example.orderflowmanagement.kafka.producer.EventPublisher;
import com.example.orderflowmanagement.order.entity.OrderEntity;
import com.example.orderflowmanagement.order.repository.OrderRepository;
import com.example.orderflowmanagement.redis.service.RedisService;
import com.example.orderflowmanagement.workflow.OrderWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final RedisService redisService;
    private final EventStoreService eventStoreService;
    private final EventPublisher eventPublisher;
    private final WorkflowClient workflowClient;
    private final OrderRepository orderRepository;

    private static final String IDEMPOTENCY_KEY_PREFIX = "order:idempotency:";
    private static final long IDEMPOTENCY_TTL_SECONDS = 3600; // 1 hour
    private static final String ORDER_WORKFLOW_QUEUE = "ORDER_PROCESSING_TASK_QUEUE";

    /**
     * Creates a new order and initiates the order processing workflow.
     *
     * <p><b>Algorithm:</b>
     * <ol>
     *   <li>Validate input parameters (customerId, amount, currency)</li>
     *   <li>Check idempotency: if idempotencyKey exists in Redis, return cached order</li>
     *   <li>Acquire distributed lock to prevent race conditions</li>
     *   <li>Generate unique order ID (UUID)</li>
     *   <li>Create OrderCreated event and persist to event store</li>
     *   <li>Cache result with idempotencyKey for idempotency</li>
     *   <li>Submit Temporal workflow for async order processing</li>
     *   <li>Publish OrderCreated event to Kafka consumers</li>
     *   <li>Release distributed lock</li>
     *   <li>Return order ID to caller</li>
     * </ol></p>
     *
     * @param customerId unique identifier of the customer placing the order
     * @param amount the order amount (e.g., 99.99)
     * @param currency the currency code (e.g., "USD", "EUR")
     * @param idempotencyKey unique key to ensure idempotency across retries
     * @return the generated orderId (UUID string)
     *
     * @throws IllegalArgumentException if customerId is null/blank, amount is invalid, or currency is invalid
     * @throws DomainException if event store persistence fails or workflow submission fails
     */
    public String createOrder(String customerId, BigDecimal amount, String currency, String idempotencyKey) {
        log.info("Creating order for customer: {}, amount: {}, currency: {}, idempotencyKey: {}",
                 customerId, amount, currency, idempotencyKey);

        // Input validation
        validateOrderInput(customerId, amount, currency, idempotencyKey);

        String idempotencyKeyFull = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        // Check if order already created with this idempotency key
        String cachedOrderId = redisService.get(idempotencyKeyFull);
        if (cachedOrderId != null) {
            log.info("Idempotent request detected. Returning cached order ID: {}", cachedOrderId);
            return cachedOrderId;
        }

        // Acquire distributed lock for idempotency
        String lockKey = IDEMPOTENCY_KEY_PREFIX + "lock:" + idempotencyKey;
        if (!redisService.setIfAbsent(lockKey, "locked", 5)) {
            throw new DomainException("Failed to acquire lock for order creation. Concurrent request detected.");
        }

        try {
            String orderId = UUID.randomUUID().toString();

            // Create domain event
            OrderCreatedEvent orderCreatedEvent = OrderCreatedEvent.create(orderId, customerId, amount, currency);

            // Persist event to event store
            eventStoreService.appendEvent(orderId, orderCreatedEvent, null, null);
            log.debug("OrderCreated event persisted to event store. orderId: {}", orderId);

            // Persist order projection
            OrderEntity orderEntity = OrderEntity.builder()
                    .orderId(orderId)
                    .customerId(customerId)
                    .amount(amount)
                    .currency(currency)
                    .status(OrderStatus.PENDING.name())
                    .build();
            orderRepository.save(orderEntity);

            // Cache the order ID with idempotency key
            redisService.set(idempotencyKeyFull, orderId, IDEMPOTENCY_TTL_SECONDS);
            log.debug("Order ID cached with idempotency key. TTL: {} seconds", IDEMPOTENCY_TTL_SECONDS);

            // Submit Temporal workflow for async processing
            startOrderWorkflow(orderId, customerId, amount, currency);

            // Publish event to Kafka for other services
            eventPublisher.publishOrderEvent(orderId, orderCreatedEvent);
            log.info("Order created successfully. orderId: {}, workflow submitted to Temporal", orderId);

            return orderId;
        } catch (Exception e) {
            log.error("Error creating order. Idempotency key: {}", idempotencyKey, e);
            throw new DomainException("Failed to create order: " + e.getMessage(), e);
        } finally {
            // Release distributed lock
            redisService.delete(lockKey);
        }
    }

    /**
     * Retrieves an order by its ID from the projection table.
     *
     * @param orderId the unique order identifier
     * @return the OrderEntity
     * @throws OrderNotFoundException if the orderId doesn't exist
     */
    public OrderEntity getOrder(String orderId) {
        log.debug("Retrieving order: {}", orderId);
        validateOrderId(orderId);

        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /**
     * Retrieves the complete event history for an order from the event store.
     *
     * @param orderId the unique order identifier
     * @return list of all OrderEvent objects for this order, ordered by version
     * @throws OrderNotFoundException if the orderId doesn't exist
     */
    public List<OrderEvent> getOrderEvents(String orderId) {
        log.debug("Retrieving events for order: {}", orderId);
        validateOrderId(orderId);

        List<OrderEvent> events = eventStoreService.getEventsByAggregateId(orderId);
        if (events.isEmpty()) {
            log.warn("No events found for order: {}", orderId);
            throw new OrderNotFoundException(orderId);
        }

        log.info("Retrieved {} events for order: {}", events.size(), orderId);
        return events;
    }

    /**
     * Replays all events for an order to rebuild its current state.
     *
     * @param orderId the unique order identifier
     * @return the reconstructed OrderEntity representing the final state after replaying all events
     * @throws OrderNotFoundException if the orderId doesn't exist
     */
    public OrderEntity replayOrderEvents(String orderId) {
        log.info("Replaying events for order: {}", orderId);
        validateOrderId(orderId);

        List<OrderEvent> events = eventStoreService.getEventsByAggregateId(orderId);
        if (events.isEmpty()) {
            throw new OrderNotFoundException(orderId);
        }

        OrderEntity rebuiltOrder = rebuildOrderFromEvents(orderId, events);
        log.info("Event replay completed for order: {}. Final status: {}", orderId, rebuiltOrder.getStatus());

        return rebuiltOrder;
    }

    /**
     * Reconstructs the current order state by applying all events in sequence.
     */
    private OrderEntity rebuildOrderFromEvents(String orderId, List<OrderEvent> events) {
        if (events.isEmpty()) {
            throw new DomainException("Cannot rebuild order with zero events");
        }

        OrderEntity entity = new OrderEntity();
        entity.setOrderId(orderId);

        for (OrderEvent event : events) {
            applyEvent(entity, event);
        }

        log.debug("Order rebuilt from {} events. Final status: {}", events.size(), entity.getStatus());
        return entity;
    }

    /**
     * Applies a single event to the order entity, transitioning its state.
     * Uses Java 21 pattern matching on the sealed interface hierarchy.
     */
    private void applyEvent(OrderEntity entity, OrderEvent event) {
        switch (event) {
            case OrderCreatedEvent e -> {
                entity.setCustomerId(e.customerId());
                entity.setAmount(e.amount());
                entity.setCurrency(e.currency());
                entity.setStatus(OrderStatus.PENDING.name());
                entity.setCreatedAt(e.occurredAt());
            }
            case InventoryReservedEvent e -> {
                entity.setStatus(OrderStatus.INVENTORY_RESERVED.name());
                entity.setInventoryReservationId(e.reservationId());
            }
            case InventoryReservationFailedEvent e -> {
                entity.setStatus(OrderStatus.INVENTORY_RESERVATION_FAILED.name());
                entity.setFailureReason(e.reason());
            }
            case PaymentProcessedEvent e -> {
                entity.setStatus(OrderStatus.PAYMENT_PROCESSED.name());
                entity.setPaymentId(e.paymentId());
            }
            case PaymentFailedEvent e -> {
                entity.setStatus(OrderStatus.PAYMENT_FAILED.name());
                entity.setFailureReason(e.reason());
            }
            case OrderConfirmedEvent e -> {
                entity.setStatus(OrderStatus.CONFIRMED.name());
                entity.setConfirmedAt(e.occurredAt());
            }
            case OrderCancelledEvent e -> {
                entity.setStatus(OrderStatus.CANCELLED.name());
                entity.setCancellationReason(e.reason());
            }
            case InventoryReleasedEvent e -> {
                // Inventory released as compensation — status already set by prior failure event
                log.debug("Inventory released event applied for order: {}", e.orderId());
            }
        }
    }

    /**
     * Starts the Temporal workflow for order processing.
     */
    private void startOrderWorkflow(String orderId, String customerId, BigDecimal amount, String currency) {
        try {
            WorkflowOptions options = WorkflowOptions.newBuilder()
                    .setTaskQueue(ORDER_WORKFLOW_QUEUE)
                    .setWorkflowId("order-" + orderId)
                    .build();

            OrderWorkflow workflow = workflowClient.newWorkflowStub(OrderWorkflow.class, options);
            WorkflowClient.start(workflow::processOrder, orderId, customerId, amount, currency);

            log.info("Temporal workflow submitted for order: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to submit Temporal workflow for order: {}", orderId, e);
            throw new DomainException("Failed to start order processing workflow: " + e.getMessage(), e);
        }
    }

    /**
     * Validates the input parameters for order creation.
     */
    private void validateOrderInput(String customerId, BigDecimal amount, String currency, String idempotencyKey) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be null or blank");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be null or blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key cannot be null or blank");
        }
    }

    /**
     * Validates the order ID format.
     */
    private void validateOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID cannot be null or blank");
        }
    }
}
