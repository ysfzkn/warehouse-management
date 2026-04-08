package com.warehouse.repository;

import com.warehouse.entity.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    Page<SupportTicket> findByCustomerId(Long customerId, Pageable pageable);

    @Query("SELECT t FROM SupportTicket t LEFT JOIN t.customer WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:search IS NULL OR LOWER(t.orderNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(t.topic) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<SupportTicket> findByFilters(@Param("status") String status, @Param("search") String search, Pageable pageable);

    long countByStatus(String status);
}
