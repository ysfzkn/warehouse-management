package com.warehouse.controller;

import com.warehouse.dto.StockRequestDto;
import com.warehouse.entity.StockRequest;
import com.warehouse.enums.StockRequestStatus;
import com.warehouse.enums.StockRequestType;
import com.warehouse.service.StockRequestService;
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

    /**
     * Create a new stock request
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('STOCK_IN', 'STOCK_OUT')")
    public ResponseEntity<StockRequestDto> createRequest(@RequestBody Map<String, Object> payload) {
        Long stockId = Long.valueOf(payload.get("stockId").toString());
        StockRequestType type = StockRequestType.valueOf(payload.get("type").toString());
        Integer quantity = Integer.valueOf(payload.get("quantity").toString());
        String notes = payload.containsKey("notes") ? payload.get("notes").toString() : null;

        StockRequest request = stockRequestService.createRequest(stockId, type, quantity, notes);
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
    public ResponseEntity<Void> approveRequest(@PathVariable Long id) {
        stockRequestService.approveRequest(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Reject a stock request
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> rejectRequest(@PathVariable Long id,
                                                @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("rejectionReason") : null;
        stockRequestService.rejectRequest(id, reason);
        return ResponseEntity.ok().build();
    }
}

