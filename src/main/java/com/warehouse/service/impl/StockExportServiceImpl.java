package com.warehouse.service.impl;

import com.warehouse.dto.StockFilter;
import com.warehouse.entity.Stock;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.BrandRepository;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.ColorRepository;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.WarehouseRepository;
import com.warehouse.service.StockExportService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Service implementation for exporting stock data to Excel format.
 * Handles Excel file generation with proper formatting, filtering, and metadata.
 */
@Service
@RequiredArgsConstructor
public class StockExportServiceImpl implements StockExportService {

    private static final Logger logger = LoggerFactory.getLogger(StockExportServiceImpl.class);
    private static final DateTimeFormatter DATE_FORMATTER = 
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.forLanguageTag("tr-TR"));
    
    private static final int PAGE_SIZE = 1000;
    private static final int TITLE_ROW_SPAN = 10;
    private static final int MIN_COLUMN_WIDTH = 2000;
    private static final int MAX_COLUMN_WIDTH = 15000;
    private static final short HEADER_FONT_SIZE = 11;
    private static final short TITLE_FONT_SIZE = 16;
    private static final short INFO_FONT_SIZE = 10;
    private static final short DATA_FONT_SIZE = 10;
    
    private static final String SHEET_NAME = "Stok Raporu";
    private static final String REPORT_TITLE = "STOK YÖNETİMİ RAPORU";
    private static final String EXPORT_DATE_LABEL = "Export Tarihi:";
    private static final String FILTER_LABEL = "Filtre: Tüm Kayıtlar";
    private static final String FILTERS_LABEL = "Uygulanan Filtreler:";
    private static final String EMPTY_VALUE = "-";
    private static final String BULLET_POINT = "• ";
    
    private static final String[] COLUMN_HEADERS = {
        "ID", "Ürün Adı", "SKU", "Marka", "Renk", "Kategori", "Alt Kategori",
        "Depo", "Depo Tipi", "Konum", "Miktar", "Rezerve", "Emanet", 
        "Müsait", "Min. Stok", "Müşteri Adı", "Müşteri Telefon", "Not", "Son Güncelleme"
    };

    private final StockRepository stockRepository;
    private final BrandRepository brandRepository;
    private final ColorRepository colorRepository;
    private final WarehouseRepository warehouseRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public Resource exportToExcel(StockFilter filter) {
        logger.debug("Starting Excel export with filter: {}", filter);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            ExcelStyles styles = new ExcelStyles(workbook);
            
            int currentRow = createHeaderSection(sheet, filter, styles);
            int headerRowIndex = currentRow - 1;
            currentRow = createDataSection(sheet, filter, currentRow, styles);
            configureSheetLayout(sheet, headerRowIndex);
            
            byte[] excelBytes = writeWorkbookToBytes(workbook);
            logger.info("Excel export completed successfully. Total rows exported: {}", 
                    getTotalRowCount(currentRow));
            
            return new ByteArrayResource(excelBytes);
            
        } catch (IOException e) {
            logger.error("Failed to create Excel file during export", e);
            throw new WarehouseManagementException(ErrorCode.INTERNAL_SERVER_ERROR, 
                    "Excel dosyası oluşturulurken bir hata oluştu", e);
        }
    }

    private int createHeaderSection(Sheet sheet, StockFilter filter, ExcelStyles styles) {
        int rowIndex = 0;
        
        rowIndex = addTitleRow(sheet, rowIndex, styles.titleStyle);
        rowIndex = addExportDateRow(sheet, rowIndex, styles.infoStyle);
        rowIndex = addFilterInformationRows(sheet, filter, rowIndex, styles.infoStyle);
        rowIndex++;
        rowIndex = addColumnHeaders(sheet, rowIndex, styles.headerStyle);
        
        return rowIndex;
    }

    private int addTitleRow(Sheet sheet, int rowIndex, CellStyle style) {
        Row titleRow = sheet.createRow(rowIndex);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(REPORT_TITLE);
        titleCell.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, TITLE_ROW_SPAN));
        return rowIndex + 1;
    }

    private int addExportDateRow(Sheet sheet, int rowIndex, CellStyle style) {
        Row dateRow = sheet.createRow(rowIndex);
        Cell dateLabelCell = dateRow.createCell(0);
        dateLabelCell.setCellValue(EXPORT_DATE_LABEL);
        dateLabelCell.setCellStyle(style);
        
        Cell dateValueCell = dateRow.createCell(1);
        dateValueCell.setCellValue(LocalDateTime.now().format(DATE_FORMATTER));
        dateValueCell.setCellStyle(style);
        
        return rowIndex + 1;
    }

    private int addColumnHeaders(Sheet sheet, int rowIndex, CellStyle style) {
        Row headerRow = sheet.createRow(rowIndex);
        for (int i = 0; i < COLUMN_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(COLUMN_HEADERS[i]);
            cell.setCellStyle(style);
        }
        return rowIndex + 1;
    }

    private int createDataSection(Sheet sheet, StockFilter filter, int startRow, ExcelStyles styles) {
        List<Stock> stocks = fetchAllStocks(filter);
        int currentRow = startRow;
        
        for (Stock stock : stocks) {
            Row row = sheet.createRow(currentRow++);
            populateStockRow(row, stock, styles);
        }
        
        return currentRow;
    }

    private void populateStockRow(Row row, Stock stock, ExcelStyles styles) {
        int columnIndex = 0;
        
        setCellValue(row, columnIndex++, stock.getId().toString(), styles.numberStyle);
        setCellValue(row, columnIndex++, getProductName(stock), styles.dataStyle);
        setCellValue(row, columnIndex++, getProductSku(stock), styles.dataStyle);
        setCellValue(row, columnIndex++, getBrandName(stock), styles.dataStyle);
        setCellValue(row, columnIndex++, getColorName(stock), styles.dataStyle);
        
        CategoryInfo categoryInfo = getCategoryInfo(stock);
        setCellValue(row, columnIndex++, categoryInfo.categoryName(), styles.dataStyle);
        setCellValue(row, columnIndex++, categoryInfo.subCategoryName(), styles.dataStyle);
        
        setCellValue(row, columnIndex++, getWarehouseName(stock), styles.dataStyle);
        setCellValue(row, columnIndex++, getWarehouseType(stock), styles.dataStyle);
        setCellValue(row, columnIndex++, getWarehouseLocation(stock), styles.dataStyle);
        
        setCellValue(row, columnIndex++, getQuantity(stock), styles.numberStyle);
        setCellValue(row, columnIndex++, getReservedQuantity(stock), styles.numberStyle);
        setCellValue(row, columnIndex++, getConsignedQuantity(stock), styles.numberStyle);
        setCellValue(row, columnIndex++, getAvailableQuantity(stock), styles.numberStyle);
        setCellValue(row, columnIndex++, getMinStockLevel(stock), styles.numberStyle);
        
        setCellValue(row, columnIndex++, getCustomerName(stock), styles.dataStyle);
        setCellValue(row, columnIndex++, getCustomerPhone(stock), styles.dataStyle);
        setCellValue(row, columnIndex++, getAdditionNote(stock), styles.dataStyle);
        setCellValue(row, columnIndex, formatLastUpdated(stock), styles.dateStyle);
    }

    private int addFilterInformationRows(Sheet sheet, StockFilter filter, int startRow, CellStyle style) {
        List<String> filterInfo = buildFilterInfoList(filter);
        
        if (filterInfo.isEmpty()) {
            return addSingleFilterRow(sheet, startRow, FILTER_LABEL, style);
        }
        
        return addMultipleFilterRows(sheet, startRow, filterInfo, style);
    }

    private int addSingleFilterRow(Sheet sheet, int rowIndex, String label, CellStyle style) {
        Row filterRow = sheet.createRow(rowIndex);
        Cell filterCell = filterRow.createCell(0);
        filterCell.setCellValue(label);
        filterCell.setCellStyle(style);
        return rowIndex + 1;
    }

    private int addMultipleFilterRows(Sheet sheet, int startRow, List<String> filterInfo, CellStyle style) {
        int currentRow = startRow;
        
        Row labelRow = sheet.createRow(currentRow++);
        Cell labelCell = labelRow.createCell(0);
        labelCell.setCellValue(FILTERS_LABEL);
        labelCell.setCellStyle(style);
        
        for (String info : filterInfo) {
            Row filterRow = sheet.createRow(currentRow++);
            Cell filterCell = filterRow.createCell(1);
            filterCell.setCellValue(BULLET_POINT + info);
            filterCell.setCellStyle(style);
        }
        
        return currentRow;
    }

    private List<String> buildFilterInfoList(StockFilter filter) {
        List<String> filterInfo = new ArrayList<>();
        
        if (filter == null) {
            return filterInfo;
        }
        
        addFilterIfPresent(filterInfo, filter.getBrandId(), "Marka", 
                id -> brandRepository.findById(id).map(b -> b.getName()));
        addFilterIfPresent(filterInfo, filter.getColorId(), "Renk", 
                id -> colorRepository.findById(id).map(c -> c.getName()));
        // Warehouse filter supports both single id and multi ids
        if (filter.getWarehouseIds() != null && !filter.getWarehouseIds().isEmpty()) {
            List<String> names = filter.getWarehouseIds().stream()
                    .filter(id -> id != null)
                    .map(id -> warehouseRepository.findById(id).map(w -> w.getName()).orElse("ID: " + id))
                    .toList();
            if (!names.isEmpty()) {
                filterInfo.add("Depo: " + String.join(", ", names));
            }
        } else {
            addFilterIfPresent(filterInfo, filter.getWarehouseId(), "Depo",
                    id -> warehouseRepository.findById(id).map(w -> w.getName()));
        }
        addFilterIfPresent(filterInfo, filter.getCategoryId(), "Kategori", 
                id -> categoryRepository.findById(id).map(c -> c.getName()));
        addFilterIfPresent(filterInfo, filter.getSubCategoryId(), "Alt Kategori", 
                id -> categoryRepository.findById(id).map(c -> c.getName()));
        
        if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
            filterInfo.add("Arama: " + filter.getSearch());
        }
        
        if (filter.getStatus() != null && filter.getStatus() != StockFilter.Status.ALL) {
            filterInfo.add("Durum: " + getStatusText(filter.getStatus()));
        }
        
        if (filter.isReservedOnly()) {
            filterInfo.add("Sadece Rezerve Olanlar");
        }
        
        if (filter.isConsignedOnly()) {
            filterInfo.add("Sadece Emanet Olanlar");
        }
        
        return filterInfo;
    }

    private void addFilterIfPresent(List<String> filterInfo, Long id, String label, 
                                   java.util.function.Function<Long, Optional<String>> nameResolver) {
        if (id != null) {
            String name = nameResolver.apply(id).orElse("ID: " + id);
            filterInfo.add(label + ": " + name);
        }
    }

    private String getStatusText(StockFilter.Status status) {
        return switch (status) {
            case LOW -> "Düşük Stok";
            case OUT -> "Stokta Yok";
            default -> status.name();
        };
    }

    private List<Stock> fetchAllStocks(StockFilter filter) {
        List<Stock> allStocks = new ArrayList<>();
        StockFilter appliedFilter = filter != null ? filter : new StockFilter();
        FilterParams params = buildFilterParams(appliedFilter);
        
        int page = 0;
        boolean hasMore = true;
        
        while (hasMore) {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE);
            Page<Stock> stockPage = stockRepository.findByFilters(
                    params.brandId(),
                    params.colorId(),
                    params.warehouseId(),
                    params.warehouseIds(),
                    params.hasWarehouseFilter(),
                    params.categoryId(),
                    params.subCategoryId(),
                    params.searchEnabled(),
                    params.searchPattern(),
                    params.reservedOnly(),
                    params.consignedOnly(),
                    params.hideOutOfStock(),
                    params.statusValue(),
                    params.lastUpdatedFrom(),
                    params.lastUpdatedTo(),
                    pageable);

            allStocks.addAll(stockPage.getContent());
            hasMore = stockPage.hasNext();
            page++;
        }
        
        logger.debug("Fetched {} stocks for export", allStocks.size());
        return allStocks;
    }

    private FilterParams buildFilterParams(StockFilter filter) {
        StockFilter.Status status = Optional.ofNullable(filter.getStatus())
                .orElse(StockFilter.Status.ALL);
        String search = filter.getSearch();
        boolean searchEnabled = search != null && !search.isBlank();
        String searchPattern = searchEnabled && search != null
                ? "%" + search.toLowerCase(Locale.forLanguageTag("tr-TR")) + "%"
                : "%";
        LocalDateTime lastUpdatedFrom = filter.getLastUpdatedFrom() != null ? filter.getLastUpdatedFrom() : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime lastUpdatedTo = filter.getLastUpdatedTo() != null ? filter.getLastUpdatedTo() : LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        List<Long> warehouseIds = filter.getWarehouseIds();
        boolean hasWarehouseFilter = warehouseIds != null && !warehouseIds.isEmpty();
        Long warehouseId = hasWarehouseFilter ? null : filter.getWarehouseId();

        return new FilterParams(
                filter.getBrandId(),
                filter.getColorId(),
                warehouseId,
                hasWarehouseFilter ? warehouseIds : List.of(0L),
                hasWarehouseFilter,
                filter.getCategoryId(),
                filter.getSubCategoryId(),
                searchEnabled,
                searchPattern,
                filter.isReservedOnly(),
                filter.isConsignedOnly(),
                filter.isHideOutOfStock(),
                status.name(),
                lastUpdatedFrom,
                lastUpdatedTo
        );
    }

    private void configureSheetLayout(Sheet sheet, int headerRowIndex) {
        autoSizeColumns(sheet);
        freezeHeaderRow(sheet, headerRowIndex);
    }

    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i < COLUMN_HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
            int currentWidth = sheet.getColumnWidth(i);
            
            if (currentWidth < MIN_COLUMN_WIDTH) {
                sheet.setColumnWidth(i, MIN_COLUMN_WIDTH);
            } else if (currentWidth > MAX_COLUMN_WIDTH) {
                sheet.setColumnWidth(i, MAX_COLUMN_WIDTH);
            }
        }
    }

    private void freezeHeaderRow(Sheet sheet, int headerRowIndex) {
        int freezeRow = headerRowIndex + 1;
        sheet.createFreezePane(0, freezeRow);
    }

    private byte[] writeWorkbookToBytes(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private int getTotalRowCount(int currentRow) {
        return currentRow;
    }

    private void setCellValue(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : EMPTY_VALUE);
        cell.setCellStyle(style);
    }

    private void setCellValue(Row row, int column, Integer value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : 0);
        cell.setCellStyle(style);
    }

    private String getProductName(Stock stock) {
        return stock.getProduct() != null ? stock.getProduct().getName() : EMPTY_VALUE;
    }

    private String getProductSku(Stock stock) {
        return stock.getProduct() != null ? stock.getProduct().getSku() : EMPTY_VALUE;
    }

    private String getBrandName(Stock stock) {
        return stock.getProduct() != null && stock.getProduct().getBrand() != null
                ? stock.getProduct().getBrand().getName() : EMPTY_VALUE;
    }

    private String getColorName(Stock stock) {
        return stock.getProduct() != null && stock.getProduct().getColor() != null
                ? stock.getProduct().getColor().getName() : EMPTY_VALUE;
    }

    private CategoryInfo getCategoryInfo(Stock stock) {
        if (stock.getProduct() == null || stock.getProduct().getCategory() == null) {
            return new CategoryInfo(EMPTY_VALUE, EMPTY_VALUE);
        }
        
        var category = stock.getProduct().getCategory();
        if (category.getParent() != null) {
            return new CategoryInfo(category.getParent().getName(), category.getName());
        }
        return new CategoryInfo(category.getName(), EMPTY_VALUE);
    }

    private String getWarehouseName(Stock stock) {
        return stock.getWarehouse() != null ? stock.getWarehouse().getName() : EMPTY_VALUE;
    }

    private String getWarehouseType(Stock stock) {
        return stock.getWarehouse() != null && stock.getWarehouse().getWarehouseType() != null
                ? stock.getWarehouse().getWarehouseType().name() : EMPTY_VALUE;
    }

    private String getWarehouseLocation(Stock stock) {
        return stock.getWarehouse() != null && stock.getWarehouse().getLocation() != null
                ? stock.getWarehouse().getLocation() : EMPTY_VALUE;
    }

    private Integer getQuantity(Stock stock) {
        return stock.getQuantity() != null ? stock.getQuantity() : 0;
    }

    private Integer getReservedQuantity(Stock stock) {
        return stock.getReservedQuantity() != null ? stock.getReservedQuantity() : 0;
    }

    private Integer getConsignedQuantity(Stock stock) {
        return stock.getConsignedQuantity() != null ? stock.getConsignedQuantity() : 0;
    }

    private Integer getAvailableQuantity(Stock stock) {
        return stock.getAvailableQuantity() != null ? stock.getAvailableQuantity() : 0;
    }

    private Integer getMinStockLevel(Stock stock) {
        return stock.getMinStockLevel() != null ? stock.getMinStockLevel() : 0;
    }

    private String getCustomerName(Stock stock) {
        return stock.getCustomerName() != null ? stock.getCustomerName() : EMPTY_VALUE;
    }

    private String getCustomerPhone(Stock stock) {
        return stock.getCustomerPhone() != null ? stock.getCustomerPhone() : EMPTY_VALUE;
    }

    private String getAdditionNote(Stock stock) {
        return stock.getAdditionNote() != null ? stock.getAdditionNote() : EMPTY_VALUE;
    }

    private String formatLastUpdated(Stock stock) {
        return stock.getLastUpdated() != null
                ? stock.getLastUpdated().format(DATE_FORMATTER) : EMPTY_VALUE;
    }

    private record CategoryInfo(String categoryName, String subCategoryName) {}
    
    private record FilterParams(
            Long brandId,
            Long colorId,
            Long warehouseId,
            List<Long> warehouseIds,
            boolean hasWarehouseFilter,
            Long categoryId,
            Long subCategoryId,
            boolean searchEnabled,
            String searchPattern,
            boolean reservedOnly,
            boolean consignedOnly,
            boolean hideOutOfStock,
            String statusValue,
            java.time.LocalDateTime lastUpdatedFrom,
            java.time.LocalDateTime lastUpdatedTo
    ) {}

    private static class ExcelStyles {
        final CellStyle headerStyle;
        final CellStyle titleStyle;
        final CellStyle infoStyle;
        final CellStyle dataStyle;
        final CellStyle numberStyle;
        final CellStyle dateStyle;

        ExcelStyles(Workbook workbook) {
            this.headerStyle = createHeaderStyle(workbook);
            this.titleStyle = createTitleStyle(workbook);
            this.infoStyle = createInfoStyle(workbook);
            this.dataStyle = createDataStyle(workbook);
            this.numberStyle = createNumberStyle(workbook, dataStyle);
            this.dateStyle = createDateStyle(workbook, dataStyle);
        }

        private static CellStyle createHeaderStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints(HEADER_FONT_SIZE);
            font.setColor(IndexedColors.WHITE.getIndex());
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorders(style, BorderStyle.THIN);
            style.setWrapText(true);
            return style;
        }

        private static CellStyle createTitleStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints(TITLE_FONT_SIZE);
            font.setColor(IndexedColors.DARK_BLUE.getIndex());
            style.setFont(font);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        private static CellStyle createInfoStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setFontHeightInPoints(INFO_FONT_SIZE);
            font.setBold(true);
            style.setFont(font);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        private static CellStyle createDataStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setFontHeightInPoints(DATA_FONT_SIZE);
            style.setFont(font);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorders(style, BorderStyle.THIN);
            applyBorderColors(style);
            style.setWrapText(true);
            return style;
        }

        private static CellStyle createNumberStyle(Workbook workbook, CellStyle baseStyle) {
            CellStyle style = workbook.createCellStyle();
            style.cloneStyleFrom(baseStyle);
            style.setAlignment(HorizontalAlignment.RIGHT);
            DataFormat format = workbook.createDataFormat();
            style.setDataFormat(format.getFormat("#,##0"));
            return style;
        }

        private static CellStyle createDateStyle(Workbook workbook, CellStyle baseStyle) {
            CellStyle style = workbook.createCellStyle();
            style.cloneStyleFrom(baseStyle);
            style.setAlignment(HorizontalAlignment.CENTER);
            DataFormat format = workbook.createDataFormat();
            style.setDataFormat(format.getFormat("dd.mm.yyyy hh:mm:ss"));
            return style;
        }

        private static void applyBorders(CellStyle style, BorderStyle borderStyle) {
            style.setBorderTop(borderStyle);
            style.setBorderBottom(borderStyle);
            style.setBorderLeft(borderStyle);
            style.setBorderRight(borderStyle);
        }

        private static void applyBorderColors(CellStyle style) {
            short greyColor = IndexedColors.GREY_25_PERCENT.getIndex();
            style.setTopBorderColor(greyColor);
            style.setBottomBorderColor(greyColor);
            style.setLeftBorderColor(greyColor);
            style.setRightBorderColor(greyColor);
        }
    }
}
