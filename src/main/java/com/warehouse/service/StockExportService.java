package com.warehouse.service;

import com.warehouse.dto.StockFilter;
import org.springframework.core.io.Resource;

/**
 * Service interface for exporting stock data to Excel.
 */
public interface StockExportService {
    
    /**
     * Exports stock data to Excel format based on the provided filter.
     * 
     * @param filter The filter to apply to stock data
     * @return Resource containing the Excel file
     */
    Resource exportToExcel(StockFilter filter);
}

