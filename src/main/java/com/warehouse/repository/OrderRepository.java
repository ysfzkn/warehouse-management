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

    /** True if the customer has a non-cancelled order containing the product (review eligibility). */
    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END FROM Order o JOIN o.items i " +
           "WHERE o.customer.id = :customerId AND i.product.id = :productId " +
           "AND o.status <> com.warehouse.enums.OrderStatus.CANCELLED")
    boolean hasPurchasedProduct(@Param("customerId") Long customerId, @Param("productId") Long productId);

    /** Ids of the customer's non-cancelled orders containing the product, newest first. */
    @Query("SELECT o.id FROM Order o JOIN o.items i " +
           "WHERE o.customer.id = :customerId AND i.product.id = :productId " +
           "AND o.status <> com.warehouse.enums.OrderStatus.CANCELLED ORDER BY o.createdAt DESC")
    List<Long> eligibleOrderIds(@Param("customerId") Long customerId, @Param("productId") Long productId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.paymentMethod = :paymentMethod AND o.bankTransferDeadline < :deadline")
    List<Order> findExpiredBankTransferOrders(@Param("status") com.warehouse.enums.OrderStatus status,
                                              @Param("paymentMethod") String paymentMethod,
                                              @Param("deadline") java.time.LocalDateTime deadline);

    @Query("SELECT o FROM Order o WHERE o.status = com.warehouse.enums.OrderStatus.PENDING_PAYMENT " +
           "AND o.paymentReminderAt IS NOT NULL AND o.paymentReminderAt <= :now AND o.paymentReminderSentAt IS NULL")
    List<Order> findPaymentRemindersDue(@Param("now") LocalDateTime now);
    Optional<Order> findByCustomerConfirmationTokenHash(String customerConfirmationTokenHash);

    /**
     * Public order tracking: queries by order number + customer e-mail.
     * The e-mail must match the one registered on the customer account.
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c WHERE o.orderNumber = :orderNumber AND LOWER(c.email) = LOWER(:email)")
    Optional<Order> findByOrderNumberAndCustomerEmail(@Param("orderNumber") String orderNumber,
                                                      @Param("email") String email);
}
