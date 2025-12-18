package com.warehouse.cezeri.tools;

import com.warehouse.dto.PagedResponse;
import com.warehouse.dto.StockRequestDto;
import com.warehouse.dto.StockTransferFilter;
import com.warehouse.dto.StockTransferSummary;
import com.warehouse.entity.StockTransfer;
import com.warehouse.enums.StockRequestStatus;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.service.StockRequestService;
import com.warehouse.service.StockTransferService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tools safe for non-admin users.
 */
@Component
public class CezeriUserTools {

    private static final Logger log = LoggerFactory.getLogger(CezeriUserTools.class);

    private final StockRequestService stockRequestService;
    private final StockTransferService stockTransferService;

    public CezeriUserTools(StockRequestService stockRequestService,
                           StockTransferService stockTransferService) {
        this.stockRequestService = stockRequestService;
        this.stockTransferService = stockTransferService;
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

    @Tool(description = "Mevcut kullanıcının transfer hareketlerini arar ve sayfalı listeler. Filtreler: status (PENDING/IN_TRANSIT/COMPLETED/CANCELLED veya Türkçe), transferType (WAREHOUSE/CUSTOMER_DELIVERY veya depo/müşteri), sourceWarehouseId, destinationWarehouseId, startDate/endDate (tarih+saat), productName, sku, driverName, notes, customerQuery (örn. müşteri adı/telefon). Sayfalama: page,size.")
    public PagedResponse<com.warehouse.cezeri.tools.CezeriAdminTools.CezeriTransferListItem> myTransfers(String status,
                                                                                                         String transferType,
                                                                                                         Long sourceWarehouseId,
                                                                                                         Long destinationWarehouseId,
                                                                                                         String startDate,
                                                                                                         String endDate,
                                                                                                         String productName,
                                                                                                         String sku,
                                                                                                         String driverName,
                                                                                                         String notes,
                                                                                                         String customerQuery,
                                                                                                         Integer page,
                                                                                                         Integer size) {
        log.debug("Cezeri tool myTransfers filters: status={}, transferType={}, sourceWarehouseId={}, destinationWarehouseId={}, startDateRaw={}, endDateRaw={}, productName={}, sku={}, driverName={}, notes={}, customerQuery={}, page={}, size={}",
                status, transferType, sourceWarehouseId, destinationWarehouseId, startDate, endDate, productName, sku, driverName, notes, customerQuery, page, size);
        int safePage = page != null && page >= 0 ? page : 0;
        int safeSize = size != null ? Math.min(Math.max(size, 1), 50) : 25;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "transferDate"));

        StockTransferFilter filter = new StockTransferFilter();
        filter.setStatus(parseTransferStatus(status));
        filter.setTransferType(parseTransferType(transferType));
        filter.setSourceWarehouseId(sourceWarehouseId);
        filter.setDestinationWarehouseId(destinationWarehouseId);
        filter.setStartDate(com.warehouse.cezeri.tools.CezeriAdminTools.parseDateTimeLoose(startDate));
        filter.setEndDate(com.warehouse.cezeri.tools.CezeriAdminTools.parseDateTimeLoose(endDate));
        filter.setProductName(trimToNull(productName));
        filter.setSku(trimToNull(sku));
        filter.setDriverName(trimToNull(driverName));
        filter.setNotes(trimToNull(notes));
        filter.setCustomerQuery(trimToNull(customerQuery));

        Page<StockTransfer> transfers = stockTransferService.getTransfersForCurrentUserPaged(filter, pageable);

        boolean relaxedTransferType = false;
        if (transfers.getTotalElements() == 0
                && filter.getCustomerQuery() != null
                && filter.getTransferType() != null) {
            StockTransferFilter relaxed = new StockTransferFilter();
            relaxed.setStatus(filter.getStatus());
            relaxed.setTransferType(null);
            relaxed.setSourceWarehouseId(filter.getSourceWarehouseId());
            relaxed.setDestinationWarehouseId(filter.getDestinationWarehouseId());
            relaxed.setStartDate(filter.getStartDate());
            relaxed.setEndDate(filter.getEndDate());
            relaxed.setProductName(filter.getProductName());
            relaxed.setSku(filter.getSku());
            relaxed.setDriverName(filter.getDriverName());
            relaxed.setNotes(filter.getNotes());
            relaxed.setCustomerQuery(filter.getCustomerQuery());

            transfers = stockTransferService.getTransfersForCurrentUserPaged(relaxed, pageable);
            relaxedTransferType = true;
            log.debug("Cezeri tool myTransfers fallback: relaxed transferType filter (was {}). totalElements={}", filter.getTransferType(), transfers.getTotalElements());
        }
        List<com.warehouse.cezeri.tools.CezeriAdminTools.CezeriTransferListItem> content = transfers.getContent().stream()
                .map(com.warehouse.cezeri.tools.CezeriAdminTools::toListItem)
                .toList();

        StockTransferSummary summary = stockTransferService.getTransferSummary(filter, true);
        Map<String, Object> metadata = Map.of(
                "statusCounts", summary != null ? summary.getStatusCounts() : Map.of(),
                "transferTypeCounts", summary != null ? summary.getTransferTypeCounts() : Map.of(),
                "relaxedTransferType", relaxedTransferType
        );

        return new PagedResponse<>(
                content,
                transfers.getNumber(),
                transfers.getSize(),
                transfers.getTotalElements(),
                transfers.getTotalPages(),
                transfers.isFirst(),
                transfers.isLast(),
                metadata
        );
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static TransferStatus parseTransferStatus(String raw) {
        return com.warehouse.cezeri.tools.CezeriAdminTools.parseTransferStatus(raw);
    }

    private static TransferType parseTransferType(String raw) {
        return com.warehouse.cezeri.tools.CezeriAdminTools.parseTransferType(raw);
    }
}


