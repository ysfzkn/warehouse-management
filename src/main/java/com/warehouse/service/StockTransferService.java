package com.warehouse.service;

import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.Stock;
import com.warehouse.entity.Product;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.TransferStatus;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.WarehouseRepository;
import com.warehouse.util.EntityValidator;
import com.warehouse.util.ValidationUtil;
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

    public StockTransferService(StockTransferRepository stockTransferRepository,
                                StockRepository stockRepository,
                                ProductRepository productRepository,
                                WarehouseRepository warehouseRepository) {
        this.stockTransferRepository = stockTransferRepository;
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional(readOnly = true)
    public List<StockTransfer> getAllTransfers() {
        return stockTransferRepository.findAllOrderByTransferDateDesc();
    }

    @Transactional(readOnly = true)
    public Optional<StockTransfer> getTransferById(Long id) {
        return stockTransferRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public StockTransfer getTransferByIdOrThrow(Long id) {
        return stockTransferRepository.findById(id)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.TRANSFER_NOT_FOUND, "ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<StockTransfer> getTransfersByWarehouse(Long warehouseId) {
        Warehouse warehouse = findWarehouseOrThrow(warehouseId);
        return stockTransferRepository.findByWarehouse(warehouse);
    }

    @Transactional(readOnly = true)
    public List<StockTransfer> getTransfersByProduct(Long productId) {
        Product product = findProductOrThrow(productId);
        return stockTransferRepository.findByProduct(product);
    }

    @Transactional(readOnly = true)
    public List<StockTransfer> getTransfersByStatus(TransferStatus status) {
        return stockTransferRepository.findByStatus(status);
    }

    public StockTransfer createTransfer(StockTransfer transfer) {
        validateTransferCreation(transfer);
        
        Warehouse sourceWarehouse = findWarehouseOrThrow(transfer.getSourceWarehouse().getId());
        Warehouse destinationWarehouse = findWarehouseOrThrow(transfer.getDestinationWarehouse().getId());
        Product product = findProductOrThrow(transfer.getProduct().getId());
        
        EntityValidator.validateWarehousesDifferent(sourceWarehouse, destinationWarehouse);
        ValidationUtil.requirePositive(transfer.getQuantity(), "Quantity");
        
        validateSufficientStock(product, sourceWarehouse, transfer.getQuantity());
        
        transfer.setSourceWarehouse(sourceWarehouse);
        transfer.setDestinationWarehouse(destinationWarehouse);
        transfer.setProduct(product);
        transfer.setStatus(TransferStatus.PENDING);
        
        return stockTransferRepository.save(transfer);
    }

    public StockTransfer startTransfer(Long transferId) {
        StockTransfer transfer = getTransferByIdOrThrow(transferId);
        
        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new WarehouseManagementException(ErrorCode.ONLY_PENDING_CAN_BE_STARTED, 
                "Current status: " + transfer.getStatus());
        }
        
        Stock sourceStock = findSourceStockOrThrow(transfer);
        validateSufficientAvailableStock(sourceStock, transfer.getQuantity());
        
        reserveStockForTransfer(sourceStock, transfer.getQuantity());
        
        transfer.setStatus(TransferStatus.IN_TRANSIT);
        return stockTransferRepository.save(transfer);
    }

    public StockTransfer completeTransfer(Long transferId) {
        StockTransfer transfer = getTransferByIdOrThrow(transferId);
        
        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            throw new WarehouseManagementException(ErrorCode.TRANSFER_ALREADY_COMPLETED);
        }
        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            throw new WarehouseManagementException(ErrorCode.CANNOT_CANCEL_COMPLETED);
        }
        
        Stock sourceStock = findSourceStockOrThrow(transfer);
        
        if (transfer.getStatus() == TransferStatus.PENDING) {
            deductStockDirectly(sourceStock, transfer.getQuantity());
        } else if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
            deductReservedStock(sourceStock, transfer.getQuantity());
        }
        
        addStockToDestination(transfer);
        
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setCompletedDate(LocalDateTime.now());
        return stockTransferRepository.save(transfer);
    }

    public StockTransfer cancelTransfer(Long transferId, String cancellationReason) {
        StockTransfer transfer = getTransferByIdOrThrow(transferId);
        
        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            throw new WarehouseManagementException(ErrorCode.CANNOT_CANCEL_COMPLETED);
        }
        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            throw new WarehouseManagementException(ErrorCode.TRANSFER_ALREADY_CANCELLED);
        }
        
        if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
            Stock sourceStock = findSourceStockOrThrow(transfer);
            releaseReservedStock(sourceStock, transfer.getQuantity());
        }
        
        transfer.setStatus(TransferStatus.CANCELLED);
        transfer.setCancelledDate(LocalDateTime.now());
        transfer.setCancellationReason(cancellationReason);
        return stockTransferRepository.save(transfer);
    }

    public StockTransfer updateTransfer(Long transferId, StockTransfer updatedTransfer) {
        StockTransfer transfer = getTransferByIdOrThrow(transferId);
        
        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new WarehouseManagementException(ErrorCode.ONLY_PENDING_CAN_BE_UPDATED, 
                "Current status: " + transfer.getStatus());
        }
        
        updateTransferFields(transfer, updatedTransfer);
        
        return stockTransferRepository.save(transfer);
    }

    public void deleteTransfer(Long transferId) {
        StockTransfer transfer = getTransferByIdOrThrow(transferId);
        
        if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
            throw new WarehouseManagementException(ErrorCode.CANNOT_DELETE_IN_TRANSIT);
        }
        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            throw new WarehouseManagementException(ErrorCode.CANNOT_DELETE_COMPLETED);
        }
        
        stockTransferRepository.delete(transfer);
    }

    private void validateTransferCreation(StockTransfer transfer) {
        ValidationUtil.requireNonNull(transfer.getSourceWarehouse(), "Source warehouse");
        ValidationUtil.requireNonNull(transfer.getSourceWarehouse().getId(), "Source warehouse ID");
        ValidationUtil.requireNonNull(transfer.getDestinationWarehouse(), "Destination warehouse");
        ValidationUtil.requireNonNull(transfer.getDestinationWarehouse().getId(), "Destination warehouse ID");
        ValidationUtil.requireNonNull(transfer.getProduct(), "Product");
        ValidationUtil.requireNonNull(transfer.getProduct().getId(), "Product ID");
    }

    private void validateSufficientStock(Product product, Warehouse warehouse, Integer quantity) {
        Optional<Stock> stockOpt = stockRepository.findByProductAndWarehouse(product, warehouse);
        if (stockOpt.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.PRODUCT_NOT_IN_WAREHOUSE, 
                String.format("Product: %s, Warehouse: %s", product.getName(), warehouse.getName()));
        }
        
        Stock stock = stockOpt.get();
        if (stock.getAvailableQuantity() < quantity) {
            throw new WarehouseManagementException(ErrorCode.INSUFFICIENT_STOCK, 
                String.format("Available: %d, Requested: %d", stock.getAvailableQuantity(), quantity));
        }
    }

    private void validateSufficientAvailableStock(Stock stock, Integer quantity) {
        if (stock.getAvailableQuantity() < quantity) {
            throw new WarehouseManagementException(ErrorCode.INSUFFICIENT_STOCK, 
                String.format("Available: %d, Requested: %d", stock.getAvailableQuantity(), quantity));
        }
    }

    private Stock findSourceStockOrThrow(StockTransfer transfer) {
        return stockRepository.findByProductAndWarehouse(transfer.getProduct(), transfer.getSourceWarehouse())
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_IN_WAREHOUSE, 
                    "Source warehouse stock not found"));
    }

    private void reserveStockForTransfer(Stock stock, Integer quantity) {
        stock.setReservedQuantity(stock.getReservedQuantity() + quantity);
        stockRepository.save(stock);
    }

    private void releaseReservedStock(Stock stock, Integer quantity) {
        stock.setReservedQuantity(stock.getReservedQuantity() - quantity);
        stockRepository.save(stock);
    }

    private void deductStockDirectly(Stock stock, Integer quantity) {
        stock.setQuantity(stock.getQuantity() - quantity);
        stockRepository.save(stock);
    }

    private void deductReservedStock(Stock stock, Integer quantity) {
        stock.setQuantity(stock.getQuantity() - quantity);
        stock.setReservedQuantity(stock.getReservedQuantity() - quantity);
        stockRepository.save(stock);
    }

    private void addStockToDestination(StockTransfer transfer) {
        Optional<Stock> destinationStockOpt = stockRepository.findByProductAndWarehouse(
                transfer.getProduct(), transfer.getDestinationWarehouse());
        
        Stock destinationStock;
        if (destinationStockOpt.isPresent()) {
            destinationStock = destinationStockOpt.get();
            destinationStock.setQuantity(destinationStock.getQuantity() + transfer.getQuantity());
        } else {
            destinationStock = createNewStock(transfer);
        }
        
        stockRepository.save(destinationStock);
    }

    private Stock createNewStock(StockTransfer transfer) {
        Stock stock = new Stock();
        stock.setProduct(transfer.getProduct());
        stock.setWarehouse(transfer.getDestinationWarehouse());
        stock.setQuantity(transfer.getQuantity());
        stock.setMinStockLevel(0);
        stock.setReservedQuantity(0);
        stock.setConsignedQuantity(0);
        return stock;
    }

    private void updateTransferFields(StockTransfer transfer, StockTransfer updatedTransfer) {
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
    }

    private Product findProductOrThrow(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND, "ID: " + productId));
    }

    private Warehouse findWarehouseOrThrow(Long warehouseId) {
        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.WAREHOUSE_NOT_FOUND, "ID: " + warehouseId));
    }
}
