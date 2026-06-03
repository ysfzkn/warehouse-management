package com.warehouse.repository;

import com.warehouse.entity.Order;
import com.warehouse.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Pessimistic write lock — SELECT ... FOR UPDATE.
     * Prevents the double-confirm race during concurrent state transitions
     * such as bank transfer confirmation / cancellation / expiry job.
     * Must be used within a transaction scope.
     */
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    /** Looks up an Order by the shipment id received via the cargo webhook. */
    Optional<Order> findByCargoProviderShipmentId(String cargoProviderShipmentId);

    /** Looks up an Order by tracking number — carrier-specific. */
    Optional<Order> findByCargoTrackingNo(String cargoTrackingNo);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer WHERE o.id = :id")
    Optional<Order> findByIdWithCustomer(@Param("id") Long id);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer")
    Page<Order> findAllWithCustomer(Pageable pageable);

    Page<Order> findByCustomerId(Long customerId, Pageable pageable);
    List<Order> findByCustomerId(Long customerId);
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime threshold);
    Optional<Order> findByStockTransferId(Long stockTransferId);
    long countByStatus(OrderStatus status);
    long countByCustomerId(Long customerId);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.paymentMethod = :paymentMethod AND o.bankTransferDeadline < :deadline")
    List<Order> findExpiredBankTransferOrders(@Param("status") com.warehouse.enums.OrderStatus status,
                                              @Param("paymentMethod") String paymentMethod,
                                              @Param("deadline") java.time.LocalDateTime deadline);

    /**
     * Public order tracking: queries by order number + customer e-mail.
     * The e-mail must match the one registered on the customer account.
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c WHERE o.orderNumber = :orderNumber AND LOWER(c.email) = LOWER(:email)")
    Optional<Order> findByOrderNumberAndCustomerEmail(@Param("orderNumber") String orderNumber,
                                                      @Param("email") String email);
}
