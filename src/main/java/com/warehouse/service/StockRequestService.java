package com.warehouse.service;

import com.warehouse.dto.StockRequestDto;
import com.warehouse.entity.StockRequest;
import com.warehouse.enums.StockRequestStatus;
import com.warehouse.enums.StockRequestType;

import java.util.List;

/**
 * Service interface for stock request operations
 */
public interface StockRequestService {

    /**
     * Create a new stock request. If stockId is null and product/warehouse are provided,
     * a stock entry will be created (quantity=0) if it does not exist.
     */
    StockRequest createRequest(Long stockId,
                               StockRequestType type,
                               Integer quantity,
                               String notes,
                               Long productId,
                               Long warehouseId,
                               String customerName,
                               String customerPhone,
                               String irsaliyeNo,
                               java.time.LocalDate irsaliyeDate);

    /**
     * Get all requests
     */
    List<StockRequestDto> getAllRequests();

    /**
     * Get requests by status
     */
    List<StockRequestDto> getRequestsByStatus(StockRequestStatus status);

    /**
     * Get requests created by current user (non-admin)
     */
    List<StockRequestDto> getRequestsForCurrentUser(StockRequestStatus status);

    /**
     * Get pending requests count
     */
    long getPendingRequestsCount();

    /**
     * Approve a stock request
     */
    void approveRequest(Long requestId);

    /**
     * Reject a stock request
     */
    void rejectRequest(Long requestId, String rejectionReason);

    /**
     * Get request by ID
     */
    StockRequest getRequestById(Long id);

    /**
     * Convert entity to DTO
     */
    StockRequestDto toDto(StockRequest request);

    /**
     * Delete own pending request
     */
    void deleteOwnPendingRequest(Long requestId);
}


