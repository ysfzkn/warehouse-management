package com.warehouse.controller;

import com.warehouse.dto.StockTransferCreateRequest;
import com.warehouse.dto.StockTransferDto;
import com.warehouse.entity.Product;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.StockTransferItem;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.mapper.StockTransferMapper;
import com.warehouse.service.StockTransferService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @GetMapping("/current-user")
    public ResponseEntity<List<StockTransferDto>> getCurrentUserTransfers() {
        List<StockTransfer> transfers = stockTransferService.getTransfersForCurrentUser();
        List<StockTransferDto> dtos = transferMapper.toDtoList(transfers);
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<StockTransferDto> createTransfer(@Valid @RequestBody StockTransferCreateRequest request) {
        StockTransfer transfer = mapToEntity(request);
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
            transfer.setCustomerFullName(request.getCustomerFullName() != null ? request.getCustomerFullName().trim() : null);
            transfer.setCustomerPhone(request.getCustomerPhone() != null ? request.getCustomerPhone().trim() : null);
            transfer.setCustomerAddress(request.getCustomerAddress() != null ? request.getCustomerAddress().trim() : null);
        } else {
            transfer.setCustomerFullName(null);
            transfer.setCustomerPhone(null);
            transfer.setCustomerAddress(null);
        }

        if (request.getItems() != null) {
            transfer.setItems(
                    request.getItems().stream().map(itemRequest -> {
                        StockTransferItem item = new StockTransferItem();
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
