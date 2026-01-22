package com.warehouse.service.impl;

import com.warehouse.constants.NotificationMessages;
import com.warehouse.dto.AuditMetadata;
import com.warehouse.dto.BulkDeleteResponse;
import com.warehouse.dto.NotificationRequest;
import com.warehouse.dto.StockFilter;
import com.warehouse.entity.Product;
import com.warehouse.entity.Stock;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.WarehouseType;
import com.warehouse.enums.AuditAction;
import com.warehouse.enums.DomainEntityType;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.WarehouseRepository;
import com.warehouse.service.AuditService;
import com.warehouse.service.NotificationService;
import com.warehouse.service.StockService;
import com.warehouse.util.CurrentUser;
import com.warehouse.util.EntityValidator;
import com.warehouse.util.StockQuantityValidator;
import com.warehouse.util.ValidationUtil;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of StockService for managing stock operations.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private static final Logger logger = LoggerFactory.getLogger(StockServiceImpl.class);

    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public List<Stock> getAllStocks() {
        logger.debug("Fetching all stocks");
        return stockRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Stock> getAllStocks(Pageable pageable) {
        logger.debug("Fetching paged stocks - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return stockRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Stock> getStocks(StockFilter filter, Pageable pageable) {
        StockFilter appliedFilter = filter != null ? filter : new StockFilter();
        StockFilter.Status status = appliedFilter.getStatus() != null ? appliedFilter.getStatus()
                : StockFilter.Status.ALL;
        String statusValue = status.name();
        String search = appliedFilter.getSearch();
        boolean searchEnabled = search != null && !search.isBlank();
        // Use Turkish locale for proper case-insensitive search with Turkish characters
        String searchPattern = searchEnabled ? "%" + search.toLowerCase(Locale.forLanguageTag("tr-TR")) + "%" : "%";

        logger.debug("Fetching stocks with advanced filters - page: {}, size: {}", pageable.getPageNumber(),
                pageable.getPageSize());
        return stockRepository.findByFilters(
                appliedFilter.getBrandId(),
                appliedFilter.getColorId(),
                appliedFilter.getWarehouseId(),
                appliedFilter.getCategoryId(),
                appliedFilter.getSubCategoryId(),
                searchEnabled,
                searchPattern,
                appliedFilter.isReservedOnly(),
                appliedFilter.isConsignedOnly(),
                statusValue,
                pageable);
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
    public List<Stock> getStocksByWarehouseAndProductIds(Long warehouseId, List<Long> productIds) {
        logger.debug("Fetching stocks by warehouse id: {} and product ids: {}", warehouseId, productIds);
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        Warehouse warehouse = findWarehouseOrThrow(warehouseId);
        return stockRepository.findByWarehouseAndProductIds(warehouse, productIds);
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

    /**
     * Returns low stock count using an efficient COUNT query on the repository.
     * Cached briefly to reduce database pressure during bursts.
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "counts", key = "'lowStock'", unless = "#result == null")
    public long countLowStockItems() {
        return stockRepository.countLowStockItems();
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
    @Transactional(readOnly = true)
    public Long getTotalQuantityByFilter(StockFilter filter) {
        StockFilter appliedFilter = filter != null ? filter : new StockFilter();
        StockFilter.Status status = appliedFilter.getStatus() != null ? appliedFilter.getStatus()
                : StockFilter.Status.ALL;
        String statusValue = status.name();
        String search = appliedFilter.getSearch();
        boolean searchEnabled = search != null && !search.isBlank();
        String searchPattern = searchEnabled ? "%" + search.toLowerCase(Locale.forLanguageTag("tr-TR")) + "%" : "%";

        Long total = stockRepository.sumQuantityByFilters(
                appliedFilter.getBrandId(),
                appliedFilter.getColorId(),
                appliedFilter.getWarehouseId(),
                appliedFilter.getCategoryId(),
                appliedFilter.getSubCategoryId(),
                searchEnabled,
                searchPattern,
                appliedFilter.isReservedOnly(),
                appliedFilter.isConsignedOnly(),
                statusValue);
        return total != null ? total : 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> getTotalQuantitiesByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }

        Set<Long> distinctIds = productIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (distinctIds.isEmpty()) {
            return Map.of();
        }

        List<StockRepository.ProductQuantityAggregate> aggregates = stockRepository
                .getTotalQuantitiesByProductIds(distinctIds);

        Map<Long, Long> result = new HashMap<>();
        distinctIds.forEach(id -> result.put(id, 0L));

        for (StockRepository.ProductQuantityAggregate aggregate : aggregates) {
            Long productId = aggregate.getProductId();
            Long totalQuantity = aggregate.getTotalQuantity();
            if (productId != null) {
                result.put(productId, totalQuantity != null ? totalQuantity : 0L);
            }
        }

        return result;
    }

    @Override
    public Stock createStock(Stock stock) {
        logger.info("Creating new stock for product id: {} and warehouse id: {}",
                stock.getProduct().getId(), stock.getWarehouse().getId());
        EntityValidator.validateStockForCreation(stock);

        Product product = findProductOrThrow(stock.getProduct().getId());
        Warehouse warehouse = findWarehouseOrThrow(stock.getWarehouse().getId());

        validateStockUniqueness(product, warehouse, stock.getCustomerName());

        stock.setProduct(product);
        stock.setWarehouse(warehouse);
        stock.setAdditionNote(normalizeAdditionNote(stock.getAdditionNote()));

        // For STANDART warehouses, customerName and customerPhone should be null
        if (warehouse.getWarehouseType() == WarehouseType.STANDART) {
            stock.setCustomerName(null);
            stock.setCustomerPhone(null);
        } else if (warehouse.getWarehouseType() == WarehouseType.EMANET_DEPO) {
            // For EMANET_DEPO, customerPhone is required
            if (stock.getCustomerName() == null || stock.getCustomerName().trim().isEmpty()) {
                throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING,
                        "Emanet depo için müşteri adı gereklidir.");
            }
            if (stock.getCustomerPhone() == null || stock.getCustomerPhone().trim().isEmpty()) {
                throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING,
                        "Emanet depo için müşteri telefon numarası gereklidir.");
            }
        }

        Stock saved = stockRepository.save(stock);
        String username = CurrentUser.usernameOrSystem();
        String customerInfo = saved.getCustomerName() != null ? " (Müşteri: " + saved.getCustomerName() + ")" : "";
        AuditMetadata metadata = buildStockMetadata(saved, saved.getQuantity());
        auditService.log(AuditAction.STOCK_CREATE, DomainEntityType.Stock.name(), saved.getId(), username,
                String.format("Stok oluşturuldu: Depo=%s, Ürün=%s, Miktar=%s%s",
                        warehouse.getName(), product.getName(), String.valueOf(saved.getQuantity()), customerInfo),
                metadata);
        notificationService.create(buildNotificationRequest(
                NotificationMessages.STOCK_CREATED_TITLE,
                String.format("Kullanıcı %s, %s/%s için %d adet stok oluşturdu.%s", username,
                        warehouse.getName(), product.getName(), saved.getQuantity(), customerInfo),
                saved,
                saved.getQuantity()));
        logger.info("Stock created successfully with id: {}", saved.getId());
        return saved;
    }

    @Override
    public List<Stock> createStocks(List<Stock> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING,
                    "At least one stock entry is required");
        }
        List<Stock> created = new ArrayList<>();
        for (Stock stock : stocks) {
            created.add(createStock(stock));
        }
        return created;
    }

    @Override
    public Stock updateStock(Long id, Stock stockDetails) {
        logger.info("Updating stock with id: {}", id);
        Stock stock = getStockByIdOrThrow(id);
        Warehouse warehouse = stock.getWarehouse();

        // CRITICAL: Quantity updates are NOT allowed through this endpoint
        // Quantity can ONLY be changed via add/remove endpoints (/add, /remove)
        // This prevents accidental zeroing or incorrect quantity updates
        // If quantity is provided in the request, it will be ignored for security

        // Store old values for audit log
        String oldProductName = stock.getProduct() != null ? stock.getProduct().getName() : null;
        Integer oldMinStockLevel = stock.getMinStockLevel();
        Integer oldReservedQuantity = stock.getReservedQuantity();
        Integer oldConsignedQuantity = stock.getConsignedQuantity();
        String oldAdditionNote = stock.getAdditionNote();
        String oldCustomerName = stock.getCustomerName();
        String oldCustomerPhone = stock.getCustomerPhone();
        
        // Track changes
        List<String> changes = new ArrayList<>();

        // Update product if provided
        if (stockDetails.getProduct() != null && stockDetails.getProduct().getId() != null) {
            Long newProductId = stockDetails.getProduct().getId();
            Long currentProductId = stock.getProduct().getId();

            // Only update if product is actually changing
            if (!newProductId.equals(currentProductId)) {
                Product newProduct = findProductOrThrow(newProductId);

                // Validate uniqueness with new product
                // For EMANET_DEPO, check with customerName
                // For STANDART, check without customerName
                String customerNameForValidation = warehouse.getWarehouseType() == WarehouseType.EMANET_DEPO
                        ? stock.getCustomerName()
                        : null;

                // Check if another stock exists with the new product, same warehouse, and same
                // customer (if EMANET_DEPO)
                if (warehouse.getWarehouseType() == WarehouseType.EMANET_DEPO) {
                    if (customerNameForValidation == null || customerNameForValidation.trim().isEmpty()) {
                        throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING,
                                "Emanet depo için müşteri adı gereklidir.");
                    }
                    Optional<Stock> existingStock = stockRepository.findByProductAndWarehouseAndCustomerName(
                            newProduct, warehouse, customerNameForValidation.trim());
                    // Allow if it's the same stock (updating itself)
                    if (existingStock.isPresent() && !existingStock.get().getId().equals(stock.getId())) {
                        throw new WarehouseManagementException(ErrorCode.STOCK_ALREADY_EXISTS,
                                String.format("Bu ürün için %s müşterisi adına zaten bir stok kaydı mevcut.",
                                        customerNameForValidation));
                    }
                } else {
                    Optional<Stock> existingStock = stockRepository.findByProductAndWarehouse(newProduct, warehouse);
                    // Allow if it's the same stock (updating itself)
                    if (existingStock.isPresent() && !existingStock.get().getId().equals(stock.getId())) {
                        throw new WarehouseManagementException(ErrorCode.STOCK_ALREADY_EXISTS,
                                "Bu ürün için bu depoda zaten bir stok kaydı mevcut.");
                    }
                }

                stock.setProduct(newProduct);
                String newProductName = newProduct.getName();
                changes.add(String.format("Ürün: %s → %s", oldProductName, newProductName));
                logger.info("Product changed from {} to {} for stock id: {}", currentProductId, newProductId, id);
            }
        }

        // Update min stock level
        if (stockDetails.getMinStockLevel() != null) {
            Integer newMinStockLevel = stockDetails.getMinStockLevel();
            if (!Objects.equals(oldMinStockLevel, newMinStockLevel)) {
                updateMinStockLevel(stock, newMinStockLevel);
                String oldValue = oldMinStockLevel != null ? String.valueOf(oldMinStockLevel) : "(boş)";
                changes.add(String.format("Min Stok: %s → %s", oldValue, newMinStockLevel));
            }
        }

        // Update reserved quantity
        if (stockDetails.getReservedQuantity() != null) {
            Integer newReservedQuantity = stockDetails.getReservedQuantity();
            if (!Objects.equals(oldReservedQuantity, newReservedQuantity)) {
                updateReservedQuantity(stock, newReservedQuantity);
                String oldValue = oldReservedQuantity != null ? String.valueOf(oldReservedQuantity) : "(boş)";
                changes.add(String.format("Rezerve Miktar: %s → %s", oldValue, newReservedQuantity));
            }
        }

        // Update consigned quantity
        if (stockDetails.getConsignedQuantity() != null) {
            Integer newConsignedQuantity = stockDetails.getConsignedQuantity();
            if (!Objects.equals(oldConsignedQuantity, newConsignedQuantity)) {
                updateConsignedQuantity(stock, newConsignedQuantity);
                String oldValue = oldConsignedQuantity != null ? String.valueOf(oldConsignedQuantity) : "(boş)";
                changes.add(String.format("Konsinye Miktar: %s → %s", oldValue, newConsignedQuantity));
            }
        }

        // Update addition note
        if (stockDetails.getAdditionNote() != null) {
            String newAdditionNote = normalizeAdditionNote(stockDetails.getAdditionNote());
            if (!Objects.equals(oldAdditionNote, newAdditionNote)) {
                updateAdditionNote(stock, stockDetails.getAdditionNote());
                String oldValue = oldAdditionNote != null && !oldAdditionNote.isEmpty() ? oldAdditionNote : "(boş)";
                String newValue = newAdditionNote != null && !newAdditionNote.isEmpty() ? newAdditionNote : "(boş)";
                changes.add(String.format("Not: %s → %s", oldValue, newValue));
            }
        }

        // Update customer info for EMANET_DEPO warehouses
        if (warehouse.getWarehouseType() == WarehouseType.EMANET_DEPO) {
            // Update customer name if provided
            if (stockDetails.getCustomerName() != null) {
                String customerName = stockDetails.getCustomerName().trim();
                if (customerName.isEmpty()) {
                    throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING,
                            "Emanet depo için müşteri adı gereklidir.");
                }
                if (!Objects.equals(oldCustomerName, customerName)) {
                    stock.setCustomerName(customerName);
                    String oldValue = oldCustomerName != null && !oldCustomerName.isEmpty() ? oldCustomerName : "(boş)";
                    changes.add(String.format("Müşteri Adı: %s → %s", oldValue, customerName));
                }
            }
            // Update customer phone if provided
            if (stockDetails.getCustomerPhone() != null) {
                String customerPhone = stockDetails.getCustomerPhone().trim();
                if (customerPhone.isEmpty()) {
                    throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING,
                            "Emanet depo için müşteri telefon numarası gereklidir.");
                }
                if (!Objects.equals(oldCustomerPhone, customerPhone)) {
                    stock.setCustomerPhone(customerPhone);
                    String oldValue = oldCustomerPhone != null && !oldCustomerPhone.isEmpty() ? oldCustomerPhone : "(boş)";
                    changes.add(String.format("Müşteri Telefonu: %s → %s", oldValue, customerPhone));
                }
            }
        } else {
            // For STANDART warehouses, customer info should always be null
            stock.setCustomerName(null);
            stock.setCustomerPhone(null);
        }

        StockQuantityValidator.validateAvailableQuantity(stock);

        Stock saved = stockRepository.save(stock);
        String username = CurrentUser.usernameOrSystem();
        AuditMetadata metadata = buildStockMetadata(saved, saved.getQuantity());
        
        // Build detailed audit message
        String auditMessage;
        if (changes.isEmpty()) {
            // No changes detected (shouldn't happen, but handle gracefully)
            auditMessage = String.format("Stok güncellendi: Depo=%s, Ürün=%s (Değişiklik yok)",
                    saved.getWarehouse().getName(), saved.getProduct().getName());
        } else {
            // Build message with all changes
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append(String.format("Stok güncellendi: Depo=%s, Ürün=%s | Değişiklikler: ",
                    saved.getWarehouse().getName(), saved.getProduct().getName()));
            messageBuilder.append(String.join(", ", changes));
            auditMessage = messageBuilder.toString();
        }
        
        auditService.log(AuditAction.STOCK_UPDATE, DomainEntityType.Stock.name(), saved.getId(), username,
                auditMessage, metadata);
        notificationService.create(buildNotificationRequest(
                NotificationMessages.STOCK_UPDATED_TITLE,
                String.format("Kullanıcı %s, %s/%s stok kaydını güncelledi.", username,
                        saved.getWarehouse().getName(), saved.getProduct().getName()),
                saved,
                saved.getQuantity()));
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
        AuditMetadata metadata = buildStockMetadata(saved, quantity);
        auditService.log(AuditAction.STOCK_ADD, DomainEntityType.Stock.name(), saved.getId(), username,
                String.format("Stok artırıldı: +%s adet → Yeni=%s | Depo=%s, Ürün=%s",
                        String.valueOf(quantity), String.valueOf(saved.getQuantity()),
                        saved.getWarehouse().getName(), saved.getProduct().getName()),
                metadata);
        notificationService.create(buildNotificationRequest(
                NotificationMessages.STOCK_INCREASED_TITLE,
                String.format("Kullanıcı %s, %s/%s stokuna %s adet ekledi (Yeni toplam: %s).", username,
                        saved.getWarehouse().getName(), saved.getProduct().getName(), String.valueOf(quantity),
                        String.valueOf(saved.getQuantity())),
                saved,
                quantity));
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
        AuditMetadata metadata = buildStockMetadata(saved, -quantity);
        auditService.log(AuditAction.STOCK_REMOVE, DomainEntityType.Stock.name(), saved.getId(), username,
                String.format("Stok azaltıldı: -%s adet → Yeni=%s | Depo=%s, Ürün=%s",
                        String.valueOf(quantity), String.valueOf(saved.getQuantity()),
                        saved.getWarehouse().getName(), saved.getProduct().getName()),
                metadata);
        notificationService.create(buildNotificationRequest(
                NotificationMessages.STOCK_DECREASED_TITLE,
                String.format("Kullanıcı %s, %s/%s stokundan %s adet çıkardı (Yeni toplam: %s).", username,
                        saved.getWarehouse().getName(), saved.getProduct().getName(), String.valueOf(quantity),
                        String.valueOf(saved.getQuantity())),
                saved,
                -quantity));
        logger.info("Stock decreased successfully. Stock id: {}, New quantity: {}", saved.getId(), saved.getQuantity());
        return saved;
    }

    @Override
    public void deleteStock(Long id) {
        logger.info("Deleting stock with id: {}", id);
        Stock stock = getStockByIdOrThrow(id);
        String warehouseName = stock.getWarehouse().getName();
        String productName = stock.getProduct().getName();
        String productSku = stock.getProduct().getSku();
        String username = CurrentUser.usernameOrSystem();
        AuditMetadata metadata = buildStockMetadata(stock, stock.getQuantity());
        stockRepository.delete(stock);
        auditService.log(AuditAction.STOCK_DELETE, DomainEntityType.Stock.name(), id, username,
                String.format("Stok silindi: Depo=%s, Ürün=%s (SKU=%s, Miktar=%s)",
                        warehouseName, productName, productSku, String.valueOf(stock.getQuantity())),
                metadata);
        notificationService.create(buildNotificationRequest(
                NotificationMessages.STOCK_DELETED_TITLE,
                String.format("Kullanıcı %s, %s/%s (SKU: %s) stok kaydını sildi. Son miktar: %s.", username,
                        warehouseName, productName, productSku, String.valueOf(stock.getQuantity())),
                stock,
                stock.getQuantity()));
        logger.info("Stock deleted successfully with id: {}", id);
    }

    @Override
    public BulkDeleteResponse deleteStocks(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            logger.debug("deleteStocks called with empty ids");
            return new BulkDeleteResponse(0, 0, List.of());
        }

        logger.info("Deleting {} stocks", ids.size());
        List<BulkDeleteResponse.DeleteError> errors = new java.util.ArrayList<>();
        int successCount = 0;
        String username = CurrentUser.usernameOrSystem();

        for (Long id : ids) {
            try {
                Stock stock = getStockByIdOrThrow(id);
                String warehouseName = stock.getWarehouse().getName();
                String productName = stock.getProduct().getName();
                String productSku = stock.getProduct().getSku();

                AuditMetadata metadata = buildStockMetadata(stock, stock.getQuantity());
                stockRepository.delete(stock);
                auditService.log(AuditAction.STOCK_DELETE, DomainEntityType.Stock.name(), id, username,
                        String.format("Stok silindi: Depo=%s, Ürün=%s (SKU=%s, Miktar=%s)", warehouseName, productName,
                                productSku, String.valueOf(stock.getQuantity())),
                        metadata);
                successCount++;
                logger.debug("Stock deleted successfully with id: {}", id);
            } catch (WarehouseManagementException e) {
                // Domain exception'ları yakala
                Stock stock = null;
                try {
                    stock = getStockByIdOrThrow(id);
                } catch (Exception ex) {
                    // Stok bulunamadı
                }
                String stockInfo = stock != null
                        ? String.format("%s / %s (SKU: %s)",
                                stock.getWarehouse() != null ? stock.getWarehouse().getName() : "Bilinmeyen",
                                stock.getProduct() != null ? stock.getProduct().getName() : "Bilinmeyen",
                                stock.getProduct() != null ? stock.getProduct().getSku() : "N/A")
                        : String.format("Stok #%d", id);

                errors.add(new BulkDeleteResponse.DeleteError(
                        id,
                        stockInfo,
                        stock != null && stock.getProduct() != null ? stock.getProduct().getSku() : null,
                        e.getErrorCode().getCode(),
                        e.getMessage()));
                logger.warn("Cannot delete stock with id {}: {}", id, e.getMessage());
            } catch (Exception e) {
                // Diğer hatalar
                Stock stock = null;
                try {
                    stock = getStockByIdOrThrow(id);
                } catch (Exception ex) {
                    // Stok bulunamadı
                }
                String stockInfo = stock != null
                        ? String.format("%s / %s (SKU: %s)",
                                stock.getWarehouse() != null ? stock.getWarehouse().getName() : "Bilinmeyen",
                                stock.getProduct() != null ? stock.getProduct().getName() : "Bilinmeyen",
                                stock.getProduct() != null ? stock.getProduct().getSku() : "N/A")
                        : String.format("Stok #%d", id);

                errors.add(new BulkDeleteResponse.DeleteError(
                        id,
                        stockInfo,
                        stock != null && stock.getProduct() != null ? stock.getProduct().getSku() : null,
                        ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        "Stok silinirken beklenmeyen bir hata oluştu: " + e.getMessage()));
                logger.error("Error deleting stock {}: {}", id, e.getMessage(), e);
            }
        }

        if (successCount > 0) {
            notificationService.create(NotificationMessages.STOCK_DELETED_TITLE,
                    String.format("Kullanıcı %s, %d adet stok kaydını sildi.", username, successCount),
                    DomainEntityType.Stock.name(), null);
        }

        logger.info("Batch delete completed: {} successful, {} errors", successCount, errors.size());
        return new BulkDeleteResponse(successCount, errors.size(), errors);
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
        AuditMetadata metadata = buildStockMetadata(saved, quantity);
        auditService.log(AuditAction.STOCK_RESERVE, DomainEntityType.Stock.name(), saved.getId(), username,
                String.format("Stok rezerve edildi: %s adet rezerve edildi (Toplam Rezerv=%s) | Depo=%s, Ürün=%s",
                        String.valueOf(quantity), String.valueOf(saved.getReservedQuantity()),
                        saved.getWarehouse().getName(), saved.getProduct().getName()),
                metadata);
        notificationService.create(buildNotificationRequest(
                NotificationMessages.STOCK_RESERVED_TITLE,
                String.format("Kullanıcı %s, %s/%s stoktan %s adet rezerve etti.", username,
                        saved.getWarehouse().getName(), saved.getProduct().getName(), String.valueOf(quantity)),
                saved,
                quantity));
        logger.info("Stock reserved successfully. Stock id: {}, Reserved quantity: {}", saved.getId(),
                saved.getReservedQuantity());
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
        AuditMetadata metadata = buildStockMetadata(saved, -quantity);
        auditService.log(AuditAction.STOCK_RELEASE, DomainEntityType.Stock.name(), saved.getId(), username,
                String.format("Rezervasyon bırakıldı: %s adet bırakıldı (Kalan Rezerv=%s) | Depo=%s, Ürün=%s",
                        String.valueOf(quantity), String.valueOf(saved.getReservedQuantity()),
                        saved.getWarehouse().getName(), saved.getProduct().getName()),
                metadata);
        notificationService.create(buildNotificationRequest(
                NotificationMessages.RESERVATION_RELEASED_TITLE,
                String.format("Kullanıcı %s, %s/%s stoktan rezerve %s adedi bıraktı.", username,
                        saved.getWarehouse().getName(), saved.getProduct().getName(), String.valueOf(quantity)),
                saved,
                -quantity));
        logger.info("Reservation released successfully. Stock id: {}, Remaining reserved: {}", saved.getId(),
                saved.getReservedQuantity());
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

    private void validateStockUniqueness(Product product, Warehouse warehouse, String customerName) {
        // For EMANET_DEPO warehouses, check uniqueness with customerName
        if (warehouse.getWarehouseType() == WarehouseType.EMANET_DEPO) {
            if (customerName == null || customerName.trim().isEmpty()) {
                throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING,
                        "Emanet depo için müşteri adı gereklidir.");
            }
            Optional<Stock> existingStock = stockRepository.findByProductAndWarehouseAndCustomerName(
                    product, warehouse, customerName.trim());
            if (existingStock.isPresent()) {
                logger.warn("Stock already exists for product id: {}, warehouse id: {}, customer: {}",
                        product.getId(), warehouse.getId(), customerName);
                String message = String.format(
                        "Bu ürün için %s müşterisi adına zaten bir stok kaydı mevcut. Lütfen mevcut kaydı düzenleyin.",
                        customerName);
                throw new WarehouseManagementException(ErrorCode.STOCK_ALREADY_EXISTS, message);
            }
        } else {
            // For STANDART warehouses, check uniqueness without customerName
            Optional<Stock> existingStock = stockRepository.findByProductAndWarehouse(product, warehouse);
            if (existingStock.isPresent()) {
                logger.warn("Stock already exists for product id: {} and warehouse id: {}",
                        product.getId(), warehouse.getId());
                String message = String.format(
                        "Stock record already exists. Product %s already has a stock record in warehouse %s. Please edit the existing record from Stock Management screen.",
                        product.getName(), warehouse.getName());
                throw new WarehouseManagementException(ErrorCode.STOCK_ALREADY_EXISTS, message);
            }
        }
    }

    // In-memory matchers removed; filtering is handled by the repository query now

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

    private void updateAdditionNote(Stock stock, String additionNote) {
        if (additionNote != null) {
            stock.setAdditionNote(normalizeAdditionNote(additionNote));
        }
    }

    private String normalizeAdditionNote(String additionNote) {
        if (additionNote == null) {
            return null;
        }
        String trimmed = additionNote.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AuditMetadata buildStockMetadata(Stock stock, Integer quantityContext) {
        if (stock == null) {
            return null;
        }
        Product product = stock.getProduct();
        Warehouse warehouse = stock.getWarehouse();
        return AuditMetadata.builder()
                .warehouseId(warehouse != null ? warehouse.getId() : null)
                .warehouseName(warehouse != null ? warehouse.getName() : null)
                .productId(product != null ? product.getId() : null)
                .productName(product != null ? product.getName() : null)
                .productSku(product != null ? product.getSku() : null)
                .quantity(quantityContext)
                .build();
    }

    private NotificationRequest buildNotificationRequest(String title, String message, Stock stock,
            Integer quantityContext) {
        Product product = stock != null ? stock.getProduct() : null;
        Warehouse warehouse = stock != null ? stock.getWarehouse() : null;
        return NotificationRequest.builder()
                .title(title)
                .message(message)
                .entityType(DomainEntityType.Stock.name())
                .entityId(stock != null ? stock.getId() : null)
                .actor(CurrentUser.usernameOrSystem())
                .warehouseId(warehouse != null ? warehouse.getId() : null)
                .warehouseName(warehouse != null ? warehouse.getName() : null)
                .productId(product != null ? product.getId() : null)
                .productName(product != null ? product.getName() : null)
                .productSku(product != null ? product.getSku() : null)
                .quantity(quantityContext)
                .build();
    }

}
