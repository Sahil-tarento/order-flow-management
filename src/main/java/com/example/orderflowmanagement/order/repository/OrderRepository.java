package com.example.orderflowmanagement.order.repository;

import com.example.orderflowmanagement.order.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for Order entities.
 * 
 * Interview Talking Points:
 * - Provides CRUD operations and query methods
 * - findByOrderId enables efficient lookup by domain ID
 * - Automatically implements pagination, sorting
 * - SQL queries are optimized by Hibernate
 */
@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    Optional<OrderEntity> findByOrderId(String orderId);
}
