package com.warehouse.repository;

import com.warehouse.entity.DeliveryReceipt;
import com.warehouse.enums.DeliveryReceiptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeliveryReceiptRepository extends JpaRepository<DeliveryReceipt, Long> {

    Optional<DeliveryReceipt> findByTransferId(Long transferId);

    Optional<DeliveryReceipt> findByReceiptNo(String receiptNo);

    List<DeliveryReceipt> findByTransferIdIn(List<Long> transferIds);

    /**
     * Backing query for the receipts screen. {@code attachmentCount} is projected here
     * rather than counted per row in the service, so listing 50 receipts stays one query
     * instead of 51.
     */
    @Query("""
            SELECT r FROM DeliveryReceipt r
            WHERE (:status IS NULL OR r.status = :status)
              AND (:hasSignedCopy IS NULL
                   OR (:hasSignedCopy = TRUE  AND SIZE(r.attachments) > 0)
                   OR (:hasSignedCopy = FALSE AND SIZE(r.attachments) = 0))
              AND (:from IS NULL OR r.issuedAt >= :from)
              AND (:to   IS NULL OR r.issuedAt <= :to)
              AND (:search IS NULL OR :search = '' OR
                   LOWER(r.receiptNo)         LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(r.customerFullName)  LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(r.customerPhone)     LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(r.driverName)        LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(r.vehiclePlate)      LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(r.orderNumber)       LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<DeliveryReceipt> search(@Param("status") DeliveryReceiptStatus status,
                                 @Param("hasSignedCopy") Boolean hasSignedCopy,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 @Param("search") String search,
                                 Pageable pageable);

    long countByStatus(DeliveryReceiptStatus status);

    /** Receipts still waiting for the signed page to come back — the admin dashboard number. */
    @Query("SELECT COUNT(r) FROM DeliveryReceipt r WHERE SIZE(r.attachments) = 0 AND r.status <> com.warehouse.enums.DeliveryReceiptStatus.CANCELLED")
    long countAwaitingSignedCopy();
}
