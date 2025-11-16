package com.warehouse.service;

import com.warehouse.entity.StockTransfer;
import com.warehouse.enums.TransferStatus;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing stock transfers.
 */
public interface StockTransferService {

    List<StockTransfer> getAllTransfers();

    Optional<StockTransfer> getTransferById(Long id);

    StockTransfer getTransferByIdOrThrow(Long id);

    List<StockTransfer> getTransfersByWarehouse(Long warehouseId);

    List<StockTransfer> getTransfersByProduct(Long productId);

    List<StockTransfer> getTransfersByStatus(TransferStatus status);

    StockTransfer createTransfer(StockTransfer transfer);

    StockTransfer startTransfer(Long transferId);

    StockTransfer completeTransfer(Long transferId, String completionNote);

    StockTransfer cancelTransfer(Long transferId, String cancellationReason);

    StockTransfer updateTransfer(Long transferId, StockTransfer updatedTransfer);

    void deleteTransfer(Long transferId);
}
