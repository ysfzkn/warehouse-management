package com.warehouse.cezeri.tools;

import com.warehouse.dto.StockRequestDto;
import com.warehouse.enums.StockRequestStatus;
import com.warehouse.service.StockRequestService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tools safe for non-admin users.
 */
@Component
public class CezeriUserTools {

    private final StockRequestService stockRequestService;

    public CezeriUserTools(StockRequestService stockRequestService) {
        this.stockRequestService = stockRequestService;
    }

    @Tool(description = "Mevcut kullanıcının stok taleplerini listeler. İsteğe bağlı status: PENDING/APPROVED/REJECTED.")
    public List<StockRequestDto> myStockRequests(String status) {
        StockRequestStatus parsed = null;
        if (status != null && !status.isBlank()) {
            try {
                parsed = StockRequestStatus.valueOf(status.trim().toUpperCase());
            } catch (Exception ignored) { }
        }
        return stockRequestService.getRequestsForCurrentUser(parsed);
    }
}


