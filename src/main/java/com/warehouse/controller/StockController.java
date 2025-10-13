package com.warehouse.controller;

import com.warehouse.entity.Stock;
import com.warehouse.service.StockService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(origins = "*")
public class StockController {

    private final StockService stockService;

    @Autowired
    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    public ResponseEntity<List<Stock>> getAllStocks(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long colorId) {
        if (brandId != null || colorId != null) {
            return ResponseEntity.ok(stockService.getAllStocksFiltered(brandId, colorId));
        }
        List<Stock> stocks = stockService.getAllStocks();
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Stock> getStockById(@PathVariable Long id) {
        return stockService.getStockById(id)
                .map(stock -> ResponseEntity.ok(stock))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<Stock>> getStocksByProduct(@PathVariable Long productId) {
        try {
            List<Stock> stocks = stockService.getStocksByProduct(productId);
            return ResponseEntity.ok(stocks);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<List<Stock>> getStocksByWarehouse(@PathVariable Long warehouseId) {
        try {
            List<Stock> stocks = stockService.getStocksByWarehouse(warehouseId);
            return ResponseEntity.ok(stocks);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/product/{productId}/warehouse/{warehouseId}")
    public ResponseEntity<Stock> getStockByProductAndWarehouse(@PathVariable Long productId, @PathVariable Long warehouseId) {
        return stockService.getStockByProductAndWarehouse(productId, warehouseId)
                .map(stock -> ResponseEntity.ok(stock))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<Stock>> getLowStockItems() {
        List<Stock> stocks = stockService.getLowStockItems();
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/out-of-stock")
    public ResponseEntity<List<Stock>> getOutOfStockItems() {
        List<Stock> stocks = stockService.getOutOfStockItems();
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/warehouse/{warehouseId}/low-stock")
    public ResponseEntity<List<Stock>> getLowStockItemsByWarehouse(@PathVariable Long warehouseId) {
        try {
            List<Stock> stocks = stockService.getLowStockItemsByWarehouse(warehouseId);
            return ResponseEntity.ok(stocks);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/product/{productId}/total-quantity")
    public ResponseEntity<Long> getTotalQuantityByProduct(@PathVariable Long productId) {
        try {
            Long total = stockService.getTotalQuantityByProduct(productId);
            return ResponseEntity.ok(total);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/warehouse/{warehouseId}/total-quantity")
    public ResponseEntity<Long> getTotalQuantityByWarehouse(@PathVariable Long warehouseId) {
        try {
            Long total = stockService.getTotalQuantityByWarehouse(warehouseId);
            return ResponseEntity.ok(total);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<?> createStock(@Valid @RequestBody Stock stock) {
        try {
            Stock createdStock = stockService.createStock(stock);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdStock);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateStock(@PathVariable Long id, @Valid @RequestBody Stock stock) {
        try {
            Stock updatedStock = stockService.updateStock(id, stock);
            return ResponseEntity.ok(updatedStock);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/add")
    public ResponseEntity<?> addToStock(@PathVariable Long id, @RequestParam Integer quantity) {
        try {
            Stock updatedStock = stockService.addToStock(id, quantity);
            return ResponseEntity.ok(updatedStock);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/remove")
    public ResponseEntity<?> removeFromStock(@PathVariable Long id, @RequestParam Integer quantity) {
        try {
            Stock updatedStock = stockService.removeFromStock(id, quantity);
            return ResponseEntity.ok(updatedStock);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/reserve")
    public ResponseEntity<?> reserveStock(@PathVariable Long id, @RequestParam Integer quantity) {
        try {
            Stock updatedStock = stockService.reserveStock(id, quantity);
            return ResponseEntity.ok(updatedStock);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/release")
    public ResponseEntity<?> releaseStock(@PathVariable Long id, @RequestParam Integer quantity) {
        try {
            Stock updatedStock = stockService.releaseStock(id, quantity);
            return ResponseEntity.ok(updatedStock);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStock(@PathVariable Long id) {
        try {
            stockService.deleteStock(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
