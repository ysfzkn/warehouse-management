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

    /**
     * Excel export source. Status/date are filtered in SQL and the customer is fetch-joined
     * (single-valued, so the row limit still applies in SQL) — the export used to read the
     * whole table with {@code PageRequest.of(0, Integer.MAX_VALUE)} and filter in Java.
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer " +
           "WHERE (:status IS NULL OR o.status = :status) " +
           "AND o.createdAt >= :from AND o.createdAt <= :to ORDER BY o.createdAt DESC")
    List<Order> findForExport(@Param("status") OrderStatus status,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to,
                              Pageable pageable);

    /**
     * Orders whose goods have physically left the warehouse recently — the third candidate pool
     * for the duplicate-delivery check, alongside customer-delivery transfers and manual stock
     * removals. Filtered by {@code updatedAt} so an old order that shipped this week still
     * counts; names are matched in Java (see {@link com.warehouse.util.TurkishText}).
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer " +
           "WHERE o.status IN :statuses AND o.updatedAt >= :since ORDER BY o.updatedAt DESC")
    List<Order> findRecentDispatched(@Param("statuses") java.util.Collection<OrderStatus> statuses,
                                     @Param("since") LocalDateTime since,
                                     Pageable pageable);

    // ─── Sales dashboard aggregates ───────────────────────────────────────────
    // These replace the previous "load every order and filter in Java" approach: the
    // dashboard polls every 30 seconds, so a full table scan per widget did not scale.

    /** {@code [status, orderCount, revenueSum]} for orders created at or after {@code from}. */
    @Query("SELECT o.status, COUNT(o), COALESCE(SUM(o.grandTotal), 0) FROM Order o " +
           "WHERE o.createdAt >= :from GROUP BY o.status")
    List<Object[]> aggregateByStatusSince(@Param("from") LocalDateTime from);

    /** {@code [orderCount, revenueSum]} for non-cancelled orders inside a half-open window. */
    @Query("SELECT COUNT(o), COALESCE(SUM(o.grandTotal), 0) FROM Order o " +
           "WHERE o.createdAt >= :from AND o.createdAt < :to AND o.status <> com.warehouse.enums.OrderStatus.CANCELLED")
    Object[] aggregateBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * {@code [createdAt, grandTotal]} of non-cancelled orders since {@code from} — two scalar
     * columns instead of hydrated entities, so day/hour bucketing stays cheap and portable.
     */
    @Query("SELECT o.createdAt, COALESCE(o.grandTotal, 0) FROM Order o " +
           "WHERE o.createdAt >= :from AND o.status <> com.warehouse.enums.OrderStatus.CANCELLED")
    List<Object[]> findCreatedAtAndTotalSince(@Param("from") LocalDateTime from);

    /** {@code [paymentMethod, orderCount, revenueSum]} across all non-cancelled orders. */
    @Query("SELECT o.paymentMethod, COUNT(o), COALESCE(SUM(o.grandTotal), 0) FROM Order o " +
           "WHERE o.status <> com.warehouse.enums.OrderStatus.CANCELLED AND o.paymentMethod IS NOT NULL " +
           "GROUP BY o.paymentMethod")
    List<Object[]> aggregateByPaymentMethod();

    /** {@code [productName, quantitySum, revenueSum]} — best sellers, highest revenue first. */
    @Query("SELECT p.name, COALESCE(SUM(i.quantity), 0), COALESCE(SUM(i.lineTotal), 0) " +
           "FROM OrderItem i JOIN i.order o JOIN i.product p " +
           "WHERE o.status <> com.warehouse.enums.OrderStatus.CANCELLED " +
           "GROUP BY p.name ORDER BY SUM(i.lineTotal) DESC")
    List<Object[]> aggregateTopProducts(Pageable pageable);
}
