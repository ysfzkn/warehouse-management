package com.warehouse.repository;

import com.warehouse.entity.StockEvent;
import com.warehouse.enums.StockEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

@Repository
public interface StockEventRepository extends JpaRepository<StockEvent, Long> {
    Page<StockEvent> findByProductId(Long productId, Pageable pageable);
    Page<StockEvent> findByStockId(Long stockId, Pageable pageable);
    Page<StockEvent> findByEventType(StockEventType eventType, Pageable pageable);

    @Query("""
        SELECT e FROM StockEvent e
        WHERE (:source IS NULL OR e.source = :source)
          AND (:eventType IS NULL OR e.eventType = :eventType)
          AND (:productId IS NULL OR e.productId = :productId)
          AND (:startDate IS NULL OR e.createdAt >= :startDate)
          AND (:endDate IS NULL OR e.createdAt <= :endDate)
          AND (:search IS NULL OR LOWER(e.sourceDetail) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    Page<StockEvent> findByFilters(
        @org.springframework.data.repository.query.Param("source") com.warehouse.enums.StockEventSource source,
        @org.springframework.data.repository.query.Param("eventType") StockEventType eventType,
        @org.springframework.data.repository.query.Param("productId") Long productId,
        @org.springframework.data.repository.query.Param("startDate") LocalDateTime startDate,
        @org.springframework.data.repository.query.Param("endDate") LocalDateTime endDate,
        @org.springframework.data.repository.query.Param("search") String search,
        Pageable pageable);

    @Modifying
    @Query("DELETE FROM StockEvent e WHERE e.createdAt < :threshold")
    int deleteOlderThan(LocalDateTime threshold);
}
