package com.warehouse.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.config.ImportProperties;
import com.warehouse.entity.*;
import com.warehouse.repository.*;
import com.warehouse.service.AuditService;
import com.warehouse.service.NotificationService;
import com.warehouse.enums.AuditAction;
import com.warehouse.enums.DomainEntityType;
import com.warehouse.service.StockImportService;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.warehouse.constants.ImportMessages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of StockImportService for managing stock imports from Excel files.
 */
@Service
@Transactional
public class StockImportServiceImpl implements StockImportService {

    private static final Logger logger = LoggerFactory.getLogger(StockImportServiceImpl.class);
    private static final String TEMPLATE_SHEET_NAME = ImportMessages.TEMPLATE_SHEET_NAME;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockRepository stockRepository;
    private final StockImportHistoryRepository historyRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ImportProperties importProperties;

    public StockImportServiceImpl(ProductRepository productRepository,
                                  CategoryRepository categoryRepository,
                                  BrandRepository brandRepository,
                                  WarehouseRepository warehouseRepository,
                                  StockRepository stockRepository,
                                  StockImportHistoryRepository historyRepository,
                                  AuditService auditService,
                                  NotificationService notificationService,
                                  ImportProperties importProperties) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockRepository = stockRepository;
        this.historyRepository = historyRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.importProperties = importProperties;
    }

    @Override
    public XSSFWorkbook generateTemplate() {
        logger.debug("Generating stock import template");
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(TEMPLATE_SHEET_NAME);

        CellStyle headerStyle = createHeaderStyle(workbook);

        Row header = sheet.createRow(0);
        String[] headers = {
            "Ürün Adı (zorunlu)",
            "Stok Kodu (zorunlu)",
            "Kategori Adı (zorunlu)",
            "Marka (opsiyonel)",
            "Miktar (zorunlu)",
            "Emanet (opsiyonel)",
            "Fiyat (opsiyonel)",
            "Minimum Stok (opsiyonel)",
            "Rezerve (opsiyonel)"
        };

        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
            header.getCell(i).setCellStyle(headerStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        logger.debug("Template generated successfully");
        return workbook;
    }

    @Override
    public com.warehouse.entity.StockImportHistory importStocks(Long warehouseId, MultipartFile file) throws IOException {
        logger.info("Starting stock import for warehouse id: {}", warehouseId);
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> {
                    logger.error("Warehouse not found with id: {}", warehouseId);
                    return new IllegalArgumentException("Warehouse not found: " + warehouseId);
                });

        String storedFilename = System.currentTimeMillis() + "_" + sanitizeFilename(file.getOriginalFilename());
        Path targetFile = storeFile(file, storedFilename);
        com.warehouse.entity.StockImportHistory history = createImportHistory(file, warehouse, storedFilename);

        try {
            ImportResult result = processExcelFile(targetFile, warehouse);
            updateHistoryWithResult(history, result);
            logger.info("Stock import completed. Status: {}, Processed: {}/{}, Failed: {}", 
                    history.getStatus(), result.getProcessedRows(), result.getTotalRows(), result.getFailedRows().size());
        } catch (Exception ex) {
            logger.error("Error during stock import for warehouse id: {}", warehouseId, ex);
            history.setStatus(ImportMessages.STATUS_FAILED);
            history.setErrorMessage(ex.getMessage());
            historyRepository.save(history);
            throw ex;
        } finally {
            historyRepository.save(history);
        }

        return history;
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        XSSFFont boldFont = workbook.createFont();
        boldFont.setBold(true);
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(boldFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        return headerStyle;
    }

    private Path storeFile(MultipartFile file, String storedFilename) throws IOException {
        Path dir = Path.of(importProperties.getStorageDir());
        Files.createDirectories(dir);
        Path target = dir.resolve(storedFilename);
        try (InputStream is = file.getInputStream()) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }
        logger.debug("File stored: {}", target);
        return target;
    }

    private com.warehouse.entity.StockImportHistory createImportHistory(MultipartFile file, Warehouse warehouse, String storedFilename) {
        com.warehouse.entity.StockImportHistory history = new com.warehouse.entity.StockImportHistory();
        history.setOriginalFilename(file.getOriginalFilename());
        history.setStoredFilename(storedFilename);
        history.setContentType(file.getContentType() != null ? 
                file.getContentType() : ImportMessages.CONTENT_TYPE_XLSX);
        history.setWarehouse(warehouse);
        history.setStatus(ImportMessages.STATUS_PROCESSING);
        return historyRepository.save(history);
    }

    private ImportResult processExcelFile(Path filePath, Warehouse warehouse) throws IOException {
        ImportResult result = new ImportResult();
        ObjectMapper objectMapper = new ObjectMapper();

        try (InputStream is = Files.newInputStream(filePath);
             XSSFWorkbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            logger.debug("Processing Excel file with {} rows", sheet.getLastRowNum());

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                int excelRowNumber = i + 1;
                RowData rowData = extractRowData(row, excelRowNumber);

                if (isRowEmpty(rowData)) {
                    continue;
                }

                result.incrementTotalRows();

                try {
                    if (hasMissingRequiredFields(rowData)) {
                        String reason = buildMissingFieldsReason(rowData);
                        result.addFailedRow(new com.warehouse.dto.FailedRowInfo(excelRowNumber, 
                                rowData.getName(), rowData.getSku(), reason));
                        continue;
                    }

                    processRow(rowData, warehouse, result);

                } catch (Exception ex) {
                    logger.warn("Error processing row {}: {}", excelRowNumber, ex.getMessage());
                    String errorMsg = ex.getMessage() != null && !ex.getMessage().isEmpty() 
                            ? ex.getMessage() : ex.getClass().getSimpleName();
                    result.addFailedRow(new com.warehouse.dto.FailedRowInfo(excelRowNumber, 
                            rowData.getName(), rowData.getSku(), ImportMessages.ERROR_PREFIX + errorMsg));
                }
            }

            result.setFailedRowsJson(objectMapper.writeValueAsString(result.getFailedRows()));
        }

        return result;
    }

    private RowData extractRowData(Row row, int rowNumber) {
        return new RowData(
            rowNumber,
            getStringValue(row, 0),  // Ürün Adı
            getStringValue(row, 1),  // Stok Kodu
            getStringValue(row, 2),  // Kategori Adı
            getStringValue(row, 3),  // Marka (opsiyonel)
            getStringValue(row, 4),  // Miktar
            getStringValue(row, 5),  // Emanet
            getStringValue(row, 6),  // Fiyat
            getStringValue(row, 7),  // Minimum Stok
            getStringValue(row, 8)   // Rezerve
        );
    }

    private String getStringValue(Row row, int index) {
        if (row.getCell(index) == null) return null;
        org.apache.poi.ss.usermodel.Cell cell = row.getCell(index);
        org.apache.poi.ss.usermodel.CellType cellType = cell.getCellType();
        
        if (cellType == org.apache.poi.ss.usermodel.CellType.STRING) {
            return cell.getStringCellValue();
        } else if (cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
            double numericValue = cell.getNumericCellValue();
            if (numericValue == Math.floor(numericValue)) {
                return String.valueOf((long) numericValue);
            }
            return String.valueOf(numericValue);
        } else if (cellType == org.apache.poi.ss.usermodel.CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        } else {
            org.apache.poi.ss.usermodel.DataFormatter formatter = new org.apache.poi.ss.usermodel.DataFormatter();
            return formatter.formatCellValue(cell);
        }
    }

    private boolean isRowEmpty(RowData rowData) {
        return isBlank(rowData.getName()) && isBlank(rowData.getSku()) 
                && isBlank(rowData.getCategoryName()) && isBlank(rowData.getQuantity());
    }

    private boolean hasMissingRequiredFields(RowData rowData) {
        return isBlank(rowData.getName()) || isBlank(rowData.getSku()) 
                || isBlank(rowData.getCategoryName()) || isBlank(rowData.getQuantity());
    }

    private String buildMissingFieldsReason(RowData rowData) {
        List<String> missingFields = new ArrayList<>();
        if (isBlank(rowData.getName())) missingFields.add("Ürün Adı");
        if (isBlank(rowData.getSku())) missingFields.add("Stok Kodu");
        if (isBlank(rowData.getCategoryName())) missingFields.add("Kategori Adı");
        if (isBlank(rowData.getQuantity())) missingFields.add("Miktar");
        return ImportMessages.MISSING_REQUIRED_PREFIX + String.join(", ", missingFields);
    }

    private void processRow(RowData rowData, Warehouse warehouse, ImportResult result) {
        BigDecimal price = parsePrice(rowData.getPrice());
        int quantity = parseIntSafe(rowData.getQuantity(), 0);
        int minStock = parseIntSafe(rowData.getMinStock(), 0);
        int reserved = parseIntSafe(rowData.getReserved(), 0);
        int consigned = parseIntSafe(rowData.getConsigned(), 0);

        Category category = findOrCreateCategory(rowData.getCategoryName(), result);
        Brand brand = null;
        if (!isBlank(rowData.getBrandName())) {
            brand = findOrCreateBrand(rowData.getBrandName(), result);
        }
        Product product = findOrCreateProduct(rowData, price, category, brand, result);
        findOrUpdateStock(product, warehouse, quantity, minStock, reserved, consigned, result);

        result.incrementProcessedRows();
    }

    private BigDecimal parsePrice(String priceStr) {
        if (isBlank(priceStr)) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(priceStr.trim().replace(",", "."));
            return price.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : price;
        } catch (Exception ex) {
            logger.debug("Failed to parse price: {}", priceStr);
            return null;
        }
    }

    private Category findOrCreateCategory(String categoryName, ImportResult result) {
        return categoryRepository.findByName(categoryName.trim())
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setName(categoryName.trim());
                    category.setActive(true);
                    Category saved = categoryRepository.save(category);
                    result.incrementCreatedCategories();
                    logger.debug("Created new category: {}", categoryName);
                    return saved;
                });
    }

    private Brand findOrCreateBrand(String brandName, ImportResult result) {
        return brandRepository.findByName(brandName.trim())
                .orElseGet(() -> {
                    Brand brand = new Brand();
                    brand.setName(brandName.trim());
                    brand.setActive(true);
                    Brand saved = brandRepository.save(brand);
                    result.incrementCreatedBrands();
                    logger.debug("Created new brand: {}", brandName);
                    return saved;
                });
    }

    private Product findOrCreateProduct(RowData rowData, BigDecimal price, Category category, Brand brand, ImportResult result) {
        String skuTrimmed = rowData.getSku().trim();
        Optional<Product> existingProduct = productRepository.findBySku(skuTrimmed);
        
        if (existingProduct.isPresent()) {
            Product product = existingProduct.get();
            // Eğer marka belirtilmişse ve ürünün markası yoksa veya farklıysa, markayı güncelle
            if (brand != null && (product.getBrand() == null || !product.getBrand().getId().equals(brand.getId()))) {
                product.setBrand(brand);
                product.setUpdatedAt(LocalDateTime.now());
                productRepository.save(product);
                logger.debug("Updated product brand: {} -> {}", product.getName(), brand.getName());
            }
            return product;
        }

        Product product = new Product();
        product.setName(rowData.getName().trim());
        product.setSku(skuTrimmed);
        product.setPrice(price);
        product.setCategory(category);
        product.setBrand(brand);
        product.setActive(true);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        Product saved = productRepository.save(product);
        result.incrementCreatedProducts();
        logger.debug("Created new product: {}", saved.getName());
        return saved;
    }

    private void findOrUpdateStock(Product product, Warehouse warehouse, int quantity, 
                                    int minStock, int reserved, int consigned, ImportResult result) {
        Optional<Stock> existingStock = stockRepository.findByProductAndWarehouse(product, warehouse);
        
        if (existingStock.isPresent()) {
            Stock stock = existingStock.get();
            int oldQty = stock.getQuantity() != null ? stock.getQuantity() : 0;
            stock.setQuantity(quantity);
            stock.setMinStockLevel(minStock);
            stock.setReservedQuantity(reserved);
            stock.setConsignedQuantity(consigned);
            stock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(stock);
            result.incrementUpdatedStocks();

            int delta = quantity - oldQty;
            if (delta > 0) {
                String username = getCurrentUsername();
                // Audit: stok artırma
                auditService.log(
                        AuditAction.STOCK_ADD,
                        DomainEntityType.Stock.name(),
                        stock.getId(),
                        username,
                        String.format("Stok artırıldı: Depo=%s, Ürün=%s, Artış=%d, Yeni Miktar=%d",
                                warehouse.getName(), product.getName(), delta, quantity)
                );
                // Notification
                notificationService.create(
                        com.warehouse.constants.NotificationMessages.STOCK_INCREASED_TITLE,
                        String.format("Kullanıcı %s, %s/%s için %d adet stok artırdı.",
                                username, warehouse.getName(), product.getName(), delta),
                        DomainEntityType.Stock.name(),
                        stock.getId()
                );
            }
        } else {
            Stock stock = new Stock();
            stock.setProduct(product);
            stock.setWarehouse(warehouse);
            stock.setQuantity(quantity);
            stock.setMinStockLevel(minStock);
            stock.setReservedQuantity(reserved);
            stock.setConsignedQuantity(consigned);
            stock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(stock);
            result.incrementCreatedStocks();

            String username = getCurrentUsername();
            // Audit: stok oluşturma
            auditService.log(
                    AuditAction.STOCK_CREATE,
                    DomainEntityType.Stock.name(),
                    stock.getId(),
                    username,
                    String.format("Stok oluşturuldu: Depo=%s, Ürün=%s, Miktar=%d",
                            warehouse.getName(), product.getName(), quantity)
            );
            // Notification
            notificationService.create(
                    com.warehouse.constants.NotificationMessages.STOCK_CREATED_TITLE,
                    String.format("Kullanıcı %s, %s/%s için %d adet stok oluşturdu.",
                            username, warehouse.getName(), product.getName(), quantity),
                    DomainEntityType.Stock.name(),
                    stock.getId()
            );
        }
    }

    private void updateHistoryWithResult(com.warehouse.entity.StockImportHistory history, ImportResult result) {
        history.setTotalRows(result.getTotalRows());
        history.setCreatedProducts(result.getCreatedProducts());
        history.setUpdatedProducts(0);
        history.setCreatedStocks(result.getCreatedStocks());
        history.setUpdatedStocks(result.getUpdatedStocks());
        history.setCreatedCategories(result.getCreatedCategories());

        if (result.getProcessedRows() == 0) {
            history.setStatus(ImportMessages.STATUS_FAILED);
            history.setErrorMessage(ImportMessages.NO_ROWS_PROCESSED);
        } else if (result.getProcessedRows() < result.getTotalRows()) {
            history.setStatus(ImportMessages.STATUS_PARTIAL);
            history.setErrorMessage(String.format(ImportMessages.SKIPPED_ROWS_TEMPLATE, 
                    result.getTotalRows() - result.getProcessedRows()));
        } else {
            history.setStatus(ImportMessages.STATUS_SUCCESS);
        }

        if (!result.getFailedRows().isEmpty()) {
            history.setFailedRows(result.getFailedRowsJson());
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "file.xlsx";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private int parseIntSafe(String s, int defaultValue) {
        try {
            if (s == null) return defaultValue;
            String cleaned = s.trim().replace(".", "").replace(",", ".");
            return (int) Math.round(Double.parseDouble(cleaned));
        } catch (Exception ex) {
            logger.debug("Failed to parse integer: {}", s);
            return defaultValue;
        }
    }

    private String getCurrentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                return auth.getName();
            }
        } catch (Exception ignored) {}
        return "system";
    }

    private static class RowData {
        private final String name;
        private final String sku;
        private final String categoryName;
        private final String brandName;
        private final String quantity;
        private final String consigned;
        private final String price;
        private final String minStock;
        private final String reserved;

        RowData(int rowNumber, String name, String sku, String categoryName, String brandName, String quantity,
                String consigned, String price, String minStock, String reserved) {
            this.name = name;
            this.sku = sku;
            this.categoryName = categoryName;
            this.brandName = brandName;
            this.quantity = quantity;
            this.consigned = consigned;
            this.price = price;
            this.minStock = minStock;
            this.reserved = reserved;
        }
        String getName() { return name; }
        String getSku() { return sku; }
        String getCategoryName() { return categoryName; }
        String getBrandName() { return brandName; }
        String getQuantity() { return quantity; }
        String getConsigned() { return consigned; }
        String getPrice() { return price; }
        String getMinStock() { return minStock; }
        String getReserved() { return reserved; }
    }

    private static class ImportResult {
        private int totalRows = 0;
        private int processedRows = 0;
        private int createdProducts = 0;
        private int createdCategories = 0;
        private int createdBrands = 0;
        private int createdStocks = 0;
        private int updatedStocks = 0;
        private final List<com.warehouse.dto.FailedRowInfo> failedRows = new ArrayList<>();
        private String failedRowsJson;

        void incrementTotalRows() { totalRows++; }
        void incrementProcessedRows() { processedRows++; }
        void incrementCreatedProducts() { createdProducts++; }
        void incrementCreatedCategories() { createdCategories++; }
        void incrementCreatedBrands() { createdBrands++; }
        void incrementCreatedStocks() { createdStocks++; }
        void incrementUpdatedStocks() { updatedStocks++; }
        void addFailedRow(com.warehouse.dto.FailedRowInfo row) { failedRows.add(row); }

        int getTotalRows() { return totalRows; }
        int getProcessedRows() { return processedRows; }
        int getCreatedProducts() { return createdProducts; }
        int getCreatedCategories() { return createdCategories; }
        int getCreatedBrands() { return createdBrands; }
        int getCreatedStocks() { return createdStocks; }
        int getUpdatedStocks() { return updatedStocks; }
        List<com.warehouse.dto.FailedRowInfo> getFailedRows() { return failedRows; }
        String getFailedRowsJson() { return failedRowsJson; }
        void setFailedRowsJson(String json) { this.failedRowsJson = json; }
    }
}

