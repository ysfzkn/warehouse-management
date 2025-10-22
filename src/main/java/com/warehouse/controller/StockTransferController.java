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
        try {
            List<StockTransfer> transfers = stockTransferService.getAllTransfers();
            List<StockTransferDto> dtos = transferMapper.toDtoList(transfers);
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTransferById(@PathVariable Long id) {
        try {
            return stockTransferService.getTransferById(id)
                    .map(transferMapper::toDto)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving transfer: " + e.getMessage());
        }
    }

    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<?> getTransfersByWarehouse(@PathVariable Long warehouseId) {
        try {
            List<StockTransfer> transfers = stockTransferService.getTransfersByWarehouse(warehouseId);
            List<StockTransferDto> dtos = transferMapper.toDtoList(transfers);
            return ResponseEntity.ok(dtos);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving transfers: " + e.getMessage());
        }
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<?> getTransfersByProduct(@PathVariable Long productId) {
        try {
            List<StockTransfer> transfers = stockTransferService.getTransfersByProduct(productId);
            List<StockTransferDto> dtos = transferMapper.toDtoList(transfers);
            return ResponseEntity.ok(dtos);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving transfers: " + e.getMessage());
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<?> getTransfersByStatus(@PathVariable String status) {
        try {
            TransferStatus transferStatus = TransferStatus.valueOf(status.toUpperCase());
            List<StockTransfer> transfers = stockTransferService.getTransfersByStatus(transferStatus);
            List<StockTransferDto> dtos = transferMapper.toDtoList(transfers);
            return ResponseEntity.ok(dtos);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid status. Valid values: PENDING, IN_TRANSIT, COMPLETED, CANCELLED");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving transfers: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> createTransfer(@Valid @RequestBody StockTransfer transfer) {
        try {
            StockTransfer createdTransfer = stockTransferService.createTransfer(transfer);
            StockTransferDto dto = transferMapper.toDto(createdTransfer);
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating transfer: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<?> startTransfer(@PathVariable Long id) {
        try {
            StockTransfer transfer = stockTransferService.startTransfer(id);
            StockTransferDto dto = transferMapper.toDto(transfer);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error starting transfer: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<?> completeTransfer(@PathVariable Long id) {
        try {
            StockTransfer transfer = stockTransferService.completeTransfer(id);
            StockTransferDto dto = transferMapper.toDto(transfer);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error completing transfer: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelTransfer(@PathVariable Long id) {
        try {
            StockTransfer transfer = stockTransferService.cancelTransfer(id);
            StockTransferDto dto = transferMapper.toDto(transfer);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error cancelling transfer: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTransfer(@PathVariable Long id, @Valid @RequestBody StockTransfer transfer) {
        try {
            StockTransfer updatedTransfer = stockTransferService.updateTransfer(id, transfer);
            StockTransferDto dto = transferMapper.toDto(updatedTransfer);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating transfer: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTransfer(@PathVariable Long id) {
        try {
            stockTransferService.deleteTransfer(id);
            return ResponseEntity.ok("Transfer deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting transfer: " + e.getMessage());
        }
    }
}
