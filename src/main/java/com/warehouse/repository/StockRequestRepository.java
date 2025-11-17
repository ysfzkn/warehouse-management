package com.warehouse.repository;

import com.warehouse.entity.StockRequest;
import com.warehouse.enums.StockRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for StockRequest entity
 */
@Repository
public interface StockRequestRepository extends JpaRepository<StockRequest, Long> {

    /**
     * Find all requests by status
     */
    List<StockRequest> findByStatusOrderByRequestedAtDesc(StockRequestStatus status);

    /**
     * Find all requests ordered by requested date with all related entities
     */
    @Query("SELECT sr FROM StockRequest sr " +
           "LEFT JOIN FETCH sr.stock s " +
           "LEFT JOIN FETCH s.product p " +
           "LEFT JOIN FETCH s.warehouse w " +
           "ORDER BY sr.requestedAt DESC")
    List<StockRequest> findAllByOrderByRequestedAtDesc();

    /**
     * Find pending requests for a specific user
     */
    List<StockRequest> findByRequestedByAndStatusOrderByRequestedAtDesc(String requestedBy, StockRequestStatus status);

    /**
     * Count pending requests
     */
    long countByStatus(StockRequestStatus status);

    /**
     * Find all pending requests with stock, product, and warehouse details
     */
    @Query("SELECT sr FROM StockRequest sr " +
           "LEFT JOIN FETCH sr.stock s " +
           "LEFT JOIN FETCH s.product p " +
           "LEFT JOIN FETCH s.warehouse w " +
           "WHERE sr.status = :status " +
           "ORDER BY sr.requestedAt DESC")
    List<StockRequest> findPendingRequestsWithDetails(StockRequestStatus status);

    @Query("SELECT sr FROM StockRequest sr " +
           "LEFT JOIN FETCH sr.stock s " +
           "LEFT JOIN FETCH s.product p " +
           "LEFT JOIN FETCH s.warehouse w " +
           "WHERE sr.requestedBy = :requestedBy " +
           "ORDER BY sr.requestedAt DESC")
    List<StockRequest> findAllDetailsByRequestedBy(@Param("requestedBy") String requestedBy);

    @Query("SELECT sr FROM StockRequest sr " +
           "LEFT JOIN FETCH sr.stock s " +
           "LEFT JOIN FETCH s.product p " +
           "LEFT JOIN FETCH s.warehouse w " +
           "WHERE sr.requestedBy = :requestedBy AND sr.status = :status " +
           "ORDER BY sr.requestedAt DESC")
    List<StockRequest> findAllDetailsByRequestedByAndStatus(@Param("requestedBy") String requestedBy,
                                                            @Param("status") StockRequestStatus status);
}

