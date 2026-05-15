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

    /**
     * PostgreSQL + Hibernate 6 type-inference fix: bir sorgu parametresi {@code null}
     * geldiğinde PG onun tipini {@code bytea} olarak çıkarır ve {@code lower(bytea)}
     * fonksiyonu olmadığı için patlar. {@code CAST(:search AS string)} açık tip ipucu
     * verir — aynı pattern {@code ProductRepository.findByFilters}'ta da kullanılıyor.
     */
    @Query("""
        SELECT i FROM Invoice i
        JOIN i.order o
        WHERE (:status IS NULL OR i.status = :status)
          AND (:invoiceType IS NULL OR i.invoiceType = :invoiceType)
          AND (:search IS NULL OR (
                LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                OR LOWER(i.recipientName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
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

    /**
     * PENDING durumundaki ve Logo'ya gönderilmiş (providerInvoiceId != null) faturaları
     * döner. InvoiceStatusPollingJob tarafından kullanılır.
     */
    @Query("""
        SELECT i FROM Invoice i
         WHERE i.status = :status
           AND i.providerInvoiceId IS NOT NULL
         ORDER BY i.createdAt ASC
    """)
    List<Invoice> findByStatusWithProviderId(@Param("status") InvoiceStatus status, Pageable pageable);

    /** Admin digest için: son 24 saat içinde oluşturulan ERROR veya REJECTED faturalar. */
    @Query("""
        SELECT i FROM Invoice i
         WHERE (i.status = com.warehouse.enums.InvoiceStatus.ERROR
             OR i.status = com.warehouse.enums.InvoiceStatus.REJECTED)
           AND i.updatedAt >= :since
         ORDER BY i.updatedAt DESC
    """)
    List<Invoice> findRecentErrors(@Param("since") LocalDateTime since);

    /** Admin digest için: 24 saatten uzun süredir PENDING kalan faturalar. */
    @Query("""
        SELECT i FROM Invoice i
         WHERE i.status = com.warehouse.enums.InvoiceStatus.PENDING
           AND i.createdAt < :olderThan
         ORDER BY i.createdAt ASC
    """)
    List<Invoice> findStuckPending(@Param("olderThan") LocalDateTime olderThan);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.createdAt >= :from AND i.createdAt <= :to")
    long countByDateRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Bir orijinal faturanın tüm credit note'larını döner. */
    @Query("SELECT i FROM Invoice i WHERE i.creditedInvoice.id = :originalId ORDER BY i.createdAt DESC")
    List<Invoice> findByCreditedInvoiceId(@Param("originalId") Long originalInvoiceId);
}
