package com.warehouse.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferDeletionResult {
    private boolean deleted;
    private boolean approvalRequested;
    private String message;

    public static StockTransferDeletionResult deleted() {
        return new StockTransferDeletionResult(true, false, "Transfer silindi.");
    }

    public static StockTransferDeletionResult approval(String message) {
        return new StockTransferDeletionResult(false, true, message);
    }
}

