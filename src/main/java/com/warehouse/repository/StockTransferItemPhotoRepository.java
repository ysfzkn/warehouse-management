package com.warehouse.repository;

import com.warehouse.entity.StockTransferItem;
import com.warehouse.entity.StockTransferItemPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockTransferItemPhotoRepository extends JpaRepository<StockTransferItemPhoto, Long> {

    Optional<StockTransferItemPhoto> findByItem(StockTransferItem item);
}


