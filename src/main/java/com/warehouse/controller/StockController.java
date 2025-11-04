package com.warehouse.controller;

import com.warehouse.entity.Stock;
import com.warehouse.dto.StockDto;
import com.warehouse.service.StockService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import com.warehouse.service.SsePushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(origins = "*")
public class StockController {

    private final StockService stockService;
    private final SsePushService ssePushService;
    private static final Logger logger = LoggerFactory.getLogger(StockController.class);

    @Autowired
    public StockController(StockService stockService, SsePushService ssePushService) {
        this.stockService = stockService;
        this.ssePushService = ssePushService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<StockDto>> getAllStocks(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long colorId,
            @RequestParam(required = false) Long warehouseId) {
        List<Stock> stocks = (brandId != null || colorId != null || warehouseId != null)
                ? stockService.getAllStocksFiltered(brandId, colorId, warehouseId)
                : stockService.getAllStocks();
        return ResponseEntity.ok(stocks.stream().map(this::toDto).toList());
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

    @GetMapping("/warehouse/{warehouseId}/total-quantity")
    public ResponseEntity<Long> getTotalQuantityByWarehouse(@PathVariable Long warehouseId) {
        Long total = stockService.getTotalQuantityByWarehouse(warehouseId);
        return ResponseEntity.ok(total);
    }

    @PostMapping
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

    @PutMapping("/{id}")
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

    @PutMapping("/{id}/add")
    public ResponseEntity<StockDto> addToStock(@PathVariable Long id, @RequestParam Integer quantity) {
        Stock updatedStock = stockService.addToStock(id, quantity);
        try {
            ssePushService.broadcastCounts();
            logger.debug("SSE counts broadcasted after addToStock. stockId={}, quantity={} ", id, quantity);
        } catch (Exception e) {
            logger.warn("SSE broadcast failed after addToStock. stockId={}, quantity={} ", id, quantity, e);
        }
        return ResponseEntity.ok(toDtoLean(updatedStock));
    }

    @PutMapping("/{id}/remove")
    public ResponseEntity<StockDto> removeFromStock(@PathVariable Long id, @RequestParam Integer quantity) {
        Stock updatedStock = stockService.removeFromStock(id, quantity);
        try {
            ssePushService.broadcastCounts();
            logger.debug("SSE counts broadcasted after removeFromStock. stockId={}, quantity={} ", id, quantity);
        } catch (Exception e) {
            logger.warn("SSE broadcast failed after removeFromStock. stockId={}, quantity={} ", id, quantity, e);
        }
        return ResponseEntity.ok(toDtoLean(updatedStock));
    }

    @PutMapping("/{id}/reserve")
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

    private StockDto toDto(Stock s) {
        StockDto dto = new StockDto();
        dto.id = s.getId();
        dto.quantity = s.getQuantity();
        dto.reservedQuantity = s.getReservedQuantity();
        dto.consignedQuantity = s.getConsignedQuantity();
        dto.minStockLevel = s.getMinStockLevel();
        dto.availableQuantity = s.getAvailableQuantity();
        dto.lastUpdated = s.getLastUpdated();
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
}
