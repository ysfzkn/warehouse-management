package com.warehouse.service;

import com.warehouse.dto.BulkDeleteResponse;
import com.warehouse.dto.CarrierAssignmentRequest;
import com.warehouse.dto.ServiceHandoverRequest;
import com.warehouse.dto.StockTransferFilter;
import com.warehouse.dto.StockTransferSummary;
import com.warehouse.dto.StockTransferDeletionResult;
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

    /**
     * Records goods leaving the warehouse into a service company's hands, before the driver
     * who will carry them onward is known.
     *
     * <p>Creates the shipment and completes it in one step, because that is what physically
     * happened: the goods are gone. Stock is deducted through the ordinary completion path,
     * so a depot exit and a normal delivery are indistinguishable in the ledger — which is
     * the point. The carrier fields stay empty and {@code carrierPending} is set until
     * {@link #assignCarrier} fills them in.</p>
     */
    StockTransfer createServiceHandover(ServiceHandoverRequest request);

    /**
     * Fills in the carrier of a shipment that went out on a depot exit receipt.
     *
     * <p>Deliberately does not touch stock: the goods left when the receipt was issued, and
     * naming the driver afterwards is not a second departure. It also leaves any printed
     * receipt alone — the signed page says no carrier was known, and that was true.
     * Reprinting is a separate, explicit action.</p>
     */
    StockTransfer assignCarrier(Long transferId, CarrierAssignmentRequest request);

    StockTransfer startTransfer(Long transferId);

    StockTransfer completeTransfer(Long transferId, String completionNote);

    StockTransfer cancelTransfer(Long transferId, String cancellationReason);

    StockTransfer updateTransfer(Long transferId, StockTransfer updatedTransfer);

    /**
     * Matches (or clears, when {@code customerId} is null) the e-commerce customer record of a
     * customer delivery. Can be done long after the shipment was created.
     */
    StockTransfer linkCustomer(Long transferId, Long customerId);

    /** Distinct customers from past customer deliveries, for the new-transfer picker. */
    java.util.List<java.util.Map<String, Object>> findRecentCustomers(String query);

    StockTransferDeletionResult deleteTransfer(Long transferId, String adminSecurityCode);

    BulkDeleteResponse deleteTransfers(List<Long> transferIds);

    List<StockTransfer> getTransferApprovals(TransferApprovalStatus status);

    long countTransferApprovals(TransferApprovalStatus status);

    StockTransfer approveTransferStart(Long transferId, String approvalNote, String adminSecurityCode);

    StockTransfer rejectTransferStart(Long transferId, String rejectionReason);

    List<StockTransfer> getTransferRequestsForCurrentUser();
}
