package com.warehouse.repository;

import com.warehouse.entity.DeliveryReceipt;
import com.warehouse.enums.DeliveryReceiptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Note the absence of a filtering query here.
 *
 * <p>The receipts screen used to be backed by one JPQL statement with a
 * {@code (:param IS NULL OR ...)} branch per filter. That runs on H2 and fails on
 * PostgreSQL: a bare parameter in {@code $n IS NULL} has no type context, so the server
 * rejects the whole statement with {@code 42P18 could not determine data type of
 * parameter}. Every test passed and the page was blank in production.</p>
 *
 * <p>The filters are built as a {@link org.springframework.data.jpa.domain.Specification}
 * in {@code DeliveryReceiptServiceImpl} instead — a predicate is only added when the
 * caller actually supplied that filter, so no null-typed parameter ever reaches the
 * database and the statement carries only the conditions in use.</p>
 */
public interface DeliveryReceiptRepository
        extends JpaRepository<DeliveryReceipt, Long>,
                JpaSpecificationExecutor<DeliveryReceipt> {

    Optional<DeliveryReceipt> findByTransferId(Long transferId);

    Optional<DeliveryReceipt> findByReceiptNo(String receiptNo);

    List<DeliveryReceipt> findByTransferIdIn(List<Long> transferIds);

    long countByStatus(DeliveryReceiptStatus status);

    /** Receipts still waiting for the signed page to come back — the admin dashboard number. */
    @Query("SELECT COUNT(r) FROM DeliveryReceipt r WHERE SIZE(r.attachments) = 0 AND r.status <> com.warehouse.enums.DeliveryReceiptStatus.CANCELLED")
    long countAwaitingSignedCopy();
}
