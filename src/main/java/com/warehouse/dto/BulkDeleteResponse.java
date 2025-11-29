package com.warehouse.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for bulk delete operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkDeleteResponse {
    private int successCount;
    private int errorCount;
    private List<DeleteError> errors;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteError {
        private Long id;
        private String name;
        private String sku; // For products
        private String errorCode;
        private String errorMessage;
    }
}

