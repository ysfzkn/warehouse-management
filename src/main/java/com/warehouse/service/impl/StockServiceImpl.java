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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /** Enough to recognise the delivery being typed without turning the dropdown into a list. */
    private static final int IRSALIYE_SUGGESTION_LIMIT = 10;

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
        // Consignment customers are matched on the normalised column as well, so "Ballı" finds a
        // record typed "Balli" — lower-casing alone leaves those two different strings.
        String customerSearchPattern = searchEnabled
                ? "%" + com.warehouse.util.TurkishText.normalize(search) + "%" : "%";
        // The free-text box should find a waybill too, and on the same punctuation-blind terms as
        // the dedicated filter: typing "abc 2026-14" has to reach a row stored as "ABC202614".
        String irsaliyeSearchPattern = searchEnabled ? irsaliyeLikePattern(search) : "%";
        String irsaliyeKeyPattern = irsaliyeLikePattern(appliedFilter.getIrsaliyeNo());

        logger.debug("Fetching stocks with advanced filters - page: {}, size: {}", pageable.getPageNumber(),
                pageable.getPageSize());
        // Use safe bounds when null (PostgreSQL rejects LocalDateTime.MIN/MAX as "timestamp out of range")
        LocalDateTime from = appliedFilter.getLastUpdatedFrom() != null ? appliedFilter.getLastUpdatedFrom() : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime to = appliedFilter.getLastUpdatedTo() != null ? appliedFilter.getLastUpdatedTo() : LocalDateTime.of(2099, 12, 31, 23, 59, 59);

        List<Long> warehouseIds = appliedFilter.getWarehouseIds();
        boolean hasWarehouseFilter = warehouseIds != null && !warehouseIds.isEmpty();
        // If multi-warehouse filter is provided, ignore single warehouseId to avoid conflicting filters.
        Long singleWarehouseId = hasWarehouseFilter ? null : appliedFilter.getWarehouseId();

        return stockRepository.findByFilters(
                appliedFilter.getBrandId(),
                appliedFilter.getColorId(),
                singleWarehouseId,
                hasWarehouseFilter ? warehouseIds : List.of(0L),
                hasWarehouseFilter,
                appliedFilter.getCategoryId(),
                appliedFilter.getSubCategoryId(),
                searchEnabled,
                searchPattern,
                customerSearchPattern,
                irsaliyeSearchPattern,
                irsaliyeKeyPattern,
                appliedFilter.getIrsaliyeDateFrom(),
                appliedFilter.getIrsaliyeDateTo(),
                appliedFilter.isReservedOnly(),
                appliedFilter.isConsignedOnly(),
                appliedFilter.isHideOutOfStock(),
                statusValue,
                from,
                to,
                pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.warehouse.dto.IrsaliyeSummaryDto> summarizeIrsaliye(String query) {
        return stockRepository
                .summarizeIrsaliye(irsaliyeLikePattern(query), PageRequest.of(0, IRSALIYE_SUGGESTION_LIMIT))
                .stream()
                .map(com.warehouse.dto.IrsaliyeSummaryDto::of)
                .toList();
    }

    /**
     * LIKE pattern for the waybill key column. Null when there is nothing to match on, which the
     * queries read as "no filter"; otherwise the query is folded exactly like the stored key, so
     * the punctuation the operator did or did not type stops mattering.
     */
    private static String irsaliyeLikePattern(String raw) {
        String key = Stock.toIrsaliyeKey(raw);
        return key == null ? null : "%" + key + "%";
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
        String customerSearchPattern = searchEnabled
                ? "%" + com.warehouse.util.TurkishText.normalize(search) + "%" : "%";

        LocalDateTime from = appliedFilter.getLastUpdatedFrom() != null ? appliedFilter.getLastUpdatedFrom() : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime to = appliedFilter.getLastUpdatedTo() != null ? appliedFilter.getLastUpdatedTo() : LocalDateTime.of(2099, 12, 31, 23, 59, 59);

        List<Long> warehouseIds = appliedFilter.getWarehouseIds();
        boolean hasWarehouseFilter = warehouseIds != null && !warehouseIds.isEmpty();
        Long singleWarehouseId = hasWarehouseFilter ? null : appliedFilter.getWarehouseId();

        Long total = stockRepository.sumQuantityByFilters(
                appliedFilter.getBrandId(),
                appliedFilter.getColorId(),
                singleWarehouseId,
                hasWarehouseFilter ? warehouseIds : List.of(0L),
                hasWarehouseFilter,
                appliedFilter.getCategoryId(),
                appliedFilter.getSubCategoryId(),
                searchEnabled,
                searchPattern,
                customerSearchPattern,
                searchEnabled ? irsaliyeLikePattern(search) : "%",
                irsaliyeLikePattern(appliedFilter.getIrsaliyeNo()),
                appliedFilter.getIrsaliyeDateFrom(),
                appliedFilter.getIrsaliyeDateTo(),
                appliedFilter.isReservedOnly(),
                appliedFilter.isConsignedOnly(),
                appliedFilter.isHideOutOfStock(),
                statusValue,
                from,
                to);
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
        logger.info("Creating or merging stock for product id: {} and warehouse id: {}",
                stock.getProduct().getId(), stock.getWarehouse().getId());
        EntityValidator.validateStockForCreation(stock);

        Product product = findProductOrThrow(stock.getProduct().getId());
        Warehouse warehouse = findWarehouseOrThrow(stock.getWarehouse().getId());

        // EMANET_DEPO: customerName must be present BEFORE the existence lookup —
        // otherwise a blank name would fall through to the STANDART branch and
        // wrongly merge into another customer's row.
        if (warehouse.getWarehouseType() == WarehouseType.EMANET_DEPO) {
            if (stock.getCustomerName() == null || stock.getCustomerName().trim().isEmpty()) {
                throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING,
                        "Emanet depo için müşteri adı gereklidir.");
            }
        }

        Optional<Stock> existing;
        if (warehouse.getWarehouseType() == WarehouseType.EMANET_DEPO) {
            existing = stockRepository.findByProductAndWarehouseAndCustomerName(
                    product, warehouse, stock.getCustomerName().trim());
        } else {
            existing = stockRepository.findByProductAndWarehouse(product, warehouse);
        }

        if (existing.isPresent()) {
            Integer addQty = stock.getQuantity();
            if (addQty == null || addQty <= 0) {
                throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING,
                        "Eklenecek miktar pozitif bir değer olmalıdır.");
            }
            String note = normalizeAdditionNote(stock.getAdditionNote());
            logger.info("Existing stock found (id={}); merging quantity {} via addToStock",
                    existing.get().getId(), addQty);
            return addToStock(existing.get().getId(), addQty, note,
                    stock.getIrsaliyeNo(), stock.getIrsaliyeDate());
        }

        stock.setProduct(product);
        stock.setWarehouse(warehouse);
        stock.setAdditionNote(normalizeAdditionNote(stock.getAdditionNote()));
        stock.setIrsaliyeNo(trimToNull(stock.getIrsaliyeNo()));
        // A date with no number is unreachable by any waybill lookup, so it is not worth storing.
        // The form blocks this; the API is a second entry point that has to agree.
        if (stock.getIrsaliyeNo() == null) {
            stock.setIrsaliyeDate(null);
        }

        // For STANDART warehouses, customerName and customerPhone should be null
        if (warehouse.getWarehouseType() == WarehouseType.STANDART) {
            stock.setCustomerName(null);
            stock.setCustomerPhone(null);
        } else if (warehouse.getWarehouseType() == WarehouseType.EMANET_DEPO) {
            if (stock.getCustomerPhone() == null || stock.getCustomerPhone().trim().isEmpty()) {
                throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING,
                        "Emanet depo için müşteri telefon numarası gereklidir.");
            }
            // Title-cased on save so "ayşe yılmaz" and "AYŞE YILMAZ" do not read as two people.
            stock.setCustomerName(com.warehouse.util.TurkishText.toTitleCase(stock.getCustomerName()));
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

        // CRITICAL: Quantity updates are NOT allowed through this endpoint
        // Quantity can ONLY be changed via add/remove endpoints (/add, /remove)
        // This prevents accidental zeroing or incorrect quantity updates
        // If quantity is provided in the request, it will be ignored for security

        // Store old values for audit log
        String oldProductName = stock.getProduct() != null ? stock.getProduct().getName() : null;
        String oldWarehouseName = stock.getWarehouse().getName();
        Integer oldMinStockLevel = stock.getMinStockLevel();
        Integer oldReservedQuantity = stock.getReservedQuantity();
        Integer oldConsignedQuantity = stock.getConsignedQuantity();
        String oldAdditionNote = stock.getAdditionNote();
        String oldCustomerName = stock.getCustomerName();
        String oldCustomerPhone = stock.getCustomerPhone();
        String oldIrsaliyeNo = stock.getIrsaliyeNo();
        LocalDate oldIrsaliyeDate = stock.getIrsaliyeDate();

        // Track changes
        List<String> changes = new ArrayList<>();

        // A row booked into the wrong warehouse is corrected here rather than by a transfer:
        // nothing physically moved, so recording a shipment would put a delivery that never
        // happened into the transfer history. The row keeps its id, so the orders, requests and
        // audit entries pointing at it follow the correction instead of being left behind.
        Warehouse warehouse = resolveTargetWarehouse(stock, stockDetails);
        boolean warehouseChanged = !warehouse.getId().equals(stock.getWarehouse().getId());

        Product product = resolveTargetProduct(stock, stockDetails);
        boolean productChanged = !product.getId().equals(stock.getProduct().getId());

        // Consignment rows are told apart by their customer, so the details are settled before the
        // collision lookup that reads them — doing it the other way round would let a blank name
        // merge two customers' goods into one row. A request value wins over the stored one, which
        // is what a move out of a standard warehouse into a consignment one needs.
        boolean targetIsEmanet = warehouse.getWarehouseType() == WarehouseType.EMANET_DEPO;
        String emanetCustomerName = targetIsEmanet
                ? com.warehouse.util.TurkishText.toTitleCase(
                        valueOrFallback(stockDetails.getCustomerName(), oldCustomerName))
                : null;
        String emanetCustomerPhone = targetIsEmanet
                ? trimToNull(valueOrFallback(stockDetails.getCustomerPhone(), oldCustomerPhone))
                : null;
        // Demanded only when the details are actually being written or the row is moving into a
        // consignment warehouse. A legacy row with missing details is left alone otherwise, so an
        // edit to its minimum level does not fail on a field that edit never touched.
        if (targetIsEmanet && (warehouseChanged || productChanged
                || stockDetails.getCustomerName() != null || stockDetails.getCustomerPhone() != null)) {
            if (emanetCustomerName == null || emanetCustomerName.isEmpty()) {
                throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING,
                        "Emanet depo için müşteri adı gereklidir.");
            }
            if (emanetCustomerPhone == null) {
                throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING,
                        "Emanet depo için müşteri telefon numarası gereklidir.");
            }
        }

        // Product and warehouse are checked as a pair: either one moving changes which existing
        // row this would collide with, so the lookup has to use the combination that gets saved.
        if (warehouseChanged || productChanged) {
            requireNoRivalStock(product, warehouse, emanetCustomerName, stock.getId());
        }

        if (warehouseChanged) {
            stock.setWarehouse(warehouse);
            changes.add(String.format("Depo: %s → %s", oldWarehouseName, warehouse.getName()));
            logger.info("Warehouse changed from {} to {} for stock id: {}", oldWarehouseName,
                    warehouse.getName(), id);
        }

        if (productChanged) {
            stock.setProduct(product);
            changes.add(String.format("Ürün: %s → %s", oldProductName, product.getName()));
            logger.info("Product changed from {} to {} for stock id: {}", oldProductName,
                    product.getName(), id);
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

        // Waybill number and date move together: the number being present in the request means the
        // waybill block was submitted, so an empty number clears the date with it. Omitting the
        // number entirely leaves both alone, which is what the callers that only touch quantities
        // or the note need.
        if (stockDetails.getIrsaliyeNo() != null) {
            String newIrsaliyeNo = trimToNull(stockDetails.getIrsaliyeNo());
            LocalDate newIrsaliyeDate = newIrsaliyeNo == null ? null : stockDetails.getIrsaliyeDate();
            if (!Objects.equals(oldIrsaliyeNo, newIrsaliyeNo)) {
                stock.setIrsaliyeNo(newIrsaliyeNo);
                changes.add(String.format("İrsaliye No: %s → %s",
                        oldIrsaliyeNo != null ? oldIrsaliyeNo : "(boş)",
                        newIrsaliyeNo != null ? newIrsaliyeNo : "(boş)"));
            }
            if (!Objects.equals(oldIrsaliyeDate, newIrsaliyeDate)) {
                stock.setIrsaliyeDate(newIrsaliyeDate);
                changes.add(String.format("İrsaliye Tarihi: %s → %s",
                        oldIrsaliyeDate != null ? oldIrsaliyeDate : "(boş)",
                        newIrsaliyeDate != null ? newIrsaliyeDate : "(boş)"));
            }
        }

        // Customer details for EMANET_DEPO warehouses. The values were settled at the top, before
        // the collision lookup that reads them.
        if (targetIsEmanet) {
            if (emanetCustomerName != null && !emanetCustomerName.isEmpty()
                    && !Objects.equals(oldCustomerName, emanetCustomerName)) {
                stock.setCustomerName(emanetCustomerName);
                String oldValue = oldCustomerName != null && !oldCustomerName.isEmpty() ? oldCustomerName : "(boş)";
                changes.add(String.format("Müşteri Adı: %s → %s", oldValue, emanetCustomerName));
            }
            if (emanetCustomerPhone != null && !Objects.equals(oldCustomerPhone, emanetCustomerPhone)) {
                stock.setCustomerPhone(emanetCustomerPhone);
                String oldValue = oldCustomerPhone != null && !oldCustomerPhone.isEmpty() ? oldCustomerPhone : "(boş)";
                changes.add(String.format("Müşteri Telefonu: %s → %s", oldValue, emanetCustomerPhone));
            }
        } else {
            // For STANDART warehouses, customer info should always be null. Named in the audit
            // trail when there was something to drop — on a move out of a consignment warehouse
            // the details were on the row a moment ago and their disappearance needs a reason.
            if (oldCustomerName != null || oldCustomerPhone != null) {
                changes.add(String.format("Müşteri Bilgisi: %s → (kaldırıldı)",
                        oldCustomerName != null && !oldCustomerName.isEmpty() ? oldCustomerName : "(boş)"));
            }
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
        return addToStock(stockId, quantity, null);
    }

    @Override
    public Stock addToStock(Long stockId, Integer quantity, String note) {
        return addToStock(stockId, quantity, note, null, null);
    }

    @Override
    public Stock addToStock(Long stockId, Integer quantity, String note, String irsaliyeNo,
                            LocalDate irsaliyeDate) {
        logger.info("Adding {} units to stock id: {}", quantity, stockId);
        ValidationUtil.requirePositive(quantity, "Quantity to add");
        Stock stock = getStockByIdOrThrow(stockId);
        stock.setQuantity(stock.getQuantity() + quantity);
        // The row now holds goods from this delivery, so it carries this delivery's waybill.
        // Nothing is wiped when the caller has none — an older number is better than no number.
        String cleanIrsaliyeNo = trimToNull(irsaliyeNo);
        if (cleanIrsaliyeNo != null) {
            stock.setIrsaliyeNo(cleanIrsaliyeNo);
            stock.setIrsaliyeDate(irsaliyeDate);
        }
        Stock saved = stockRepository.save(stock);
        String username = CurrentUser.usernameOrSystem();
        String normalizedNote = normalizeAdditionNote(note);
        AuditMetadata metadata = buildStockMetadata(saved, quantity);
        metadata.setNote(normalizedNote);
        String detailsMessage = String.format("Stok artırıldı: +%s adet → Yeni=%s | Depo=%s, Ürün=%s",
                String.valueOf(quantity), String.valueOf(saved.getQuantity()),
                saved.getWarehouse().getName(), saved.getProduct().getName());
        if (cleanIrsaliyeNo != null) {
            detailsMessage += String.format(" | İrsaliye: %s%s", cleanIrsaliyeNo,
                    irsaliyeDate != null ? " (" + irsaliyeDate + ")" : "");
        }
        if (normalizedNote != null) {
            detailsMessage += String.format(" | Not: %s", normalizedNote);
        }
        auditService.log(AuditAction.STOCK_ADD, DomainEntityType.Stock.name(), saved.getId(), username,
                detailsMessage, metadata);
        NotificationRequest notifReq = buildNotificationRequest(
                NotificationMessages.STOCK_INCREASED_TITLE,
                String.format("Kullanıcı %s, %s/%s stokuna %s adet ekledi (Yeni toplam: %s).", username,
                        saved.getWarehouse().getName(), saved.getProduct().getName(), String.valueOf(quantity),
                        String.valueOf(saved.getQuantity())),
                saved,
                quantity);
        notifReq.setNote(normalizedNote);
        notificationService.create(notifReq);
        logger.info("Stock increased successfully. Stock id: {}, New quantity: {}", saved.getId(), saved.getQuantity());
        return saved;
    }

    @Override
    public Stock removeFromStock(Long stockId, Integer quantity) {
        return removeFromStock(stockId, quantity, null);
    }

    @Override
    public Stock removeFromStock(Long stockId, Integer quantity, String note) {
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
        String normalizedNote = normalizeAdditionNote(note);
        Warehouse warehouse = saved.getWarehouse();
        boolean isEmanetDepo = warehouse != null && warehouse.getWarehouseType() == WarehouseType.EMANET_DEPO;
        String customerName = isEmanetDepo ? saved.getCustomerName() : null;
        String customerPhone = isEmanetDepo ? saved.getCustomerPhone() : null;
        
        AuditMetadata metadata = buildStockMetadata(saved, -quantity, customerName, customerPhone, null);
        metadata.setNote(normalizedNote);
        String detailsMessage = String.format("Stok azaltıldı: -%s adet → Yeni=%s | Depo=%s, Ürün=%s",
                String.valueOf(quantity), String.valueOf(saved.getQuantity()),
                saved.getWarehouse().getName(), saved.getProduct().getName());
        if (isEmanetDepo && customerName != null && !customerName.trim().isEmpty()) {
            detailsMessage += String.format(" | Müşteri: %s", customerName);
        }
        if (normalizedNote != null) {
            detailsMessage += String.format(" | Not: %s", normalizedNote);
        }
        auditService.log(AuditAction.STOCK_REMOVE, DomainEntityType.Stock.name(), saved.getId(), username,
                detailsMessage, metadata);
        NotificationRequest notifReq = buildNotificationRequest(
                NotificationMessages.STOCK_DECREASED_TITLE,
                String.format("Kullanıcı %s, %s/%s stokundan %s adet çıkardı (Yeni toplam: %s).", username,
                        saved.getWarehouse().getName(), saved.getProduct().getName(), String.valueOf(quantity),
                        String.valueOf(saved.getQuantity())),
                saved,
                -quantity);
        notifReq.setNote(normalizedNote);
        notificationService.create(notifReq);
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
                // Catch domain exceptions
                Stock stock = null;
                try {
                    stock = getStockByIdOrThrow(id);
                } catch (Exception ex) {
                    // Stock not found
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
                // Other errors
                Stock stock = null;
                try {
                    stock = getStockByIdOrThrow(id);
                } catch (Exception ex) {
                    // Stock not found
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

    /**
     * The warehouse this row should end up in — the current one unless the request names another,
     * so callers that only touch quantities or notes are unaffected.
     *
     * <p>A reserved quantity refuses the move: those units are already promised to an order or a
     * transfer that recorded this very row, and quietly changing which warehouse ships them is not
     * a correction but a second mistake. A passive warehouse is refused for the same reason it
     * cannot be picked when the stock is first entered.
     */
    private Warehouse resolveTargetWarehouse(Stock stock, Stock stockDetails) {
        Warehouse current = stock.getWarehouse();
        Long requestedId = stockDetails.getWarehouse() != null ? stockDetails.getWarehouse().getId() : null;
        if (requestedId == null || requestedId.equals(current.getId())) {
            return current;
        }
        Warehouse target = findWarehouseOrThrow(requestedId);
        if (!target.isActive()) {
            throw new WarehouseManagementException(ErrorCode.INVALID_VALUE,
                    String.format("%s deposu pasif durumda, stok bu depoya taşınamaz.", target.getName()));
        }
        int reserved = stock.getReservedQuantity() != null ? stock.getReservedQuantity() : 0;
        if (reserved > 0) {
            throw new WarehouseManagementException(ErrorCode.INVALID_VALUE, String.format(
                    "Bu kayıtta %d adet rezerve miktar var. Depo değişikliği için önce siparişin ya da"
                            + " transferin tamamlanması gerekir.", reserved));
        }
        return target;
    }

    /** The product this row should end up on — the current one unless the request names another. */
    private Product resolveTargetProduct(Stock stock, Stock stockDetails) {
        Product current = stock.getProduct();
        Long requestedId = stockDetails.getProduct() != null ? stockDetails.getProduct().getId() : null;
        if (requestedId == null || requestedId.equals(current.getId())) {
            return current;
        }
        return findProductOrThrow(requestedId);
    }

    /**
     * Refuses a product/warehouse pair another row already occupies. The two are deliberately not
     * merged here: each row has its own id and orders, requests and transfer items point at them
     * individually, so the caller is sent to transfer instead — it moves the quantity and leaves
     * both rows where the rest of the system expects to find them.
     */
    private void requireNoRivalStock(Product product, Warehouse warehouse, String customerName, Long selfId) {
        boolean emanet = warehouse.getWarehouseType() == WarehouseType.EMANET_DEPO;
        Optional<Stock> rival = emanet
                ? stockRepository.findByProductAndWarehouseAndCustomerName(product, warehouse, customerName)
                : stockRepository.findByProductAndWarehouse(product, warehouse);
        if (rival.isEmpty() || Objects.equals(rival.get().getId(), selfId)) {
            return;
        }
        String where = emanet
                ? String.format("%s deposunda %s müşterisi adına", warehouse.getName(), customerName)
                : String.format("%s deposunda", warehouse.getName());
        throw new WarehouseManagementException(ErrorCode.STOCK_ALREADY_EXISTS, String.format(
                "%s bu ürün için zaten bir stok kaydı var. Kayıtları birleştirmek için transfer kullanın.",
                where));
    }

    private static String valueOrFallback(String preferred, String fallback) {
        return preferred != null ? preferred : fallback;
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

    /**
     * Notes are often nothing but a customer name, so they get the same casing treatment as one:
     * a short all-alphabetic note is title cased, a real sentence only gets its first letter
     * capitalised — title casing the whole thing would read "Kalan 2 Adet Teslim Edildi".
     */
    private String normalizeAdditionNote(String additionNote) {
        if (additionNote == null) {
            return null;
        }
        String trimmed = additionNote.trim();
        return trimmed.isEmpty() ? null : com.warehouse.util.TurkishText.toNoteCase(trimmed);
    }

    /**
     * Waybill numbers are stored exactly as typed — the paper is the reference — so only
     * surrounding whitespace is removed. Matching is handled by the key column, which folds case
     * and punctuation.
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AuditMetadata buildStockMetadata(Stock stock, Integer quantityContext) {
        return buildStockMetadata(stock, quantityContext, null, null, null);
    }

    private AuditMetadata buildStockMetadata(Stock stock, Integer quantityContext, String customerName, String customerPhone, Long transferId) {
        if (stock == null) {
            return null;
        }
        Product product = stock.getProduct();
        Warehouse warehouse = stock.getWarehouse();
        // Use provided customer info or fallback to stock's customer info
        String finalCustomerName = customerName != null ? customerName : stock.getCustomerName();
        String finalCustomerPhone = customerPhone != null ? customerPhone : stock.getCustomerPhone();
        return AuditMetadata.builder()
                .warehouseId(warehouse != null ? warehouse.getId() : null)
                .warehouseName(warehouse != null ? warehouse.getName() : null)
                .productId(product != null ? product.getId() : null)
                .productName(product != null ? product.getName() : null)
                .productSku(product != null ? product.getSku() : null)
                .quantity(quantityContext)
                .customerName(finalCustomerName)
                .customerPhone(finalCustomerPhone)
                .transferId(transferId)
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
