package com.warehouse.service;

import com.warehouse.entity.Stock;
import com.warehouse.entity.Product;
import com.warehouse.entity.Warehouse;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.WarehouseRepository;
import com.warehouse.util.EntityValidator;
import com.warehouse.util.ValidationUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class StockService {

    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;

    public StockService(StockRepository stockRepository,
                       ProductRepository productRepository,
                       WarehouseRepository warehouseRepository) {
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional(readOnly = true)
    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Stock> getAllStocksFiltered(Long brandId, Long colorId, Long warehouseId) {
        List<Stock> stocks = stockRepository.findAll();
        return stocks.stream()
                .filter(stock -> matchesBrandFilter(stock, brandId))
                .filter(stock -> matchesColorFilter(stock, colorId))
                .filter(stock -> matchesWarehouseFilter(stock, warehouseId))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Stock> getStockById(Long id) {
        return stockRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Stock getStockByIdOrThrow(Long id) {
        return stockRepository.findById(id)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.STOCK_NOT_FOUND, "ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<Stock> getStocksByProduct(Long productId) {
        Product product = findProductOrThrow(productId);
        return stockRepository.findByProduct(product);
    }

    @Transactional(readOnly = true)
    public List<Stock> getStocksByWarehouse(Long warehouseId) {
        Warehouse warehouse = findWarehouseOrThrow(warehouseId);
        return stockRepository.findByWarehouse(warehouse);
    }

    @Transactional(readOnly = true)
    public Optional<Stock> getStockByProductAndWarehouse(Long productId, Long warehouseId) {
        Product product = findProductOrThrow(productId);
        Warehouse warehouse = findWarehouseOrThrow(warehouseId);
        return stockRepository.findByProductAndWarehouse(product, warehouse);
    }

    @Transactional(readOnly = true)
    public List<Stock> getLowStockItems() {
        return stockRepository.findLowStockItems();
    }

    @Transactional(readOnly = true)
    public List<Stock> getOutOfStockItems() {
        return stockRepository.findOutOfStockItems();
    }

    @Transactional(readOnly = true)
    public List<Stock> getLowStockItemsByWarehouse(Long warehouseId) {
        Warehouse warehouse = findWarehouseOrThrow(warehouseId);
        return stockRepository.findLowStockItemsByWarehouse(warehouse);
    }

    @Transactional(readOnly = true)
    public Long getTotalQuantityByProduct(Long productId) {
        Product product = findProductOrThrow(productId);
        Long total = stockRepository.getTotalQuantityByProduct(product);
        return total != null ? total : 0L;
    }

    @Transactional(readOnly = true)
    public Long getTotalQuantityByWarehouse(Long warehouseId) {
        Warehouse warehouse = findWarehouseOrThrow(warehouseId);
        Long total = stockRepository.getTotalQuantityByWarehouse(warehouse);
        return total != null ? total : 0L;
    }

    public Stock createStock(Stock stock) {
        EntityValidator.validateStockForCreation(stock);
        
        Product product = findProductOrThrow(stock.getProduct().getId());
        Warehouse warehouse = findWarehouseOrThrow(stock.getWarehouse().getId());
        
        checkStockDuplication(product, warehouse);
        
        stock.setProduct(product);
        stock.setWarehouse(warehouse);
        
        return stockRepository.save(stock);
    }

    public Stock updateStock(Long id, Stock stockDetails) {
        Stock stock = getStockByIdOrThrow(id);
        
        updateStockQuantity(stock, stockDetails.getQuantity());
        updateMinStockLevel(stock, stockDetails.getMinStockLevel());
        updateReservedQuantity(stock, stockDetails.getReservedQuantity());
        updateConsignedQuantity(stock, stockDetails.getConsignedQuantity());
        
        return stockRepository.save(stock);
    }

    public Stock addToStock(Long stockId, Integer quantity) {
        ValidationUtil.requirePositive(quantity, "Quantity to add");
        Stock stock = getStockByIdOrThrow(stockId);
        stock.setQuantity(stock.getQuantity() + quantity);
        return stockRepository.save(stock);
    }

    public Stock removeFromStock(Long stockId, Integer quantity) {
        ValidationUtil.requirePositive(quantity, "Quantity to remove");
        Stock stock = getStockByIdOrThrow(stockId);
        stock.setQuantity(stock.getQuantity() - quantity);
        return stockRepository.save(stock);
    }

    public void deleteStock(Long id) {
        Stock stock = getStockByIdOrThrow(id);
        stockRepository.delete(stock);
    }

    public Stock reserveStock(Long stockId, Integer quantity) {
        ValidationUtil.requirePositive(quantity, "Quantity to reserve");
        Stock stock = getStockByIdOrThrow(stockId);
        
        Integer availableQuantity = stock.getAvailableQuantity();
        if (availableQuantity < quantity) {
            throw new WarehouseManagementException(ErrorCode.INSUFFICIENT_STOCK, 
                String.format("Available: %d, Requested: %d", availableQuantity, quantity));
        }
        
        stock.setReservedQuantity(stock.getReservedQuantity() + quantity);
        return stockRepository.save(stock);
    }

    public Stock releaseStock(Long stockId, Integer quantity) {
        ValidationUtil.requirePositive(quantity, "Quantity to release");
        Stock stock = getStockByIdOrThrow(stockId);
        
        if (stock.getReservedQuantity() < quantity) {
            throw new WarehouseManagementException(ErrorCode.INSUFFICIENT_RESERVED_STOCK,
                String.format("Reserved: %d, Requested: %d", stock.getReservedQuantity(), quantity));
        }
        
        stock.setReservedQuantity(stock.getReservedQuantity() - quantity);
        return stockRepository.save(stock);
    }

    private Product findProductOrThrow(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND, "ID: " + productId));
    }

    private Warehouse findWarehouseOrThrow(Long warehouseId) {
        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.WAREHOUSE_NOT_FOUND, "ID: " + warehouseId));
    }

    private void checkStockDuplication(Product product, Warehouse warehouse) {
        Optional<Stock> existingStock = stockRepository.findByProductAndWarehouse(product, warehouse);
        if (existingStock.isPresent()) {
            throw new WarehouseManagementException(ErrorCode.STOCK_ALREADY_EXISTS,
                String.format("Product: %s, Warehouse: %s", product.getName(), warehouse.getName()));
        }
    }

    private boolean matchesBrandFilter(Stock stock, Long brandId) {
        return brandId == null || 
               (stock.getProduct() != null && 
                stock.getProduct().getBrand() != null && 
                brandId.equals(stock.getProduct().getBrand().getId()));
    }

    private boolean matchesColorFilter(Stock stock, Long colorId) {
        return colorId == null || 
               (stock.getProduct() != null && 
                stock.getProduct().getColor() != null && 
                colorId.equals(stock.getProduct().getColor().getId()));
    }

    private boolean matchesWarehouseFilter(Stock stock, Long warehouseId) {
        return warehouseId == null || 
               (stock.getWarehouse() != null && 
                warehouseId.equals(stock.getWarehouse().getId()));
    }

    private void updateStockQuantity(Stock stock, Integer quantity) {
        if (quantity != null) {
            ValidationUtil.requireNonNegative(quantity, "Quantity");
            stock.setQuantity(quantity);
        }
    }

    private void updateMinStockLevel(Stock stock, Integer minStockLevel) {
        if (minStockLevel != null) {
            ValidationUtil.requireNonNegative(minStockLevel, "Minimum stock level");
            stock.setMinStockLevel(minStockLevel);
        }
    }

    private void updateReservedQuantity(Stock stock, Integer reservedQuantity) {
        if (reservedQuantity != null) {
            ValidationUtil.requireNonNegative(reservedQuantity, "Reserved quantity");
            stock.setReservedQuantity(reservedQuantity);
        }
    }

    private void updateConsignedQuantity(Stock stock, Integer consignedQuantity) {
        if (consignedQuantity != null) {
            ValidationUtil.requireNonNegative(consignedQuantity, "Consigned quantity");
            stock.setConsignedQuantity(consignedQuantity);
        }
    }
}
