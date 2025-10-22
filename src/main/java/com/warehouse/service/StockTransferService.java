package com.warehouse.service;

import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.Stock;
import com.warehouse.entity.Product;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.TransferStatus;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;

    @Autowired
    public StockTransferService(StockTransferRepository stockTransferRepository,
                                StockRepository stockRepository,
                                ProductRepository productRepository,
                                WarehouseRepository warehouseRepository) {
        this.stockTransferRepository = stockTransferRepository;
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
    }

    public List<StockTransfer> getAllTransfers() {
        return stockTransferRepository.findAllOrderByTransferDateDesc();
    }

    public Optional<StockTransfer> getTransferById(Long id) {
        return stockTransferRepository.findById(id);
    }

    public List<StockTransfer> getTransfersByWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Warehouse not found with id: " + warehouseId));
        return stockTransferRepository.findByWarehouse(warehouse);
    }

    public List<StockTransfer> getTransfersByProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        return stockTransferRepository.findByProduct(product);
    }

    public List<StockTransfer> getTransfersByStatus(TransferStatus status) {
        return stockTransferRepository.findByStatus(status);
    }
    public StockTransfer createTransfer(StockTransfer transfer) {
        // Validate warehouses
        if (transfer.getSourceWarehouse() == null || transfer.getSourceWarehouse().getId() == null) {
            throw new RuntimeException("Source warehouse is required");
        }
        if (transfer.getDestinationWarehouse() == null || transfer.getDestinationWarehouse().getId() == null) {
            throw new RuntimeException("Destination warehouse is required");
        }

        Warehouse sourceWarehouse = warehouseRepository.findById(transfer.getSourceWarehouse().getId())
                .orElseThrow(() -> new RuntimeException("Source warehouse not found"));
        Warehouse destinationWarehouse = warehouseRepository.findById(transfer.getDestinationWarehouse().getId())
                .orElseThrow(() -> new RuntimeException("Destination warehouse not found"));

        // Validate warehouses are different
        if (sourceWarehouse.getId().equals(destinationWarehouse.getId())) {
            throw new RuntimeException("Source and destination warehouses must be different");
        }

        // Validate product
        if (transfer.getProduct() == null || transfer.getProduct().getId() == null) {
            throw new RuntimeException("Product is required");
        }

        Product product = productRepository.findById(transfer.getProduct().getId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // Validate quantity
        if (transfer.getQuantity() == null || transfer.getQuantity() <= 0) {
            throw new RuntimeException("Quantity must be greater than 0");
        }

        // Check if source warehouse has enough stock
        Optional<Stock> sourceStockOpt = stockRepository.findByProductAndWarehouse(product, sourceWarehouse);
        if (sourceStockOpt.isEmpty()) {
            throw new RuntimeException("Product not found in source warehouse");
        }

        Stock sourceStock = sourceStockOpt.get();
        if (sourceStock.getAvailableQuantity() < transfer.getQuantity()) {
            throw new RuntimeException(
                    String.format("Insufficient available stock in source warehouse. Available: %d, Requested: %d",
                            sourceStock.getAvailableQuantity(), transfer.getQuantity())
            );
        }

        // Set validated entities
        transfer.setSourceWarehouse(sourceWarehouse);
        transfer.setDestinationWarehouse(destinationWarehouse);
        transfer.setProduct(product);
        transfer.setStatus(TransferStatus.PENDING);

        return stockTransferRepository.save(transfer);
    }

    public StockTransfer startTransfer(Long transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found with id: " + transferId));

        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new RuntimeException("Only PENDING transfers can be started. Current status: " + transfer.getStatus());
        }

        // Reserve stock in source warehouse
        Stock sourceStock = stockRepository.findByProductAndWarehouse(transfer.getProduct(), transfer.getSourceWarehouse())
                .orElseThrow(() -> new RuntimeException("Source stock not found"));

        if (sourceStock.getAvailableQuantity() < transfer.getQuantity()) {
            throw new RuntimeException(
                    String.format("Insufficient available stock. Available: %d, Required: %d",
                            sourceStock.getAvailableQuantity(), transfer.getQuantity())
            );
        }

        // Reserve the stock
        sourceStock.setReservedQuantity(sourceStock.getReservedQuantity() + transfer.getQuantity());
        stockRepository.save(sourceStock);

        transfer.setStatus(TransferStatus.IN_TRANSIT);
        return stockTransferRepository.save(transfer);
    }

    public StockTransfer completeTransfer(Long transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found with id: " + transferId));

        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            throw new RuntimeException("Transfer is already completed");
        }
        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            throw new RuntimeException("Cannot complete a cancelled transfer");
        }

        Stock sourceStock = stockRepository.findByProductAndWarehouse(transfer.getProduct(), transfer.getSourceWarehouse())
                .orElseThrow(() -> new RuntimeException("Source stock not found"));

        if (transfer.getStatus() == TransferStatus.PENDING) {
            if (sourceStock.getAvailableQuantity() < transfer.getQuantity()) {
                throw new RuntimeException(
                        String.format("Insufficient available stock. Available: %d, Required: %d",
                                sourceStock.getAvailableQuantity(), transfer.getQuantity())
                );
            }
            sourceStock.setQuantity(sourceStock.getQuantity() - transfer.getQuantity());
        } else if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
            sourceStock.setQuantity(sourceStock.getQuantity() - transfer.getQuantity());
            sourceStock.setReservedQuantity(sourceStock.getReservedQuantity() - transfer.getQuantity());
        }

        stockRepository.save(sourceStock);

        Optional<Stock> destinationStockOpt = stockRepository.findByProductAndWarehouse(
                transfer.getProduct(), transfer.getDestinationWarehouse());

        Stock destinationStock;
        if (destinationStockOpt.isPresent()) {
            destinationStock = destinationStockOpt.get();
            destinationStock.setQuantity(destinationStock.getQuantity() + transfer.getQuantity());
        } else {
            destinationStock = new Stock();
            destinationStock.setProduct(transfer.getProduct());
            destinationStock.setWarehouse(transfer.getDestinationWarehouse());
            destinationStock.setQuantity(transfer.getQuantity());
            destinationStock.setMinStockLevel(0);
            destinationStock.setReservedQuantity(0);
            destinationStock.setConsignedQuantity(0);
        }

        stockRepository.save(destinationStock);

        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setCompletedDate(LocalDateTime.now());
        return stockTransferRepository.save(transfer);
    }

    public StockTransfer cancelTransfer(Long transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found with id: " + transferId));

        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            throw new RuntimeException("Cannot cancel a completed transfer");
        }
        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            throw new RuntimeException("Transfer is already cancelled");
        }

        if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
            Stock sourceStock = stockRepository.findByProductAndWarehouse(transfer.getProduct(), transfer.getSourceWarehouse())
                    .orElseThrow(() -> new RuntimeException("Source stock not found"));

            sourceStock.setReservedQuantity(sourceStock.getReservedQuantity() - transfer.getQuantity());
            stockRepository.save(sourceStock);
        }

        transfer.setStatus(TransferStatus.CANCELLED);
        transfer.setCancelledDate(LocalDateTime.now());
        return stockTransferRepository.save(transfer);
    }

    public StockTransfer updateTransfer(Long transferId, StockTransfer updatedTransfer) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found with id: " + transferId));

        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new RuntimeException("Only PENDING transfers can be updated");
        }

        // Update allowed fields
        if (updatedTransfer.getDriverName() != null) {
            transfer.setDriverName(updatedTransfer.getDriverName());
        }
        if (updatedTransfer.getDriverTcId() != null) {
            transfer.setDriverTcId(updatedTransfer.getDriverTcId());
        }
        if (updatedTransfer.getDriverPhone() != null) {
            transfer.setDriverPhone(updatedTransfer.getDriverPhone());
        }
        if (updatedTransfer.getVehiclePlate() != null) {
            transfer.setVehiclePlate(updatedTransfer.getVehiclePlate());
        }
        if (updatedTransfer.getNotes() != null) {
            transfer.setNotes(updatedTransfer.getNotes());
        }
        if (updatedTransfer.getTransferDate() != null) {
            transfer.setTransferDate(updatedTransfer.getTransferDate());
        }

        return stockTransferRepository.save(transfer);
    }

    public void deleteTransfer(Long transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found with id: " + transferId));

        if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
            throw new RuntimeException("Cannot delete a transfer that is IN_TRANSIT. Cancel it first.");
        }
        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            throw new RuntimeException("Cannot delete a completed transfer");
        }

        stockTransferRepository.delete(transfer);
    }
}

 