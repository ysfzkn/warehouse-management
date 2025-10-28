package com.warehouse.controller;

import com.warehouse.entity.Warehouse;
import com.warehouse.service.WarehouseService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/warehouses")
@CrossOrigin(origins = "*")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @Autowired
    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping
    public ResponseEntity<List<Warehouse>> getAllWarehouses() {
        List<Warehouse> warehouses = warehouseService.getAllWarehouses();
        return ResponseEntity.ok(warehouses);
    }

    @GetMapping("/active")
    public ResponseEntity<List<Warehouse>> getAllActiveWarehouses() {
        List<Warehouse> warehouses = warehouseService.getAllActiveWarehouses();
        return ResponseEntity.ok(warehouses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Warehouse> getWarehouseById(@PathVariable Long id) {
        return warehouseService.getWarehouseById(id)
                .map(warehouse -> ResponseEntity.ok(warehouse))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/with-stocks")
    public ResponseEntity<Warehouse> getWarehouseByIdWithStocks(@PathVariable Long id) {
        return warehouseService.getWarehouseByIdWithStocks(id)
                .map(warehouse -> ResponseEntity.ok(warehouse))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Warehouse> createWarehouse(@Valid @RequestBody Warehouse warehouse) {
        Warehouse createdWarehouse = warehouseService.createWarehouse(warehouse);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdWarehouse);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Warehouse> updateWarehouse(@PathVariable Long id, @Valid @RequestBody Warehouse warehouse) {
        Warehouse updatedWarehouse = warehouseService.updateWarehouse(id, warehouse);
        return ResponseEntity.ok(updatedWarehouse);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWarehouse(@PathVariable Long id) {
        warehouseService.deleteWarehouse(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateWarehouse(@PathVariable Long id) {
        warehouseService.deactivateWarehouse(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<Void> activateWarehouse(@PathVariable Long id) {
        warehouseService.activateWarehouse(id);
        return ResponseEntity.ok().build();
    }
}
