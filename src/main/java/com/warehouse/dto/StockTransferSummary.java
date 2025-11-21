package com.warehouse.dto;

import java.util.Collections;
import java.util.Map;

public class StockTransferSummary {

    private final Map<String, Long> statusCounts;
    private final Map<String, Long> transferTypeCounts;

    public StockTransferSummary(Map<String, Long> statusCounts, Map<String, Long> transferTypeCounts) {
        this.statusCounts = statusCounts != null ? statusCounts : Collections.emptyMap();
        this.transferTypeCounts = transferTypeCounts != null ? transferTypeCounts : Collections.emptyMap();
    }

    public Map<String, Long> getStatusCounts() {
        return statusCounts;
    }

    public Map<String, Long> getTransferTypeCounts() {
        return transferTypeCounts;
    }
}

