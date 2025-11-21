package com.warehouse.repository;

import com.warehouse.entity.Product;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.enums.TransferApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    @Override
    @EntityGraph(value = StockTransfer.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    Page<StockTransfer> findAll(Pageable pageable);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.sourceWarehouse = :sourceWarehouse")
    List<StockTransfer> findBySourceWarehouse(@Param("sourceWarehouse") Warehouse sourceWarehouse);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.destinationWarehouse = :destinationWarehouse")
    List<StockTransfer> findByDestinationWarehouse(@Param("destinationWarehouse") Warehouse destinationWarehouse);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product productEntity " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product itemProduct " +
           "WHERE productEntity = :product OR itemProduct = :product")
    List<StockTransfer> findByProduct(@Param("product") Product product);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.status = :status")
    List<StockTransfer> findByStatus(@Param("status") TransferStatus status);

    @EntityGraph(value = StockTransfer.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    List<StockTransfer> findByApprovalStatusOrderByTransferDateDesc(TransferApprovalStatus status);

    long countByApprovalStatus(TransferApprovalStatus status);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.sourceWarehouse = :warehouse OR st.destinationWarehouse = :warehouse " +
           "ORDER BY st.transferDate DESC")
    List<StockTransfer> findByWarehouse(@Param("warehouse") Warehouse warehouse);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.transferDate BETWEEN :startDate AND :endDate " +
           "ORDER BY st.transferDate DESC")
    List<StockTransfer> findByTransferDateBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product productEntity " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product itemProduct " +
           "WHERE (productEntity = :product OR itemProduct = :product) AND st.status IN ('PENDING', 'IN_TRANSIT')")
    List<StockTransfer> findActiveTransfersByProduct(@Param("product") Product product);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "ORDER BY st.transferDate DESC")
    List<StockTransfer> findAllOrderByTransferDateDesc();

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.sourceWarehouse = :sourceWarehouse AND st.status = :status")
    List<StockTransfer> findBySourceWarehouseAndStatus(@Param("sourceWarehouse") Warehouse sourceWarehouse,
                                                        @Param("status") TransferStatus status);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.destinationWarehouse = :destinationWarehouse AND st.status = :status")
    List<StockTransfer> findByDestinationWarehouseAndStatus(@Param("destinationWarehouse") Warehouse destinationWarehouse,
                                                             @Param("status") TransferStatus status);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.id = :id")
    java.util.Optional<StockTransfer> findByIdWithRelations(@Param("id") Long id);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.createdBy = :createdBy " +
           "ORDER BY st.transferDate DESC")
    List<StockTransfer> findAllByCreatedByOrderByTransferDateDesc(@Param("createdBy") String createdBy);

    @EntityGraph(value = StockTransfer.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    @Query("""
        SELECT st FROM StockTransfer st
        LEFT JOIN st.product directProduct
        WHERE (:createdBy IS NULL OR st.createdBy = :createdBy)
          AND (:status IS NULL OR st.status = :status)
          AND (:transferType IS NULL OR st.transferType = :transferType)
          AND (:sourceWarehouseId IS NULL OR st.sourceWarehouse.id = :sourceWarehouseId)
          AND (:destinationWarehouseId IS NULL OR st.destinationWarehouse.id = :destinationWarehouseId)
          AND (:driverProvided = false OR LOWER(st.driverName) LIKE :driverPattern)
          AND (
                :productNameProvided = false
                OR LOWER(COALESCE(directProduct.name, '')) LIKE :productNamePattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem item
                    WHERE item.transfer = st
                      AND LOWER(COALESCE(item.product.name, '')) LIKE :productNamePattern
                )
          )
          AND (
                :skuProvided = false
                OR LOWER(COALESCE(st.product.sku, '')) LIKE :skuPattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem itemSku
                    WHERE itemSku.transfer = st
                      AND LOWER(COALESCE(itemSku.product.sku, '')) LIKE :skuPattern
                )
          )
        ORDER BY st.transferDate DESC
    """)
    Page<StockTransfer> findByFilters(@Param("createdBy") String createdBy,
                                      @Param("status") TransferStatus status,
                                      @Param("transferType") TransferType transferType,
                                      @Param("sourceWarehouseId") Long sourceWarehouseId,
                                      @Param("destinationWarehouseId") Long destinationWarehouseId,
                                      @Param("driverProvided") boolean driverProvided,
                                      @Param("driverPattern") String driverPattern,
                                      @Param("productNameProvided") boolean productNameProvided,
                                      @Param("productNamePattern") String productNamePattern,
                                      @Param("skuProvided") boolean skuProvided,
                                      @Param("skuPattern") String skuPattern,
                                      Pageable pageable);

    @Query("""
        SELECT st.status AS status, COUNT(DISTINCT st.id) AS count FROM StockTransfer st
        LEFT JOIN st.product directProduct
        WHERE (:createdBy IS NULL OR st.createdBy = :createdBy)
          AND (:status IS NULL OR st.status = :status)
          AND (:transferType IS NULL OR st.transferType = :transferType)
          AND (:sourceWarehouseId IS NULL OR st.sourceWarehouse.id = :sourceWarehouseId)
          AND (:destinationWarehouseId IS NULL OR st.destinationWarehouse.id = :destinationWarehouseId)
          AND (:driverProvided = false OR LOWER(st.driverName) LIKE :driverPattern)
          AND (
                :productNameProvided = false
                OR LOWER(COALESCE(directProduct.name, '')) LIKE :productNamePattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem item
                    WHERE item.transfer = st
                      AND LOWER(COALESCE(item.product.name, '')) LIKE :productNamePattern
                )
          )
          AND (
                :skuProvided = false
                OR LOWER(COALESCE(directProduct.sku, '')) LIKE :skuPattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem itemSku
                    WHERE itemSku.transfer = st
                      AND LOWER(COALESCE(itemSku.product.sku, '')) LIKE :skuPattern
                )
          )
        GROUP BY st.status
    """)
    List<StatusCountProjection> countByFiltersGroupedStatus(@Param("createdBy") String createdBy,
                                                            @Param("status") TransferStatus status,
                                                            @Param("transferType") TransferType transferType,
                                                            @Param("sourceWarehouseId") Long sourceWarehouseId,
                                                            @Param("destinationWarehouseId") Long destinationWarehouseId,
                                                            @Param("driverProvided") boolean driverProvided,
                                                            @Param("driverPattern") String driverPattern,
                                                            @Param("productNameProvided") boolean productNameProvided,
                                                            @Param("productNamePattern") String productNamePattern,
                                                            @Param("skuProvided") boolean skuProvided,
                                                            @Param("skuPattern") String skuPattern);

    @Query("""
        SELECT st.transferType AS transferType, COUNT(DISTINCT st.id) AS count FROM StockTransfer st
        LEFT JOIN st.product directProduct
        WHERE (:createdBy IS NULL OR st.createdBy = :createdBy)
          AND (:status IS NULL OR st.status = :status)
          AND (:transferType IS NULL OR st.transferType = :transferType)
          AND (:sourceWarehouseId IS NULL OR st.sourceWarehouse.id = :sourceWarehouseId)
          AND (:destinationWarehouseId IS NULL OR st.destinationWarehouse.id = :destinationWarehouseId)
          AND (:driverProvided = false OR LOWER(st.driverName) LIKE :driverPattern)
          AND (
                :productNameProvided = false
                OR LOWER(COALESCE(directProduct.name, '')) LIKE :productNamePattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem item
                    WHERE item.transfer = st
                      AND LOWER(COALESCE(item.product.name, '')) LIKE :productNamePattern
                )
          )
          AND (
                :skuProvided = false
                OR LOWER(COALESCE(directProduct.sku, '')) LIKE :skuPattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem itemSku
                    WHERE itemSku.transfer = st
                      AND LOWER(COALESCE(itemSku.product.sku, '')) LIKE :skuPattern
                )
          )
        GROUP BY st.transferType
    """)
    List<TransferTypeCountProjection> countByFiltersGroupedTransferType(@Param("createdBy") String createdBy,
                                                                        @Param("status") TransferStatus status,
                                                                        @Param("transferType") TransferType transferType,
                                                                        @Param("sourceWarehouseId") Long sourceWarehouseId,
                                                                        @Param("destinationWarehouseId") Long destinationWarehouseId,
                                                                        @Param("driverProvided") boolean driverProvided,
                                                                        @Param("driverPattern") String driverPattern,
                                                                        @Param("productNameProvided") boolean productNameProvided,
                                                                        @Param("productNamePattern") String productNamePattern,
                                                                        @Param("skuProvided") boolean skuProvided,
                                                                        @Param("skuPattern") String skuPattern);

    interface StatusCountProjection {
        TransferStatus getStatus();
        long getCount();
    }

    interface TransferTypeCountProjection {
        TransferType getTransferType();
        long getCount();
    }
}
