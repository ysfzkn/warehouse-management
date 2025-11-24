package com.warehouse.controller;

import com.warehouse.dto.PagedResponse;
import com.warehouse.dto.StockDto;
import com.warehouse.dto.StockFilter;
import com.warehouse.dto.StockRequestDto;
import com.warehouse.entity.Stock;
import com.warehouse.service.StockService;
import com.warehouse.service.StockRequestService;
import com.warehouse.enums.StockRequestType;
import com.warehouse.entity.UserRole;
import com.warehouse.util.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import com.warehouse.service.SsePushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(origins = "*")
public class StockController {

    private final StockService stockService;
    private final SsePushService ssePushService;
    private final StockRequestService stockRequestService;
    private static final Logger logger = LoggerFactory.getLogger(StockController.class);

    @Autowired
    public StockController(StockService stockService, SsePushService ssePushService, StockRequestService stockRequestService) {
        this.stockService = stockService;
        this.ssePushService = ssePushService;
        this.stockRequestService = stockRequestService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<PagedResponse<StockDto>> getAllStocks(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long colorId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subCategoryId,
            @RequestParam(required = false) Boolean reservedOnly,
            @RequestParam(required = false) Boolean consignedOnly,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "lastUpdated") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        int safePage = page != null && page >= 0 ? page : 0;
        int safeSize = size != null && size > 0 ? size : 20;
        Sort sort = resolveSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        StockFilter filter = new StockFilter();
        filter.setBrandId(brandId);
        filter.setColorId(colorId);
        filter.setWarehouseId(warehouseId);
        filter.setCategoryId(categoryId);
        filter.setSubCategoryId(subCategoryId);
        filter.setReservedOnly(Boolean.TRUE.equals(reservedOnly));
        filter.setConsignedOnly(Boolean.TRUE.equals(consignedOnly));
        filter.setSearch(search != null && !search.isBlank() ? search.trim() : null);
        filter.setStatus(StockFilter.Status.from(status));

        Page<Stock> stocks = stockService.getStocks(filter, pageable);
        List<StockDto> content = stocks.getContent().stream().map(this::toDto).toList();
        PagedResponse<StockDto> response = new PagedResponse<>(
                content,
                stocks.getNumber(),
                stocks.getSize(),
                stocks.getTotalElements(),
                stocks.getTotalPages(),
                stocks.isFirst(),
                stocks.isLast()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<StockDto> getStockById(@PathVariable Long id) {
        return stockService.getStockById(id)
                .map(stock -> ResponseEntity.ok(toDto(stock)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/product/{productId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<StockDto>> getStocksByProduct(@PathVariable Long productId) {
        List<Stock> stocks = stockService.getStocksByProduct(productId);
        return ResponseEntity.ok(stocks.stream().map(this::toDto).toList());
    }

    @GetMapping("/warehouse/{warehouseId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<StockDto>> getStocksByWarehouse(@PathVariable Long warehouseId) {
        List<Stock> stocks = stockService.getStocksByWarehouse(warehouseId);
        return ResponseEntity.ok(stocks.stream().map(this::toDto).toList());
    }

    @GetMapping("/product/{productId}/warehouse/{warehouseId}")
    @Transactional(readOnly = true)
    public ResponseEntity<StockDto> getStockByProductAndWarehouse(@PathVariable Long productId, @PathVariable Long warehouseId) {
        return stockService.getStockByProductAndWarehouse(productId, warehouseId)
                .map(stock -> ResponseEntity.ok(toDto(stock)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/low-stock")
    @Transactional(readOnly = true)
    public ResponseEntity<List<StockDto>> getLowStockItems() {
        List<Stock> stocks = stockService.getLowStockItems();
        return ResponseEntity.ok(stocks.stream().map(this::toDto).toList());
    }

    @GetMapping("/low-stock/count")
    @Transactional(readOnly = true)
    public ResponseEntity<Long> getLowStockCount() {
        long count = stockService.countLowStockItems();
        return ResponseEntity.ok(count);
    }

    @GetMapping("/out-of-stock")
    @Transactional(readOnly = true)
    public ResponseEntity<List<StockDto>> getOutOfStockItems() {
        List<Stock> stocks = stockService.getOutOfStockItems();
        return ResponseEntity.ok(stocks.stream().map(this::toDto).toList());
    }

    @GetMapping("/warehouse/{warehouseId}/low-stock")
    @Transactional(readOnly = true)
    public ResponseEntity<List<StockDto>> getLowStockItemsByWarehouse(@PathVariable Long warehouseId) {
        List<Stock> stocks = stockService.getLowStockItemsByWarehouse(warehouseId);
        return ResponseEntity.ok(stocks.stream().map(this::toDto).toList());
    }

    @GetMapping("/product/{productId}/total-quantity")
    public ResponseEntity<Long> getTotalQuantityByProduct(@PathVariable Long productId) {
        Long total = stockService.getTotalQuantityByProduct(productId);
        return ResponseEntity.ok(total);
    }

    @GetMapping("/products/total-quantities")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<Long, Long>> getTotalQuantitiesByProducts(@RequestParam(name = "productIds") List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return ResponseEntity.ok(Map.of());
        }
        Map<Long, Long> totals = stockService.getTotalQuantitiesByProductIds(productIds);
        return ResponseEntity.ok(totals);
    }

    @GetMapping("/warehouse/{warehouseId}/total-quantity")
    public ResponseEntity<Long> getTotalQuantityByWarehouse(@PathVariable Long warehouseId) {
        Long total = stockService.getTotalQuantityByWarehouse(warehouseId);
        return ResponseEntity.ok(total);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockDto> createStock(@Valid @RequestBody Stock stock) {
        Stock createdStock = stockService.createStock(stock);
        try {
            ssePushService.broadcastCounts();
            logger.debug("SSE counts broadcasted after createStock. stockId={}", createdStock.getId());
        } catch (Exception e) {
            logger.warn("SSE broadcast failed after createStock. stockId={}", createdStock.getId(), e);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toDtoLean(createdStock));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StockDto>> createStocksBulk(@Valid @RequestBody List<Stock> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<Stock> createdStocks = stockService.createStocks(stocks);
        try {
            ssePushService.broadcastCounts();
            logger.debug("SSE counts broadcasted after createStocksBulk. created={}", createdStocks.size());
        } catch (Exception e) {
            logger.warn("SSE broadcast failed after createStocksBulk", e);
        }
        List<StockDto> response = createdStocks.stream().map(this::toDtoLean).toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockDto> updateStock(@PathVariable Long id, @RequestBody Stock stock) {
        Stock updatedStock = stockService.updateStock(id, stock);
        try {
            ssePushService.broadcastCounts();
            logger.debug("SSE counts broadcasted after updateStock. stockId={}", updatedStock.getId());
        } catch (Exception e) {
            logger.warn("SSE broadcast failed after updateStock. stockId={}", updatedStock.getId(), e);
        }
        return ResponseEntity.ok(toDtoLean(updatedStock));
    }

    /**
     * Add stock - ADMIN: direct operation, Others: creates approval request
     */
    @PutMapping("/{id}/add")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_IN')")
    public ResponseEntity<?> addToStock(@PathVariable Long id, @RequestParam Integer quantity,
                                        @RequestParam(required = false) String notes) {
        String userRole = CurrentUser.getRole();
        
        // ADMIN users can directly add stock
        if ("ADMIN".equals(userRole)) {
            Stock updatedStock = stockService.addToStock(id, quantity);
            try {
                ssePushService.broadcastCounts();
                logger.debug("SSE counts broadcasted after addToStock. stockId={}, quantity={} ", id, quantity);
            } catch (Exception e) {
                logger.warn("SSE broadcast failed after addToStock. stockId={}, quantity={} ", id, quantity, e);
            }
            return ResponseEntity.ok(toDtoLean(updatedStock));
        } else {
            // STOCK_IN users create a request
            var request = stockRequestService.createRequest(id, StockRequestType.ADD, quantity, notes);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("message", "Stok ekleme talebi oluşturuldu. Yönetici onayı bekleniyor.",
                                 "requestId", request.getId()));
        }
    }

    /**
     * Remove stock - ADMIN: direct operation, Others: creates approval request
     */
    @PutMapping("/{id}/remove")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_OUT')")
    public ResponseEntity<?> removeFromStock(@PathVariable Long id, @RequestParam Integer quantity,
                                             @RequestParam(required = false) String notes) {
        String userRole = CurrentUser.getRole();
        
        // ADMIN users can directly remove stock
        if ("ADMIN".equals(userRole)) {
            Stock updatedStock = stockService.removeFromStock(id, quantity);
            try {
                ssePushService.broadcastCounts();
                logger.debug("SSE counts broadcasted after removeFromStock. stockId={}, quantity={} ", id, quantity);
            } catch (Exception e) {
                logger.warn("SSE broadcast failed after removeFromStock. stockId={}, quantity={} ", id, quantity, e);
            }
            return ResponseEntity.ok(toDtoLean(updatedStock));
        } else {
            // STOCK_OUT users create a request
            var request = stockRequestService.createRequest(id, StockRequestType.REMOVE, quantity, notes);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("message", "Stok çıkarma talebi oluşturuldu. Yönetici onayı bekleniyor.",
                                 "requestId", request.getId()));
        }
    }

    @PutMapping("/{id}/reserve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockDto> reserveStock(@PathVariable Long id, @RequestParam Integer quantity) {
        Stock updatedStock = stockService.reserveStock(id, quantity);
        try {
            ssePushService.broadcastCounts();
            logger.debug("SSE counts broadcasted after reserveStock. stockId={}, quantity={} ", id, quantity);
        } catch (Exception e) {
            logger.warn("SSE broadcast failed after reserveStock. stockId={}, quantity={} ", id, quantity, e);
        }
        return ResponseEntity.ok(toDtoLean(updatedStock));
    }

    @PutMapping("/{id}/release")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockDto> releaseStock(@PathVariable Long id, @RequestParam Integer quantity) {
        Stock updatedStock = stockService.releaseStock(id, quantity);
        try {
            ssePushService.broadcastCounts();
            logger.debug("SSE counts broadcasted after releaseStock. stockId={}, quantity={} ", id, quantity);
        } catch (Exception e) {
            logger.warn("SSE broadcast failed after releaseStock. stockId={}, quantity={} ", id, quantity, e);
        }
        return ResponseEntity.ok(toDtoLean(updatedStock));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteStock(@PathVariable Long id) {
        stockService.deleteStock(id);
        try {
            ssePushService.broadcastCounts();
            logger.debug("SSE counts broadcasted after deleteStock. stockId={}", id);
        } catch (Exception e) {
            logger.warn("SSE broadcast failed after deleteStock. stockId={}", id, e);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteStocks(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        stockService.deleteStocks(ids);
        try {
            ssePushService.broadcastCounts();
            logger.debug("SSE counts broadcasted after deleteStocks. deletedCount={}", ids.size());
        } catch (Exception e) {
            logger.warn("SSE broadcast failed after deleteStocks", e);
        }
        return ResponseEntity.noContent().build();
    }

    private StockDto toDto(Stock s) {
        StockDto dto = new StockDto();
        dto.id = s.getId();
        dto.quantity = s.getQuantity();
        dto.reservedQuantity = s.getReservedQuantity();
        dto.consignedQuantity = s.getConsignedQuantity();
        dto.minStockLevel = s.getMinStockLevel();
        dto.availableQuantity = s.getAvailableQuantity();
        dto.lastUpdated = s.getLastUpdated();
        dto.additionNote = s.getAdditionNote();
        if (s.getProduct() != null) {
            StockDto.ProductDto p = new StockDto.ProductDto();
            p.id = s.getProduct().getId();
            p.name = s.getProduct().getName();
            p.sku = s.getProduct().getSku();
            dto.product = p;
        }
        if (s.getWarehouse() != null) {
            StockDto.WarehouseDto w = new StockDto.WarehouseDto();
            w.id = s.getWarehouse().getId();
            w.name = s.getWarehouse().getName();
            w.location = s.getWarehouse().getLocation();
            dto.warehouse = w;
        }
        return dto;
    }

    private StockDto toDtoLean(Stock s) {
        StockDto dto = new StockDto();
        dto.id = s.getId();
        dto.quantity = s.getQuantity();
        dto.reservedQuantity = s.getReservedQuantity();
        dto.consignedQuantity = s.getConsignedQuantity();
        dto.minStockLevel = s.getMinStockLevel();
        dto.availableQuantity = s.getAvailableQuantity();
        dto.lastUpdated = s.getLastUpdated();
        dto.additionNote = s.getAdditionNote();
        if (s.getProduct() != null) {
            StockDto.ProductDto p = new StockDto.ProductDto();
            p.id = s.getProduct().getId();
            dto.product = p;
        }
        if (s.getWarehouse() != null) {
            StockDto.WarehouseDto w = new StockDto.WarehouseDto();
            w.id = s.getWarehouse().getId();
            dto.warehouse = w;
        }
        return dto;
    }

    private Sort resolveSort(String sortBy, String sortDir) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        if ("quantity".equalsIgnoreCase(sortBy)) {
            return Sort.by(direction, "quantity");
        }
        if ("warehouse".equalsIgnoreCase(sortBy)) {
            return JpaSort.unsafe(direction, "warehouse.name");
        }
        if ("product".equalsIgnoreCase(sortBy)) {
            return JpaSort.unsafe(direction, "product.name");
        }
        return Sort.by(direction, "lastUpdated");
    }
}
