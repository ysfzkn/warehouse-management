package com.warehouse.repository;

import com.warehouse.entity.Order;
import com.warehouse.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer WHERE o.id = :id")
    Optional<Order> findByIdWithCustomer(@Param("id") Long id);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer")
    Page<Order> findAllWithCustomer(Pageable pageable);

    Page<Order> findByCustomerId(Long customerId, Pageable pageable);
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime threshold);
    Optional<Order> findByStockTransferId(Long stockTransferId);
    long countByStatus(OrderStatus status);
    long countByCustomerId(Long customerId);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.paymentMethod = :paymentMethod AND o.bankTransferDeadline < :deadline")
    List<Order> findExpiredBankTransferOrders(@Param("status") com.warehouse.enums.OrderStatus status,
                                              @Param("paymentMethod") String paymentMethod,
                                              @Param("deadline") java.time.LocalDateTime deadline);
}
