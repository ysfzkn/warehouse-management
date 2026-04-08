package com.warehouse.assistant.wms.tools;

import com.warehouse.dto.AuditLogFilter;
import com.warehouse.dto.NotificationFilter;
import com.warehouse.dto.PagedResponse;
import com.warehouse.dto.StockTransferFilter;
import com.warehouse.dto.StockTransferSummary;
import com.warehouse.entity.AuditLog;
import com.warehouse.entity.Notification;
import com.warehouse.entity.StockTransfer;
import com.warehouse.repository.AuditLogRepository;
import com.warehouse.repository.specification.AuditLogSpecifications;
import com.warehouse.service.StockTransferService;
import com.warehouse.service.NotificationService;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Admin-only tools for the WMS assistant. This bean is only registered as
 * tools for the admin ChatClient via {@code WmsAssistantConfig}.
 */
@Component
public class WmsAdminTools {

    private static final Logger log = LoggerFactory.getLogger(WmsAdminTools.class);

    private final AuditLogRepository auditLogRepository;
    private final NotificationService notificationService;
    private final StockTransferService stockTransferService;

    public WmsAdminTools(AuditLogRepository auditLogRepository,
                         NotificationService notificationService,
                         StockTransferService stockTransferService) {
        this.auditLogRepository = auditLogRepository;
        this.notificationService = notificationService;
        this.stockTransferService = stockTransferService;
    }

    @Tool(description = "ADMIN: Belirli bir entity için audit log kayıtlarını getir (entityType + entityId).")
    public List<AuditLog> auditByEntity(String entityType, Long entityId, Integer page, Integer size) {
        return getAuditLogs(entityType, entityId, page, size, auditLogRepository);
    }

    static List<AuditLog> getAuditLogs(String entityType, Long entityId, Integer page, Integer size, AuditLogRepository auditLogRepository) {
        int safePage = page != null && page >= 0 ? page : 0;
        int safeSize = size != null ? Math.min(Math.max(size, 1), 200) : 200;
        return auditLogRepository.findByEntity(entityType, entityId, PageRequest.of(safePage, safeSize)).getContent();
    }

    @Tool(description = "ADMIN: Depo bazlı audit log araması (opsiyonel arama metni ve tarih aralığı).")
    public Page<AuditLog> auditByWarehouse(Long warehouseId,
                                           Integer page,
                                           Integer size,
                                           String search,
                                           LocalDateTime startDate,
                                           LocalDateTime endDate) {
        return getAuditLogs(warehouseId, page, size, search, startDate, endDate, auditLogRepository);
    }

    static Page<AuditLog> getAuditLogs(Long warehouseId, Integer page, Integer size, String search, LocalDateTime startDate, LocalDateTime endDate, AuditLogRepository auditLogRepository) {
        int safePage = page != null && page >= 0 ? page : 0;
        int safeSize = size != null ? Math.min(Math.max(size, 1), 200) : 200;
        AuditLogFilter filter = AuditLogFilter.builder()
                .warehouseId(warehouseId)
                .search(search)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        return auditLogRepository.findAll(AuditLogSpecifications.withFilter(filter), PageRequest.of(safePage, safeSize));
    }

    @Tool(description = "ADMIN: Okunmamış bildirimleri listeler.")
    public List<Notification> unreadNotifications() {
        return notificationService.unread();
    }

    @Tool(description = "ADMIN: Son bildirimleri listeler.")
    public List<Notification> recentNotifications() {
        return notificationService.recent();
    }

    @Tool(description = "ADMIN: Bildirimlerde arama/filtreleme (warehouse/entityType/search/date range) + sayfalama.")
    public Page<Notification> searchNotifications(Integer page,
                                                  Integer size,
                                                  Long warehouseId,
                                                  String entityType,
                                                  String search,
                                                  LocalDateTime startDate,
                                                  LocalDateTime endDate) {
        return getNotifications(page, size, warehouseId, entityType, search, startDate, endDate, notificationService);
    }

    static Page<Notification> getNotifications(Integer page, Integer size, Long warehouseId, String entityType, String search, LocalDateTime startDate, LocalDateTime endDate, NotificationService notificationService) {
        int safePage = page != null && page >= 0 ? page : 0;
        int safeSize = size != null ? Math.min(Math.max(size, 1), 200) : 50;
        NotificationFilter filter = NotificationFilter.builder()
                .warehouseId(warehouseId)
                .entityType(entityType)
                .search(search)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        return notificationService.search(filter, PageRequest.of(safePage, safeSize));
    }

    public static class WmsTransferListItem {
        public Long id;
        public LocalDateTime transferDate;
        public TransferStatus status;
        public TransferType transferType;

        public Long sourceWarehouseId;
        public String sourceWarehouseName;

        public Long destinationWarehouseId;
        public String destinationWarehouseName;

        /** for CUSTOMER_DELIVERY transfers */
        public String customerFullName;
        public String customerPhoneMasked;

        public Integer totalQuantity;
        public Integer uniqueProductCount;

        /** e.g. "3 x Ürün A, 1 x Ürün B" (shortened) */
        public String itemsSummary;

        public String vehiclePlate;
        public String driverName;
    }

    @Tool(description = "ADMIN: Transfer hareketlerini arar ve sayfalı olarak listeler. Filtreler: status (PENDING/IN_TRANSIT/COMPLETED/CANCELLED veya Türkçe: bekleyen/yolda/tamamlanan/iptal), transferType (WAREHOUSE/CUSTOMER_DELIVERY veya depo/müşteri), sourceWarehouseId, destinationWarehouseId, startDate/endDate (tarih+saat; örn: 2025-12-12T10:30:00 veya 2025-12-12 10:30 veya 2025-12-12), productName, sku, driverName, notes, customerQuery (örn. müşteri adı/telefon). Sayfalama: page,size.")
    public PagedResponse<WmsTransferListItem> searchTransfers(String status,
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
        log.debug("WMS tool searchTransfers filters: status={}, transferType={}, sourceWarehouseId={}, destinationWarehouseId={}, startDateRaw={}, endDateRaw={}, productName={}, sku={}, driverName={}, notes={}, customerQuery={}, page={}, size={}",
                status, transferType, sourceWarehouseId, destinationWarehouseId, startDate, endDate, productName, sku, driverName, notes, customerQuery, page, size);
        int safePage = page != null && page >= 0 ? page : 0;
        int safeSize = size != null ? Math.min(Math.max(size, 1), 50) : 25;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "transferDate"));

        StockTransferFilter filter = new StockTransferFilter();
        filter.setStatus(parseTransferStatus(status));
        filter.setTransferType(parseTransferType(transferType));
        filter.setSourceWarehouseId(sourceWarehouseId);
        filter.setDestinationWarehouseId(destinationWarehouseId);
        filter.setStartDate(parseDateTimeLoose(startDate));
        filter.setEndDate(parseDateTimeLoose(endDate));
        filter.setProductName(trimToNull(productName));
        filter.setSku(trimToNull(sku));
        filter.setDriverName(trimToNull(driverName));
        filter.setNotes(trimToNull(notes));
        filter.setCustomerQuery(trimToNull(customerQuery));

        Page<StockTransfer> transfers = stockTransferService.getTransfersPaged(filter, pageable);

        // Smart fallback: if the user searches by a person/customer query and the model guessed a transferType,
        // relax transferType to avoid false negatives.
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

            transfers = stockTransferService.getTransfersPaged(relaxed, pageable);
            relaxedTransferType = true;
            log.debug("WMS tool searchTransfers fallback: relaxed transferType filter (was {}). totalElements={}", filter.getTransferType(), transfers.getTotalElements());
            filter = relaxed;
        }
        List<WmsTransferListItem> content = transfers.getContent().stream().map(WmsAdminTools::toListItem).toList();

        StockTransferSummary summary = stockTransferService.getTransferSummary(filter, false);
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

    public static WmsTransferListItem toListItem(StockTransfer t) {
        WmsTransferListItem out = new WmsTransferListItem();
        if (t == null) return out;

        out.id = t.getId();
        out.transferDate = t.getTransferDate();
        out.status = t.getStatus();
        out.transferType = t.getTransferType();

        if (t.getSourceWarehouse() != null) {
            out.sourceWarehouseId = t.getSourceWarehouse().getId();
            out.sourceWarehouseName = t.getSourceWarehouse().getName();
        }
        if (t.getDestinationWarehouse() != null) {
            out.destinationWarehouseId = t.getDestinationWarehouse().getId();
            out.destinationWarehouseName = t.getDestinationWarehouse().getName();
        }

        out.customerFullName = trimToNull(t.getCustomerFullName());
        out.customerPhoneMasked = maskPhone(t.getCustomerPhone());

        out.vehiclePlate = trimToNull(t.getVehiclePlate());
        out.driverName = trimToNull(t.getDriverName());

        List<String> itemParts = (t.getItems() != null ? t.getItems() : List.<com.warehouse.entity.StockTransferItem>of())
                .stream()
                .filter(Objects::nonNull)
                .map(it -> {
                    String name = it.getProduct() != null ? it.getProduct().getName() : "Ürün";
                    Integer qty = it.getQuantity();
                    if (qty == null) qty = 0;
                    return qty + " x " + name;
                })
                .collect(Collectors.toList());

        if (!itemParts.isEmpty()) {
            out.totalQuantity = t.getItems().stream()
                    .filter(Objects::nonNull)
                    .map(it -> it.getQuantity() != null ? it.getQuantity() : 0)
                    .mapToInt(Integer::intValue)
                    .sum();
            out.uniqueProductCount = (int) t.getItems().stream()
                    .filter(Objects::nonNull)
                    .map(it -> it.getProduct() != null ? it.getProduct().getId() : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            out.itemsSummary = summarize(itemParts, 4);
        } else {
            out.totalQuantity = t.getQuantity();
            out.uniqueProductCount = t.getProduct() != null ? 1 : 0;
            out.itemsSummary = t.getProduct() != null
                    ? (out.totalQuantity != null ? out.totalQuantity : 0) + " x " + t.getProduct().getName()
                    : null;
        }

        return out;
    }

    private static String summarize(List<String> parts, int maxItems) {
        if (parts == null || parts.isEmpty()) return null;
        int safeMax = Math.max(1, maxItems);
        if (parts.size() <= safeMax) {
            return String.join(", ", parts);
        }
        return String.join(", ", parts.subList(0, safeMax)) + " + " + (parts.size() - safeMax) + " kalem daha";
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        // Strip wrapping quotes that might appear from LLM extraction (e.g., "Hakan Özkan")
        while (t.length() >= 2 && isQuote(t.charAt(0)) && isQuote(t.charAt(t.length() - 1))) {
            t = t.substring(1, t.length() - 1).trim();
        }
        t = t.replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean isQuote(char c) {
        return c == '"' || c == '\''
                || c == '“' || c == '”'
                || c == '‘' || c == '’'
                || c == '`';
    }

    public static LocalDateTime parseDateTimeLoose(String raw) {
        String s = trimToNull(raw);
        if (s == null) return null;

        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException ignored) { }

        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ignored) { }
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (DateTimeParseException ignored) { }

        try {
            return LocalDateTime.parse(s + "T00:00:00", DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException ignored) { }

        return null;
    }

    private static String maskPhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        String last4 = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
        return "***" + last4;
    }

    public static TransferStatus parseTransferStatus(String raw) {
        String s = trimToNull(raw);
        if (s == null) return null;
        String n = s.toLowerCase(java.util.Locale.forLanguageTag("tr-TR"));
        try {
            return TransferStatus.valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception ignored) { }

        if (n.contains("bekle")) return TransferStatus.PENDING;
        if (n.contains("yol") || n.contains("sevki") || n.contains("transit")) return TransferStatus.IN_TRANSIT;
        if (n.contains("tamam") || n.contains("bitti") || n.contains("completed")) return TransferStatus.COMPLETED;
        if (n.contains("iptal") || n.contains("cancel")) return TransferStatus.CANCELLED;
        return null;
    }

    public static TransferType parseTransferType(String raw) {
        String s = trimToNull(raw);
        if (s == null) return null;
        String n = s.toLowerCase(java.util.Locale.forLanguageTag("tr-TR"));
        try {
            return TransferType.valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception ignored) { }

        if (n.contains("depo") || n.contains("warehouse")) return TransferType.WAREHOUSE;
        if (n.contains("müşteri") || n.contains("musteri") || n.contains("customer")) return TransferType.CUSTOMER_DELIVERY;
        return null;
    }
}
