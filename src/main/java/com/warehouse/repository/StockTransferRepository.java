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

    @Query("SELECT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "WHERE st.sourceWarehouse = :sourceWarehouse")
    List<StockTransfer> findBySourceWarehouse(@Param("sourceWarehouse") Warehouse sourceWarehouse);

    @Query("SELECT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "WHERE st.destinationWarehouse = :destinationWarehouse")
    List<StockTransfer> findByDestinationWarehouse(@Param("destinationWarehouse") Warehouse destinationWarehouse);

    @Query("SELECT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "WHERE st.product = :product")
    List<StockTransfer> findByProduct(@Param("product") Product product);

    @Query("SELECT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "WHERE st.status = :status")
    List<StockTransfer> findByStatus(@Param("status") TransferStatus status);

    @Query("SELECT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "WHERE st.sourceWarehouse = :warehouse OR st.destinationWarehouse = :warehouse " +
           "ORDER BY st.transferDate DESC")
    List<StockTransfer> findByWarehouse(@Param("warehouse") Warehouse warehouse);

    @Query("SELECT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "WHERE st.transferDate BETWEEN :startDate AND :endDate " +
           "ORDER BY st.transferDate DESC")
    List<StockTransfer> findByTransferDateBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "WHERE st.product = :product AND st.status IN ('PENDING', 'IN_TRANSIT')")
    List<StockTransfer> findActiveTransfersByProduct(@Param("product") Product product);

    @Query("SELECT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "ORDER BY st.transferDate DESC")
    List<StockTransfer> findAllOrderByTransferDateDesc();

    @Query("SELECT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "WHERE st.sourceWarehouse = :sourceWarehouse AND st.status = :status")
    List<StockTransfer> findBySourceWarehouseAndStatus(@Param("sourceWarehouse") Warehouse sourceWarehouse, 
                                                        @Param("status") TransferStatus status);

    @Query("SELECT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "WHERE st.destinationWarehouse = :destinationWarehouse AND st.status = :status")
    List<StockTransfer> findByDestinationWarehouseAndStatus(@Param("destinationWarehouse") Warehouse destinationWarehouse, 
                                                             @Param("status") TransferStatus status);
}

