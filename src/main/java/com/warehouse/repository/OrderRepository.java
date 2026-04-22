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

    /** Kargo webhook ile gelen shipment id'si ile Order arama. */
    Optional<Order> findByCargoProviderShipmentId(String cargoProviderShipmentId);

    /** Takip no ile Order arama — kargo firma-specific. */
    Optional<Order> findByCargoTrackingNo(String cargoTrackingNo);

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

    /**
     * Halka açık sipariş takip: sipariş numarası + müşteri e-postası ile sorgular.
     * E-posta, müşteri hesabına kayıtlı e-posta ile eşleşmelidir.
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c WHERE o.orderNumber = :orderNumber AND LOWER(c.email) = LOWER(:email)")
    Optional<Order> findByOrderNumberAndCustomerEmail(@Param("orderNumber") String orderNumber,
                                                      @Param("email") String email);
}
