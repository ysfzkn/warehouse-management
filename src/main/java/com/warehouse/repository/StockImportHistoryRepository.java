package com.warehouse.repository;

import com.warehouse.entity.StockImportHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockImportHistoryRepository extends JpaRepository<StockImportHistory, Long> {
}


