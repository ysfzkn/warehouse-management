package com.warehouse.service;

import com.warehouse.entity.*;
import com.warehouse.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class StockImportService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockRepository stockRepository;
    private final StockImportHistoryRepository historyRepository;

    private static final String STORAGE_DIR = "uploads/stock-imports";

    public XSSFWorkbook generateTemplate() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Stok Şablonu");

        // Create bold + bordered header style
        XSSFFont boldFont = workbook.createFont();
        boldFont.setBold(true);
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(boldFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Ürün Adı (zorunlu)");
        header.createCell(1).setCellValue("Stok Kodu (zorunlu)");
        header.createCell(2).setCellValue("Kategori Adı (zorunlu)");
        header.createCell(3).setCellValue("Miktar (zorunlu)");
        header.createCell(4).setCellValue("Emanet (opsiyonel)");
        header.createCell(5).setCellValue("Fiyat (opsiyonel)");
        header.createCell(6).setCellValue("Minimum Stok (opsiyonel)");
        header.createCell(7).setCellValue("Rezerve (opsiyonel)");

        // Apply style to header cells
        for (int c = 0; c <= 7; c++) {
            if (header.getCell(c) != null) {
                header.getCell(c).setCellStyle(headerStyle);
            }
        }

        for (int i = 0; i <= 7; i++) {
            sheet.autoSizeColumn(i);
        }
        return workbook;
    }

    @Transactional
    public StockImportHistory importStocks(Long warehouseId, MultipartFile file) throws IOException {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new IllegalArgumentException("Depo bulunamadı: " + warehouseId));

        // Store file
        Path dir = Path.of(STORAGE_DIR);
        Files.createDirectories(dir);
        String storedFilename = System.currentTimeMillis() + "_" + sanitize(file.getOriginalFilename());
        Path target = dir.resolve(storedFilename);
        try (InputStream is = file.getInputStream()) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }

        StockImportHistory history = new StockImportHistory();
        history.setOriginalFilename(file.getOriginalFilename());
        history.setStoredFilename(storedFilename);
        history.setContentType(file.getContentType() != null ? file.getContentType() : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        history.setWarehouse(warehouse);
        history.setStatus("PROCESSING");
        historyRepository.save(history);

        int totalRows = 0;
        int processedRows = 0; // Başarıyla işlenen satır sayısı
        int createdProducts = 0;
        int updatedProducts = 0; // Mevcut ürünleri güncellemiyoruz; hep 0 kalacak
        int createdCategories = 0;
        int createdStocks = 0;
        int updatedStocks = 0;
        List<FailedRowInfo> failedRowsList = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try (InputStream is = Files.newInputStream(target); XSSFWorkbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                int excelRowNumber = i + 1; // Excel'deki gerçek satır numarası (1-based, header dahil)
                // Yeni sütun sıralaması: 0:Ürün Adı, 1:Stok Kodu, 2:Kategori, 3:Miktar, 4:Emanet, 5:Fiyat, 6:MinStok, 7:Rezerve
                String name = getString(row, 0);
                String sku = getString(row, 1);
                String categoryName = getString(row, 2);
                String quantityStr = getString(row, 3);
                String consignedStr = getString(row, 4);
                String priceStr = getString(row, 5);
                String minStockStr = getString(row, 6);
                String reservedStr = getString(row, 7);
                
                // Tamamen boş satırı ignore et (tüm zorunlu alanlar boşsa - fiyat opsiyonel)
                if (isBlank(name) && isBlank(sku) && isBlank(categoryName) && isBlank(quantityStr)) {
                    continue; // Tamamen boş satırı atla, hata olarak gösterme
                }
                
                totalRows++; // Sadece en az bir alanı dolu olan satırları say
                
                boolean rowProcessed = false;
                String failureReason = null;
                
                try {
                    // Zorunlu alan kontrolü (fiyat opsiyonel)
                    if (isBlank(name) || isBlank(sku) || isBlank(categoryName) || isBlank(quantityStr)) {
                        List<String> missingFields = new ArrayList<>();
                        if (isBlank(name)) missingFields.add("Ürün Adı");
                        if (isBlank(sku)) missingFields.add("Stok Kodu");
                        if (isBlank(categoryName)) missingFields.add("Kategori Adı");
                        if (isBlank(quantityStr)) missingFields.add("Miktar");
                        failureReason = "Eksik zorunlu alanlar: " + String.join(", ", missingFields);
                        failedRowsList.add(new FailedRowInfo(excelRowNumber, name != null ? name.trim() : "", sku != null ? sku.trim() : "", failureReason));
                        continue;
                    }

                    // Fiyat opsiyonel - boşsa null veya 0 kullan
                    BigDecimal price = null;
                    if (!isBlank(priceStr)) {
                        try {
                            price = new BigDecimal(priceStr.trim().replace(",", "."));
                            if (price.compareTo(BigDecimal.ZERO) < 0) {
                                price = BigDecimal.ZERO; // Negatif fiyatları 0'a çevir
                            }
                        } catch (Exception priceEx) {
                            // Fiyat parse edilemezse null bırak
                            price = null;
                        }
                    }
                    int quantity = parseIntSafe(quantityStr, 0);
                    int minStock = parseIntSafe(minStockStr, 0);
                    int reserved = parseIntSafe(reservedStr, 0);
                    int consigned = parseIntSafe(consignedStr, 0);

                    // Category: match by name or create
                    Category category = categoryRepository.findByName(categoryName.trim())
                            .orElseGet(() -> {
                                Category c = new Category();
                                c.setName(categoryName.trim());
                                c.setActive(true);
                                return categoryRepository.save(c);
                            });
                    if (category.getId() != null && category.getCreatedAt() != null && category.getUpdatedAt() != null) {
                        // existing
                    } else {
                        createdCategories++;
                    }

                    // Product: SKU zorunlu, SKU ile birebir eşle
                    final String skuTrimmed = sku != null ? sku.trim() : "";
                    Optional<Product> existingBySku = productRepository.findBySku(skuTrimmed);
                    Product product;
                    if (existingBySku.isPresent()) {
                        product = existingBySku.get();
                        // Mevcut üründe isim/kategori/fiyat DEĞİŞTİRME – sadece stokta sayısal düzenleme yapılır
                    } else {
                        product = new Product();
                        product.setName(name.trim());
                        product.setSku(skuTrimmed);
                        product.setPrice(price);
                        product.setCategory(category);
                        product.setActive(true);
                        product.setCreatedAt(LocalDateTime.now());
                        product.setUpdatedAt(LocalDateTime.now());
                        productRepository.save(product);
                        createdProducts++;
                    }

                    // Stock per warehouse: update or create
                    Optional<Stock> existingStockOpt = stockRepository.findByProductAndWarehouse(product, warehouse);
                    if (existingStockOpt.isPresent()) {
                        Stock stock = existingStockOpt.get();
                        stock.setQuantity(quantity);
                        stock.setMinStockLevel(minStock);
                        stock.setReservedQuantity(reserved);
                        stock.setConsignedQuantity(consigned);
                        stock.setLastUpdated(LocalDateTime.now());
                        stockRepository.save(stock);
                        updatedStocks++;
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
                        createdStocks++;
                    }
                    rowProcessed = true;
                } catch (Exception rowEx) {
                    // Bir satırda hata olursa logla ve başarısız satırlara ekle
                    String errorMsg = rowEx.getMessage();
                    if (errorMsg == null || errorMsg.isEmpty()) {
                        errorMsg = rowEx.getClass().getSimpleName();
                    }
                    failedRowsList.add(new FailedRowInfo(excelRowNumber, name != null ? name.trim() : "", sku != null ? sku.trim() : "", "Hata: " + errorMsg));
                    System.err.println("Satır işlenemedi: " + errorMsg);
                }
                if (rowProcessed) {
                    processedRows++;
                }
            }
            // Durum belirleme: Tüm satırlar işlendiyse BAŞARILI, bazıları işlendiyse KISMEN, hiçbiri işlenmediyse BAŞARISIZ
            if (processedRows == 0) {
                history.setStatus("BAŞARISIZ");
                history.setErrorMessage("Hiçbir satır işlenemedi");
            } else if (processedRows < totalRows) {
                history.setStatus("KISMEN");
                history.setErrorMessage((totalRows - processedRows) + " satır atlandı (eksik zorunlu alanlar veya hata)");
            } else {
                history.setStatus("BAŞARILI");
            }
            
            // Başarısız satırları JSON formatında kaydet
            if (!failedRowsList.isEmpty()) {
                try {
                    history.setFailedRows(objectMapper.writeValueAsString(failedRowsList));
                } catch (Exception jsonEx) {
                    System.err.println("Başarısız satırlar JSON'a çevrilemedi: " + jsonEx.getMessage());
                }
            }
        } catch (Exception ex) {
            history.setStatus("BAŞARISIZ");
            history.setErrorMessage(ex.getMessage());
            throw ex;
        } finally {
            history.setTotalRows(totalRows);
            history.setCreatedProducts(createdProducts);
            history.setUpdatedProducts(updatedProducts);
            history.setCreatedStocks(createdStocks);
            history.setUpdatedStocks(updatedStocks);
            history.setCreatedCategories(createdCategories);
            historyRepository.save(history);
        }

        return history;
    }

    private static String sanitize(String filename) {
        if (filename == null) return "file.xlsx";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String getString(Row row, int index) {
        if (row.getCell(index) == null) return null;
        row.getCell(index).setCellType(org.apache.poi.ss.usermodel.CellType.STRING);
        return row.getCell(index).getStringCellValue();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static int parseIntSafe(String s, int def) {
        try {
            if (s == null) return def;
            String cleaned = s.trim().replace(".", "").replace(",", ".");
            return (int) Math.round(Double.parseDouble(cleaned));
        } catch (Exception e) {
            return def;
        }
    }

    // SKU otomatik üretimi kaldırıldı: Excel'de Stok Kodu zorunludur

    // Başarısız satır bilgileri için iç sınıf
    public static class FailedRowInfo {
        private int rowNumber;
        private String productName;
        private String sku;
        private String reason;

        public FailedRowInfo() {
        }

        public FailedRowInfo(int rowNumber, String productName, String sku, String reason) {
            this.rowNumber = rowNumber;
            this.productName = productName;
            this.sku = sku;
            this.reason = reason;
        }

        public int getRowNumber() {
            return rowNumber;
        }

        public void setRowNumber(int rowNumber) {
            this.rowNumber = rowNumber;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}


