package com.warehouse.repository;

import com.warehouse.entity.Invoice;
import com.warehouse.enums.InvoiceStatus;
import com.warehouse.enums.InvoiceType;
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
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByOrderId(Long orderId);

    List<Invoice> findByOrderIdIn(List<Long> orderIds);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    @Query("""
        SELECT i FROM Invoice i
        JOIN i.order o
        WHERE (:status IS NULL OR i.status = :status)
          AND (:invoiceType IS NULL OR i.invoiceType = :invoiceType)
          AND (:search IS NULL OR (
                LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(i.recipientName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :search, '%'))
          ))
          AND i.createdAt >= :from
          AND i.createdAt <= :to
        ORDER BY i.createdAt DESC
    """)
    Page<Invoice> findByFilters(
            @Param("status") InvoiceStatus status,
            @Param("invoiceType") InvoiceType invoiceType,
            @Param("search") String search,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    long countByStatus(InvoiceStatus status);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.createdAt >= :from AND i.createdAt <= :to")
    long countByDateRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
