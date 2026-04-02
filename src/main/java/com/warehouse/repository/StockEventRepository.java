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

    @Modifying
    @Query("DELETE FROM StockEvent e WHERE e.createdAt < :threshold")
    int deleteOlderThan(LocalDateTime threshold);
}
