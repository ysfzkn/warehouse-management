package com.warehouse.service.impl;

import com.warehouse.entity.Stock;
import com.warehouse.entity.Product;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.AuditAction;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.WarehouseRepository;
import com.warehouse.service.AuditService;
import com.warehouse.service.NotificationService;
import com.warehouse.service.StockService;
import com.warehouse.util.CurrentUser;
import com.warehouse.util.EntityValidator;
import com.warehouse.util.StockQuantityValidator;
import com.warehouse.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of StockService for managing stock operations.
 */
@Service
@Transactional
public class StockServiceImpl implements StockService {

    private static final Logger logger = LoggerFactory.getLogger(StockServiceImpl.class);

    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public StockServiceImpl(StockRepository stockRepository,
                           ProductRepository productRepository,
                           WarehouseRepository warehouseRepository,
                           AuditService auditService,
                           NotificationService notificationService) {
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Stock> getAllStocks() {
        logger.debug("Fetching all stocks");
        return stockRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Stock> getAllStocksFiltered(Long brandId, Long colorId, Long warehouseId) {
        logger.debug("Fetching filtered stocks - brandId: {}, colorId: {}, warehouseId: {}", brandId, colorId, warehouseId);
        List<Stock> stocks = stockRepository.findAll();
        return stocks.stream()
                .filter(stock -> matchesBrandFilter(stock, brandId))
                .filter(stock -> matchesColorFilter(stock, colorId))
                .filter(stock -> matchesWarehouseFilter(stock, warehouseId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Stock> getStockById(Long id) {
        logger.debug("Fetching stock by id: {}", id);
        return stockRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Stock getStockByIdOrThrow(Long id) {
        logger.debug("Fetching stock by id or throw: {}", id);
        return stockRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Stock not found with id: {}", id);
                    return new WarehouseManagementException(ErrorCode.STOCK_NOT_FOUND, "ID: " + id);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Stock> getStocksByProduct(Long productId) {
        logger.debug("Fetching stocks by product id: {}", productId);
        Product product = findProductOrThrow(productId);
        return stockRepository.findByProduct(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Stock> getStocksByWarehouse(Long warehouseId) {
        logger.debug("Fetching stocks by warehouse id: {}", warehouseId);
        Warehouse warehouse = findWarehouseOrThrow(warehouseId);
        return stockRepository.findByWarehouse(warehouse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Stock> getStockByProductAndWarehouse(Long productId, Long warehouseId) {
        logger.debug("Fetching stock by product id: {} and warehouse id: {}", productId, warehouseId);
        Product product = findProductOrThrow(productId);
        Warehouse warehouse = findWarehouseOrThrow(warehouseId);
        return stockRepository.findByProductAndWarehouse(product, warehouse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Stock> getLowStockItems() {
        logger.debug("Fetching low stock items");
        return stockRepository.findLowStockItems();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Stock> getOutOfStockItems() {
        logger.debug("Fetching out of stock items");
        return stockRepository.findOutOfStockItems();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Stock> getLowStockItemsByWarehouse(Long warehouseId) {
        logger.debug("Fetching low stock items for warehouse id: {}", warehouseId);
        Warehouse warehouse = findWarehouseOrThrow(warehouseId);
        return stockRepository.findLowStockItemsByWarehouse(warehouse);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getTotalQuantityByProduct(Long productId) {
        logger.debug("Calculating total quantity for product id: {}", productId);
        Product product = findProductOrThrow(productId);
        Long total = stockRepository.getTotalQuantityByProduct(product);
        return total != null ? total : 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public Long getTotalQuantityByWarehouse(Long warehouseId) {
        logger.debug("Calculating total quantity for warehouse id: {}", warehouseId);
        Warehouse warehouse = findWarehouseOrThrow(warehouseId);
        Long total = stockRepository.getTotalQuantityByWarehouse(warehouse);
        return total != null ? total : 0L;
    }

    @Override
    public Stock createStock(Stock stock) {
        logger.info("Creating new stock for product id: {} and warehouse id: {}", 
                stock.getProduct().getId(), stock.getWarehouse().getId());
        EntityValidator.validateStockForCreation(stock);

        Product product = findProductOrThrow(stock.getProduct().getId());
        Warehouse warehouse = findWarehouseOrThrow(stock.getWarehouse().getId());

        validateStockUniqueness(product, warehouse);

        stock.setProduct(product);
        stock.setWarehouse(warehouse);

        Stock saved = stockRepository.save(stock);
        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.STOCK_CREATE, "Stock", saved.getId(), username,
                String.format("Stock created: Warehouse=%s, Product=%s, Quantity=%d", 
                        warehouse.getName(), product.getName(), saved.getQuantity()));
        notificationService.create("Stock created",
                String.format("User %s created stock for %s/%s with quantity %d.", username,
                        warehouse.getName(), product.getName(), saved.getQuantity()),
                "Stock", saved.getId());
        logger.info("Stock created successfully with id: {}", saved.getId());
        return saved;
    }

    @Override
    public Stock updateStock(Long id, Stock stockDetails) {
        logger.info("Updating stock with id: {}", id);
        Stock stock = getStockByIdOrThrow(id);

        updateStockQuantity(stock, stockDetails.getQuantity());
        updateMinStockLevel(stock, stockDetails.getMinStockLevel());
        updateReservedQuantity(stock, stockDetails.getReservedQuantity());
        updateConsignedQuantity(stock, stockDetails.getConsignedQuantity());

        StockQuantityValidator.validateAvailableQuantity(stock);

        Stock saved = stockRepository.save(stock);
        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.STOCK_UPDATE, "Stock", saved.getId(), username,
                String.format("Stock updated: Warehouse=%s, Product=%s",
                        saved.getWarehouse().getName(), saved.getProduct().getName()));
        notificationService.create("Stock updated",
                String.format("User %s updated stock for %s/%s.", username,
                        saved.getWarehouse().getName(), saved.getProduct().getName()),
                "Stock", saved.getId());
        logger.info("Stock updated successfully with id: {}", saved.getId());
        return saved;
    }

    @Override
    public Stock addToStock(Long stockId, Integer quantity) {
        logger.info("Adding {} units to stock id: {}", quantity, stockId);
        ValidationUtil.requirePositive(quantity, "Quantity to add");
        Stock stock = getStockByIdOrThrow(stockId);
        stock.setQuantity(stock.getQuantity() + quantity);
        Stock saved = stockRepository.save(stock);
        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.STOCK_ADD, "Stock", saved.getId(), username,
                String.format("Stock increased: +%d units → New=%d | Warehouse=%s, Product=%s", 
                        quantity, saved.getQuantity(),
                        saved.getWarehouse().getName(), saved.getProduct().getName()));
        notificationService.create("Stock increased",
                String.format("User %s added %d units to stock %s/%s (New total: %d).", username,
                        saved.getWarehouse().getName(), saved.getProduct().getName(), quantity, saved.getQuantity()),
                "Stock", saved.getId());
        logger.info("Stock increased successfully. Stock id: {}, New quantity: {}", saved.getId(), saved.getQuantity());
        return saved;
    }

    @Override
    public Stock removeFromStock(Long stockId, Integer quantity) {
        logger.info("Removing {} units from stock id: {}", quantity, stockId);
        ValidationUtil.requirePositive(quantity, "Quantity to remove");
        Stock stock = getStockByIdOrThrow(stockId);
        int available = stock.getAvailableQuantity();
        if (available < quantity) {
            logger.warn("Insufficient stock. Available: {}, Requested: {}", available, quantity);
            throw new WarehouseManagementException(ErrorCode.INSUFFICIENT_STOCK);
        }
        stock.setQuantity(stock.getQuantity() - quantity);
        Stock saved = stockRepository.save(stock);
        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.STOCK_REMOVE, "Stock", saved.getId(), username,
                String.format("Stock decreased: -%d units → New=%d | Warehouse=%s, Product=%s", 
                        quantity, saved.getQuantity(),
                        saved.getWarehouse().getName(), saved.getProduct().getName()));
        notificationService.create("Stock decreased",
                String.format("User %s removed %d units from stock %s/%s (New total: %d).", username,
                        saved.getWarehouse().getName(), saved.getProduct().getName(), quantity, saved.getQuantity()),
                "Stock", saved.getId());
        logger.info("Stock decreased successfully. Stock id: {}, New quantity: {}", saved.getId(), saved.getQuantity());
        return saved;
    }

    @Override
    public void deleteStock(Long id) {
        logger.info("Deleting stock with id: {}", id);
        Stock stock = getStockByIdOrThrow(id);
        String warehouseName = stock.getWarehouse().getName();
        String productName = stock.getProduct().getName();
        stockRepository.delete(stock);
        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.STOCK_DELETE, "Stock", id, username,
                String.format("Stock deleted: Warehouse=%s, Product=%s",
                        warehouseName, productName));
        notificationService.create("Stock deleted",
                String.format("User %s deleted stock for %s/%s.", username, warehouseName, productName),
                "Stock", id);
        logger.info("Stock deleted successfully with id: {}", id);
    }

    @Override
    public Stock reserveStock(Long stockId, Integer quantity) {
        logger.info("Reserving {} units from stock id: {}", quantity, stockId);
        ValidationUtil.requirePositive(quantity, "Quantity to reserve");
        Stock stock = getStockByIdOrThrow(stockId);

        StockQuantityValidator.validateSufficientStock(stock, quantity);

        stock.setReservedQuantity(stock.getReservedQuantity() + quantity);
        Stock saved = stockRepository.save(stock);
        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.STOCK_RESERVE, "Stock", saved.getId(), username,
                String.format("Stock reserved: %d units reserved (Total Reserved=%d) | Warehouse=%s, Product=%s", 
                        quantity, saved.getReservedQuantity(), 
                        saved.getWarehouse().getName(), saved.getProduct().getName()));
        notificationService.create("Stock reserved",
                String.format("User %s reserved %d units from stock %s/%s.", username,
                        saved.getWarehouse().getName(), saved.getProduct().getName(), quantity),
                "Stock", saved.getId());
        logger.info("Stock reserved successfully. Stock id: {}, Reserved quantity: {}", saved.getId(), saved.getReservedQuantity());
        return saved;
    }

    @Override
    public Stock releaseStock(Long stockId, Integer quantity) {
        logger.info("Releasing {} units from reserved stock id: {}", quantity, stockId);
        ValidationUtil.requirePositive(quantity, "Quantity to release");
        Stock stock = getStockByIdOrThrow(stockId);

        StockQuantityValidator.validateSufficientReservedStock(stock, quantity);

        stock.setReservedQuantity(stock.getReservedQuantity() - quantity);
        Stock saved = stockRepository.save(stock);
        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.STOCK_RELEASE, "Stock", saved.getId(), username,
                String.format("Reservation released: %d units released (Remaining Reserved=%d) | Warehouse=%s, Product=%s", 
                        quantity, saved.getReservedQuantity(),
                        saved.getWarehouse().getName(), saved.getProduct().getName()));
        notificationService.create("Reservation released",
                String.format("User %s released %d units from reserved stock %s/%s.", username,
                        saved.getWarehouse().getName(), saved.getProduct().getName(), quantity),
                "Stock", saved.getId());
        logger.info("Reservation released successfully. Stock id: {}, Remaining reserved: {}", saved.getId(), saved.getReservedQuantity());
        return saved;
    }

    private Product findProductOrThrow(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> {
                    logger.warn("Product not found with id: {}", productId);
                    return new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND);
                });
    }

    private Warehouse findWarehouseOrThrow(Long warehouseId) {
        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> {
                    logger.warn("Warehouse not found with id: {}", warehouseId);
                    return new WarehouseManagementException(ErrorCode.WAREHOUSE_NOT_FOUND);
                });
    }

    private void validateStockUniqueness(Product product, Warehouse warehouse) {
        Optional<Stock> existingStock = stockRepository.findByProductAndWarehouse(product, warehouse);
        if (existingStock.isPresent()) {
            logger.warn("Stock already exists for product id: {} and warehouse id: {}", product.getId(), warehouse.getId());
            String message = String.format(
                "Stock record already exists. Product %s already has a stock record in warehouse %s. Please edit the existing record from Stock Management screen.",
                product.getName(), warehouse.getName()
            );
            throw new WarehouseManagementException(ErrorCode.STOCK_ALREADY_EXISTS, message);
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

