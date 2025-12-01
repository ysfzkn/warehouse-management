package com.warehouse.repository;

import com.warehouse.entity.StockTransferItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockTransferItemRepository extends JpaRepository<StockTransferItem, Long> {
}


