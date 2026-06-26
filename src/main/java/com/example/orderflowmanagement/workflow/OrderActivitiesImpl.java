package com.example.orderflowmanagement.workflow;

import com.example.orderflowmanagement.common.domain.*;
import com.example.orderflowmanagement.eventstore.service.EventStoreService;
import com.example.orderflowmanagement.kafka.producer.EventPublisher;
import com.example.orderflowmanagement.order.repository.OrderRepository;
import com.example.orderflowmanagement.service.InventoryService;
import com.example.orderflowmanagement.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Activity implementations bridge to InventoryService and PaymentService, persist events
 * and publish to Kafka via EventPublisher.
 *
 * Interview Talking Points:
 * - Activities are where side-effects occur; workflows orchestrate these calls
 * - Use EventStoreService to persist events prior to publishing to Kafka for consistency
 * - Use Resilience4j at service level for circuit-breaking external calls
 */
@Component
public class OrderActivitiesImpl implements OrderActivities {

    private static final Logger log = LoggerFactory.getLogger(OrderActivitiesImpl.class);

    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final EventStoreService eventStoreService;
    private final EventPublisher eventPublisher;
    private final OrderRepository orderRepository;

    public OrderActivitiesImpl(InventoryService inventoryService,
                               PaymentService paymentService,
                               EventStoreService eventStoreService,
                               EventPublisher eventPublisher,
                               OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.eventStoreService = eventStoreService;
        this.eventPublisher = eventPublisher;
        this.orderRepository = orderRepository;
    }

    @Override
    public String reserveInventory(String orderId) {
        String reservationId = inventoryService.reserveInventory(orderId);
        InventoryReservedEvent evt = InventoryReservedEvent.create(orderId, reservationId);
        eventStoreService.appendEvent(orderId, evt, null, null);
        eventPublisher.publishOrderEvent(orderId, evt);

        // Update order projection
        orderRepository.findByOrderId(orderId).ifPresent(o -> {
            o.setStatus(OrderStatus.INVENTORY_RESERVED.name());
            o.setInventoryReservationId(reservationId);
            orderRepository.save(o);
        });

        log.info("Inventory reserved: {} -> {}", orderId, reservationId);
        return reservationId;
    }

    @Override
    public String processPayment(String orderId, BigDecimal amount) {
        String paymentId = paymentService.processPayment(orderId, amount);
        PaymentProcessedEvent evt = PaymentProcessedEvent.create(orderId, paymentId, "txn-" + paymentId);
        eventStoreService.appendEvent(orderId, evt, null, null);
        eventPublisher.publishOrderEvent(orderId, evt);

        // Update order projection
        orderRepository.findByOrderId(orderId).ifPresent(o -> {
            o.setStatus(OrderStatus.PAYMENT_PROCESSED.name());
            o.setPaymentId(paymentId);
            orderRepository.save(o);
        });

        log.info("Payment processed: {} -> {}", orderId, paymentId);
        return paymentId;
    }

    @Override
    public void confirmOrder(String orderId) {
        OrderConfirmedEvent evt = OrderConfirmedEvent.create(orderId);
        eventStoreService.appendEvent(orderId, evt, null, null);
        eventPublisher.publishOrderEvent(orderId, evt);

        // Update order projection
        orderRepository.findByOrderId(orderId).ifPresent(o -> {
            o.setStatus(OrderStatus.CONFIRMED.name());
            orderRepository.save(o);
        });

        log.info("Order confirmed: {}", orderId);
    }

    @Override
    public void releaseInventory(String orderId, String reservationId) {
        inventoryService.releaseInventory(reservationId);
        InventoryReleasedEvent evt = InventoryReleasedEvent.create(orderId, reservationId, "Compensation: payment failed");
        eventStoreService.appendEvent(orderId, evt, null, null);
        eventPublisher.publishOrderEvent(orderId, evt);
        log.info("Inventory released for compensation: {} -> {}", orderId, reservationId);
    }

    @Override
    public void cancelOrder(String orderId, String reason) {
        OrderCancelledEvent evt = OrderCancelledEvent.create(orderId, reason);
        eventStoreService.appendEvent(orderId, evt, null, null);
        eventPublisher.publishOrderEvent(orderId, evt);

        // Update order projection
        orderRepository.findByOrderId(orderId).ifPresent(o -> {
            o.setStatus(OrderStatus.CANCELLED.name());
            o.setCancellationReason(reason);
            orderRepository.save(o);
        });

        log.info("Order cancelled: {} reason={}", orderId, reason);
    }
}
