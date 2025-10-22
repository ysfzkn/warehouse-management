package com.warehouse.repository;

import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.Warehouse;
import com.warehouse.entity.Product;
import com.warehouse.enums.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    List<StockTransfer> findBySourceWarehouse(Warehouse sourceWarehouse);

    List<StockTransfer> findByDestinationWarehouse(Warehouse destinationWarehouse);

    List<StockTransfer> findByProduct(Product product);

    List<StockTransfer> findByStatus(TransferStatus status);

    @Query("SELECT st FROM StockTransfer st WHERE st.sourceWarehouse = :warehouse OR st.destinationWarehouse = :warehouse ORDER BY st.transferDate DESC")
    List<StockTransfer> findByWarehouse(@Param("warehouse") Warehouse warehouse);

    @Query("SELECT st FROM StockTransfer st WHERE st.transferDate BETWEEN :startDate AND :endDate ORDER BY st.transferDate DESC")
    List<StockTransfer> findByTransferDateBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT st FROM StockTransfer st WHERE st.product = :product AND st.status IN ('PENDING', 'IN_TRANSIT')")
    List<StockTransfer> findActiveTransfersByProduct(@Param("product") Product product);

    @Query("SELECT st FROM StockTransfer st ORDER BY st.transferDate DESC")
    List<StockTransfer> findAllOrderByTransferDateDesc();

    List<StockTransfer> findBySourceWarehouseAndStatus(Warehouse sourceWarehouse, TransferStatus status);

    List<StockTransfer> findByDestinationWarehouseAndStatus(Warehouse destinationWarehouse, TransferStatus status);
}

