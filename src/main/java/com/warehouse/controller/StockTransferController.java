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
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/stock-transfers")
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String driverName,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String customerQuery,
            @RequestParam(required = false) String transferDateFrom,
            @RequestParam(required = false) String transferDateTo,
            @RequestParam(required = false) String createdAtFrom,
            @RequestParam(required = false) String createdAtTo,
            @PageableDefault(size = 25, sort = "transferDate", direction = Sort.Direction.DESC) Pageable pageable) {
        StockTransferFilter filter = buildFilter(status, transferType, sourceWarehouseId, destinationWarehouseId, startDate, endDate, productName, sku, driverName, notes, customerQuery, transferDateFrom, transferDateTo, createdAtFrom, createdAtTo);
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String driverName,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String customerQuery,
            @RequestParam(required = false) String transferDateFrom,
            @RequestParam(required = false) String transferDateTo,
            @RequestParam(required = false) String createdAtFrom,
            @RequestParam(required = false) String createdAtTo,
            @PageableDefault(size = 25, sort = "transferDate", direction = Sort.Direction.DESC) Pageable pageable) {
        StockTransferFilter filter = buildFilter(status, transferType, sourceWarehouseId, destinationWarehouseId, startDate, endDate, productName, sku, driverName, notes, customerQuery, transferDateFrom, transferDateTo, createdAtFrom, createdAtTo);
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

    /**
     * Customers this warehouse has delivered to before, for the new-transfer form's picker.
     * Saves retyping name, phone and address — and keeps one customer spelled one way.
     */
    @GetMapping("/customers")
    public ResponseEntity<List<Map<String, Object>>> recentCustomers(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(stockTransferService.findRecentCustomers(q));
    }

    @PostMapping
    public ResponseEntity<StockTransferDto> createTransfer(@Valid @RequestBody StockTransferCreateRequest request) {
        StockTransfer transfer = mapToEntity(request);
        StockTransfer createdTransfer = stockTransferService.createTransfer(transfer);
        StockTransferDto dto = transferMapper.toDto(createdTransfer);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Matches the delivery with an e-commerce customer record, or clears the match when
     * {@code customerId} is omitted. Usable long after the shipment was created.
     */
    @PutMapping("/{id}/customer")
    public ResponseEntity<StockTransferDto> linkCustomer(@PathVariable Long id,
                                                         @RequestBody(required = false) Map<String, Object> body) {
        Object raw = body == null ? null : body.get("customerId");
        Long customerId = raw == null ? null : Long.valueOf(String.valueOf(raw));
        StockTransfer transfer = stockTransferService.linkCustomer(id, customerId);
        return ResponseEntity.ok(transferMapper.toDto(transfer));
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
                                            LocalDateTime startDate,
                                            LocalDateTime endDate,
                                            String productName,
                                            String sku,
                                            String driverName,
                                            String notes,
                                            String customerQuery,
                                            String transferDateFrom,
                                            String transferDateTo,
                                            String createdAtFrom,
                                            String createdAtTo) {
        StockTransferFilter filter = new StockTransferFilter();
        filter.setStatus(status);
        filter.setTransferType(transferType);
        filter.setSourceWarehouseId(sourceWarehouseId);
        filter.setDestinationWarehouseId(destinationWarehouseId);
        filter.setStartDate(startDate);
        filter.setEndDate(endDate);
        filter.setProductName(productName);
        filter.setSku(sku);
        filter.setDriverName(driverName);
        filter.setNotes(notes);
        filter.setCustomerQuery(customerQuery);
        
        // Parse date strings to LocalDateTime (accepts ISO with or without Z, e.g. from frontend toISOString())
        if (transferDateFrom != null && !transferDateFrom.isBlank()) {
            try {
                filter.setTransferDateFrom(parseIsoToLocalDateTime(transferDateFrom));
            } catch (Exception ignored) { }
        }
        if (transferDateTo != null && !transferDateTo.isBlank()) {
            try {
                filter.setTransferDateTo(parseIsoToLocalDateTime(transferDateTo));
            } catch (Exception ignored) { }
        }
        if (createdAtFrom != null && !createdAtFrom.isBlank()) {
            try {
                filter.setCreatedAtFrom(parseIsoToLocalDateTime(createdAtFrom));
            } catch (Exception ignored) { }
        }
        if (createdAtTo != null && !createdAtTo.isBlank()) {
            try {
                filter.setCreatedAtTo(parseIsoToLocalDateTime(createdAtTo));
            } catch (Exception ignored) { }
        }
        return filter;
    }

    private static LocalDateTime parseIsoToLocalDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        String s = value.trim();
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
            return Instant.parse(s).atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
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
            // Stored title-cased so the same person reads identically on every screen.
            transfer.setCustomerFullName(com.warehouse.util.TurkishText.toTitleCase(request.getCustomerFullName()));
            transfer.setCustomerPhone(request.getCustomerPhone() != null ? request.getCustomerPhone().trim() : null);
            transfer.setCustomerAddress(request.getCustomerAddress() != null ? request.getCustomerAddress().trim() : null);
            transfer.setOrderId(request.getOrderId());
            transfer.setCustomerId(request.getCustomerId());
        } else {
            transfer.setCustomerFullName(null);
            transfer.setCustomerPhone(null);
            transfer.setCustomerAddress(null);
            transfer.setOrderId(null);
            transfer.setCustomerId(null);
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
