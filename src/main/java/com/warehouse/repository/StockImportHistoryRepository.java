package com.warehouse.repository;

import com.warehouse.entity.StockImportHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockImportHistoryRepository extends JpaRepository<StockImportHistory, Long> {
    @Override
    @EntityGraph(attributePaths = {"warehouse"})
    @NonNull
    List<StockImportHistory> findAll();
}


