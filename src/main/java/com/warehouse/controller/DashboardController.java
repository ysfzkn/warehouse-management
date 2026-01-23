package com.warehouse.controller;

import com.warehouse.dto.DashboardStatsDto;
import com.warehouse.dto.LowStockItemDto;
import com.warehouse.dto.WarehouseStatsDto;
import com.warehouse.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for optimized dashboard data.
 * Provides aggregated statistics without loading all entities into memory.
 */
@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;

    @Autowired
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Get all dashboard statistics in a single optimized API call.
     * Returns aggregated data with caching enabled.
     * 
     * @return DashboardStatsDto with all statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDto> getDashboardStats() {
        DashboardStatsDto stats = dashboardService.getDashboardStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get warehouse-level statistics with aggregated data.
     * Returns per-warehouse totals with caching enabled.
     * 
     * @return List of WarehouseStatsDto
     */
    @GetMapping("/warehouse-stats")
    public ResponseEntity<List<WarehouseStatsDto>> getWarehouseStats() {
        List<WarehouseStatsDto> stats = dashboardService.getWarehouseStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get low stock items with minimal data (first 50 items).
     * Returns lightweight DTOs instead of full entities.
     * 
     * @return List of LowStockItemDto
     */
    @GetMapping("/low-stock-items")
    public ResponseEntity<List<LowStockItemDto>> getLowStockItems() {
        List<LowStockItemDto> items = dashboardService.getLowStockItems();
        return ResponseEntity.ok(items);
    }

    /**
     * Get out of stock items with minimal data (first 50 items).
     * Returns lightweight DTOs instead of full entities.
     * 
     * @return List of LowStockItemDto
     */
    @GetMapping("/out-of-stock-items")
    public ResponseEntity<List<LowStockItemDto>> getOutOfStockItems() {
        List<LowStockItemDto> items = dashboardService.getOutOfStockItems();
        return ResponseEntity.ok(items);
    }
}
