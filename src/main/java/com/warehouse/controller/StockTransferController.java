package com.warehouse.controller;

import com.warehouse.dto.StockTransferDto;
import com.warehouse.entity.StockTransfer;
import com.warehouse.enums.TransferStatus;
import com.warehouse.mapper.StockTransferMapper;
import com.warehouse.service.StockTransferService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock-transfers")
@CrossOrigin(origins = "*")
public class StockTransferController {

    private final StockTransferService stockTransferService;
    private final StockTransferMapper transferMapper;

    @Autowired
    public StockTransferController(StockTransferService stockTransferService, StockTransferMapper transferMapper) {
        this.stockTransferService = stockTransferService;
        this.transferMapper = transferMapper;
    }

    @GetMapping
    public ResponseEntity<List<StockTransferDto>> getAllTransfers() {
        List<StockTransfer> transfers = stockTransferService.getAllTransfers();
        List<StockTransferDto> dtos = transferMapper.toDtoList(transfers);
        return ResponseEntity.ok(dtos);
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

    @PostMapping
    public ResponseEntity<StockTransferDto> createTransfer(@Valid @RequestBody StockTransfer transfer) {
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
    public ResponseEntity<StockTransferDto> completeTransfer(@PathVariable Long id) {
        StockTransfer transfer = stockTransferService.completeTransfer(id);
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

    @PutMapping("/{id}")
    public ResponseEntity<StockTransferDto> updateTransfer(@PathVariable Long id, @Valid @RequestBody StockTransfer transfer) {
        StockTransfer updatedTransfer = stockTransferService.updateTransfer(id, transfer);
        StockTransferDto dto = transferMapper.toDto(updatedTransfer);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransfer(@PathVariable Long id) {
        stockTransferService.deleteTransfer(id);
        return ResponseEntity.noContent().build();
    }
}
