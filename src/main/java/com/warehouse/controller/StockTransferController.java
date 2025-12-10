package com.warehouse.controller;

import com.warehouse.dto.BulkDeleteResponse;
import com.warehouse.dto.PagedResponse;
import com.warehouse.dto.StockTransferCreateRequest;
import com.warehouse.dto.StockTransferDto;
import com.warehouse.dto.StockTransferFilter;
import com.warehouse.dto.StockTransferSummary;
import com.warehouse.dto.StockTransferDeletionResult;
import com.warehouse.entity.Product;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.StockTransferItem;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.enums.TransferApprovalStatus;
import com.warehouse.mapper.StockTransferMapper;
import com.warehouse.service.StockTransferService;
import com.warehouse.service.AdminSecurityService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock-transfers")
@CrossOrigin(origins = "*")
public class StockTransferController {

    private final StockTransferService stockTransferService;
    private final StockTransferMapper transferMapper;
    private final AdminSecurityService adminSecurityService;

    @Autowired
    public StockTransferController(StockTransferService stockTransferService, StockTransferMapper transferMapper, AdminSecurityService adminSecurityService) {
        this.stockTransferService = stockTransferService;
        this.transferMapper = transferMapper;
        this.adminSecurityService = adminSecurityService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<StockTransferDto>> getAllTransfers(
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) TransferType transferType,
            @RequestParam(required = false) Long sourceWarehouseId,
            @RequestParam(required = false) Long destinationWarehouseId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String driverName,
            @RequestParam(required = false) String notes,
            @PageableDefault(size = 25, sort = "transferDate", direction = Sort.Direction.DESC) Pageable pageable) {
        StockTransferFilter filter = buildFilter(status, transferType, sourceWarehouseId, destinationWarehouseId, productName, sku, driverName, notes);
        Page<StockTransfer> transfers = stockTransferService.getTransfersPaged(filter, pageable);
        List<StockTransferDto> dtos = transferMapper.toDtoList(transfers.getContent());
        StockTransferSummary summary = stockTransferService.getTransferSummary(filter, false);
        PagedResponse<StockTransferDto> response = new PagedResponse<>(
                dtos,
                transfers.getNumber(),
                transfers.getSize(),
                transfers.getTotalElements(),
                transfers.getTotalPages(),
                transfers.isFirst(),
                transfers.isLast(),
                Map.of(
                        "statusCounts", summary.getStatusCounts(),
                        "transferTypeCounts", summary.getTransferTypeCounts()
                )
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StockTransferDto> getTransferById(@PathVariable Long id) {
        return stockTransferService.getTransferById(id)
                .map(transferMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<List<StockTransferDto>> getTransfersByWarehouse(@PathVariable Long warehouseId) {
        List<StockTransfer> transfers = stockTransferService.getTransfersByWarehouse(warehouseId);
        List<StockTransferDto> dtos = transferMapper.toDtoList(transfers);
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<StockTransferDto>> getTransfersByProduct(@PathVariable Long productId) {
        List<StockTransfer> transfers = stockTransferService.getTransfersByProduct(productId);
        List<StockTransferDto> dtos = transferMapper.toDtoList(transfers);
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<StockTransferDto>> getTransfersByStatus(@PathVariable String status) {
        TransferStatus transferStatus = TransferStatus.valueOf(status.toUpperCase());
        List<StockTransfer> transfers = stockTransferService.getTransfersByStatus(transferStatus);
        List<StockTransferDto> dtos = transferMapper.toDtoList(transfers);
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/current-user")
    public ResponseEntity<PagedResponse<StockTransferDto>> getCurrentUserTransfers(
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) TransferType transferType,
            @RequestParam(required = false) Long sourceWarehouseId,
            @RequestParam(required = false) Long destinationWarehouseId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String driverName,
            @RequestParam(required = false) String notes,
            @PageableDefault(size = 25, sort = "transferDate", direction = Sort.Direction.DESC) Pageable pageable) {
        StockTransferFilter filter = buildFilter(status, transferType, sourceWarehouseId, destinationWarehouseId, productName, sku, driverName, notes);
        Page<StockTransfer> transfers = stockTransferService.getTransfersForCurrentUserPaged(filter, pageable);
        List<StockTransferDto> dtos = transferMapper.toDtoList(transfers.getContent());
        StockTransferSummary summary = stockTransferService.getTransferSummary(filter, true);
        PagedResponse<StockTransferDto> response = new PagedResponse<>(
                dtos,
                transfers.getNumber(),
                transfers.getSize(),
                transfers.getTotalElements(),
                transfers.getTotalPages(),
                transfers.isFirst(),
                transfers.isLast(),
                Map.of(
                        "statusCounts", summary.getStatusCounts(),
                        "transferTypeCounts", summary.getTransferTypeCounts()
                )
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<StockTransferDto> createTransfer(@Valid @RequestBody StockTransferCreateRequest request) {
        StockTransfer transfer = mapToEntity(request);
        StockTransfer createdTransfer = stockTransferService.createTransfer(transfer);
        StockTransferDto dto = transferMapper.toDto(createdTransfer);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<StockTransferDto> startTransfer(@PathVariable Long id) {
        StockTransfer transfer = stockTransferService.startTransfer(id);
        StockTransferDto dto = transferMapper.toDto(transfer);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<StockTransferDto> completeTransfer(@PathVariable Long id,
                                                             @RequestBody(required = false) Map<String, String> payload) {
        String completionNote = payload != null ? payload.get("completionNote") : null;
        StockTransfer transfer = stockTransferService.completeTransfer(id, completionNote);
        StockTransferDto dto = transferMapper.toDto(transfer);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<StockTransferDto> cancelTransfer(@PathVariable Long id, 
                                                           @RequestBody(required = false) java.util.Map<String, String> payload) {
        String cancellationReason = payload != null ? payload.get("cancellationReason") : null;
        StockTransfer transfer = stockTransferService.cancelTransfer(id, cancellationReason);
        StockTransferDto dto = transferMapper.toDto(transfer);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/approvals")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StockTransferDto>> getTransferApprovals(@RequestParam(required = false) TransferApprovalStatus status) {
        TransferApprovalStatus effectiveStatus = status != null ? status : TransferApprovalStatus.PENDING;
        List<StockTransfer> transfers = stockTransferService.getTransferApprovals(effectiveStatus);
        return ResponseEntity.ok(transferMapper.toDtoList(transfers));
    }

    @GetMapping("/approvals/count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getTransferApprovalCount() {
        long count = stockTransferService.countTransferApprovals(TransferApprovalStatus.PENDING);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/{id}/approve-start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockTransferDto> approveTransferStart(@PathVariable Long id,
                                                                 @RequestBody(required = false) Map<String, String> payload,
                                                                 @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        String note = payload != null ? payload.get("note") : null;
        StockTransfer transfer = stockTransferService.approveTransferStart(id, note, adminSecurityCode);
        return ResponseEntity.ok(transferMapper.toDto(transfer));
    }

    @PostMapping("/{id}/reject-start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockTransferDto> rejectTransferStart(@PathVariable Long id,
                                                                @RequestBody(required = false) Map<String, String> payload) {
        String reason = payload != null ? payload.get("reason") : null;
        StockTransfer transfer = stockTransferService.rejectTransferStart(id, reason);
        return ResponseEntity.ok(transferMapper.toDto(transfer));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StockTransferDto> updateTransfer(@PathVariable Long id, @Valid @RequestBody StockTransfer transfer) {
        StockTransfer updatedTransfer = stockTransferService.updateTransfer(id, transfer);
        StockTransferDto dto = transferMapper.toDto(updatedTransfer);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTransfer(@PathVariable Long id,
                                            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        StockTransferDeletionResult result = stockTransferService.deleteTransfer(id, adminSecurityCode);
        if (result.isApprovalRequested()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkDeleteResponse> deleteTransfers(@RequestBody List<Long> ids,
                                                              @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        BulkDeleteResponse response = stockTransferService.deleteTransfers(ids);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/current-user/requests")
    public ResponseEntity<List<StockTransferDto>> getTransferRequestsForCurrentUser() {
        List<StockTransfer> transfers = stockTransferService.getTransferRequestsForCurrentUser();
        return ResponseEntity.ok(transferMapper.toDtoList(transfers));
    }

    private StockTransferFilter buildFilter(TransferStatus status,
                                            TransferType transferType,
                                            Long sourceWarehouseId,
                                            Long destinationWarehouseId,
                                            String productName,
                                            String sku,
                                            String driverName,
                                            String notes) {
        StockTransferFilter filter = new StockTransferFilter();
        filter.setStatus(status);
        filter.setTransferType(transferType);
        filter.setSourceWarehouseId(sourceWarehouseId);
        filter.setDestinationWarehouseId(destinationWarehouseId);
        filter.setProductName(productName);
        filter.setSku(sku);
        filter.setDriverName(driverName);
        filter.setNotes(notes);
        return filter;
    }

    private StockTransfer mapToEntity(StockTransferCreateRequest request) {
        StockTransfer transfer = new StockTransfer();

        Warehouse sourceWarehouse = new Warehouse();
        sourceWarehouse.setId(request.getSourceWarehouseId());
        transfer.setSourceWarehouse(sourceWarehouse);

        if (request.getDestinationWarehouseId() != null) {
            Warehouse destination = new Warehouse();
            destination.setId(request.getDestinationWarehouseId());
            transfer.setDestinationWarehouse(destination);
        }

        transfer.setDriverName(request.getDriverName().trim());
        transfer.setDriverTcId(request.getDriverTcId().trim());
        transfer.setDriverPhone(request.getDriverPhone().trim());
        transfer.setVehiclePlate(request.getVehiclePlate().trim().toUpperCase());
        transfer.setNotes(request.getNotes() != null ? request.getNotes().trim() : null);
        transfer.setTransferDate(request.getTransferDate());

        TransferType transferType = request.getTransferType() != null ? request.getTransferType() : TransferType.WAREHOUSE;
        transfer.setTransferType(transferType);

        if (transferType == TransferType.CUSTOMER_DELIVERY) {
            transfer.setCustomerFullName(request.getCustomerFullName() != null ? request.getCustomerFullName().trim() : null);
            transfer.setCustomerPhone(request.getCustomerPhone() != null ? request.getCustomerPhone().trim() : null);
            transfer.setCustomerAddress(request.getCustomerAddress() != null ? request.getCustomerAddress().trim() : null);
        } else {
            transfer.setCustomerFullName(null);
            transfer.setCustomerPhone(null);
            transfer.setCustomerAddress(null);
        }

        if (request.getItems() != null) {
            transfer.setItems(
                    request.getItems().stream().map(itemRequest -> {
                        StockTransferItem item = new StockTransferItem();
                        item.setStockId(itemRequest.getStockId());
                        Product product = new Product();
                        product.setId(itemRequest.getProductId());
                        item.setProduct(product);
                        item.setQuantity(itemRequest.getQuantity());
                        item.setTransfer(transfer);
                        return item;
                    }).toList()
            );
        }

        return transfer;
    }
}
