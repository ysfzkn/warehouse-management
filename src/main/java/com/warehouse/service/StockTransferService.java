package com.warehouse.service;

import com.warehouse.dto.StockTransferFilter;
import com.warehouse.dto.StockTransferSummary;
import com.warehouse.entity.StockTransfer;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing stock transfers.
 */
public interface StockTransferService {

    List<StockTransfer> getAllTransfers();

    Page<StockTransfer> getTransfersPaged(StockTransferFilter filter, Pageable pageable);

    Optional<StockTransfer> getTransferById(Long id);

    StockTransfer getTransferByIdOrThrow(Long id);

    List<StockTransfer> getTransfersByWarehouse(Long warehouseId);

    List<StockTransfer> getTransfersByProduct(Long productId);

    List<StockTransfer> getTransfersByStatus(TransferStatus status);

    List<StockTransfer> getTransfersForCurrentUser();

    Page<StockTransfer> getTransfersForCurrentUserPaged(StockTransferFilter filter, Pageable pageable);

    StockTransferSummary getTransferSummary(StockTransferFilter filter, boolean currentUserOnly);

    StockTransfer createTransfer(StockTransfer transfer);

    StockTransfer startTransfer(Long transferId);

    StockTransfer completeTransfer(Long transferId, String completionNote);

    StockTransfer cancelTransfer(Long transferId, String cancellationReason);

    StockTransfer updateTransfer(Long transferId, StockTransfer updatedTransfer);

    void deleteTransfer(Long transferId);

    List<StockTransfer> getTransferApprovals(TransferApprovalStatus status);

    long countTransferApprovals(TransferApprovalStatus status);

    StockTransfer approveTransferStart(Long transferId, String approvalNote);

    StockTransfer rejectTransferStart(Long transferId, String rejectionReason);
}
