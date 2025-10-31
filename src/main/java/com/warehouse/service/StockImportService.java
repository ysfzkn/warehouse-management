package com.warehouse.service;

import com.warehouse.entity.StockImportHistory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Service interface for managing stock imports from Excel files.
 */
public interface StockImportService {

    /**
     * Generates an Excel template for stock import.
     *
     * @return XSSFWorkbook containing the template
     */
    XSSFWorkbook generateTemplate();

    /**
     * Imports stocks from an Excel file.
     *
     * @param warehouseId the warehouse ID where stocks will be imported
     * @param file the Excel file to import
     * @return StockImportHistory containing import results
     * @throws IOException if file operations fail
     */
    StockImportHistory importStocks(Long warehouseId, MultipartFile file) throws IOException;
}
