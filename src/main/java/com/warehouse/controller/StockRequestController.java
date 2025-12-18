package com.warehouse.controller;

import com.warehouse.dto.StockRequestDto;
import com.warehouse.entity.StockRequest;
import com.warehouse.enums.StockRequestStatus;
import com.warehouse.enums.StockRequestType;
import com.warehouse.service.StockRequestService;
import com.warehouse.service.AdminSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for stock request operations
 */
@RestController
@RequestMapping("/api/stock-requests")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class StockRequestController {

    private final StockRequestService stockRequestService;
    private final AdminSecurityService adminSecurityService;

    /**
     * Create a new stock request
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('STOCK_IN', 'STOCK_OUT')")
    public ResponseEntity<StockRequestDto> createRequest(@RequestBody Map<String, Object> payload) {
        Long stockId = payload.containsKey("stockId") && payload.get("stockId") != null
                ? Long.valueOf(payload.get("stockId").toString())
                : null;
        Long productId = payload.containsKey("productId") && payload.get("productId") != null
                ? Long.valueOf(payload.get("productId").toString())
                : null;
        Long warehouseId = payload.containsKey("warehouseId") && payload.get("warehouseId") != null
                ? Long.valueOf(payload.get("warehouseId").toString())
                : null;
        String customerName = payload.containsKey("customerName") ? (String) payload.get("customerName") : null;
        String customerPhone = payload.containsKey("customerPhone") ? (String) payload.get("customerPhone") : null;

        StockRequestType type = StockRequestType.valueOf(payload.get("type").toString());
        Integer quantity = Integer.valueOf(payload.get("quantity").toString());
        String notes = payload.containsKey("notes") ? payload.get("notes").toString() : null;

        StockRequest request = stockRequestService.createRequest(
                stockId,
                type,
                quantity,
                notes,
                productId,
                warehouseId,
                customerName,
                customerPhone
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(stockRequestService.toDto(request));
    }

    /**
     * Get all stock requests
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StockRequestDto>> getAllRequests(
            @RequestParam(required = false) StockRequestStatus status) {
        List<StockRequestDto> requests = status != null
                ? stockRequestService.getRequestsByStatus(status)
                : stockRequestService.getAllRequests();
        return ResponseEntity.ok(requests);
    }

    /**
     * Get current user's requests (non-admin)
     */
    @GetMapping("/current-user")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_IN', 'STOCK_OUT')")
    public ResponseEntity<List<StockRequestDto>> getMyRequests(
            @RequestParam(required = false) StockRequestStatus status) {
        List<StockRequestDto> requests = stockRequestService.getRequestsForCurrentUser(status);
        return ResponseEntity.ok(requests);
    }

    /**
     * Get pending requests count
     */
    @GetMapping("/pending/count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getPendingCount() {
        long count = stockRequestService.getPendingRequestsCount();
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Approve a stock request
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approveRequest(@PathVariable Long id,
                                               @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        stockRequestService.approveRequest(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Reject a stock request
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> rejectRequest(@PathVariable Long id,
                                              @RequestBody(required = false) Map<String, String> body,
                                              @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        String reason = body != null ? body.get("rejectionReason") : null;
        stockRequestService.rejectRequest(id, reason);
        return ResponseEntity.ok().build();
    }

    /**
     * Delete own pending request
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_IN', 'STOCK_OUT')")
    public ResponseEntity<Void> deleteOwnRequest(@PathVariable Long id,
                                                 @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        stockRequestService.deleteOwnPendingRequest(id);
        return ResponseEntity.noContent().build();
    }
}


