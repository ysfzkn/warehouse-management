package com.warehouse.cezeri.tools;

import com.warehouse.dto.PagedResponse;
import com.warehouse.dto.StockDto;
import com.warehouse.dto.StockFilter;
import com.warehouse.entity.Stock;
import com.warehouse.enums.WarehouseType;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.StockRepository.EmanetCustomerCandidate;
import com.warehouse.service.StockService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import jakarta.annotation.Nullable;

import java.util.Locale;
import java.util.List;

/**
 * Read-only stock tools. Safe for tool calling.
 */
@Component
public class CezeriStockTools {

    private final StockService stockService;
    private final StockRepository stockRepository;

    public CezeriStockTools(StockService stockService, StockRepository stockRepository) {
        this.stockService = stockService;
        this.stockRepository = stockRepository;
    }

    @Tool(description = "Search stock records with filters (warehouse/category/brand/color/status) and pagination. Use for answering stock availability questions.")
    public PagedResponse<StockDto> searchStocks(Long brandId,
                                                Long colorId,
                                                Long warehouseId,
                                                Long categoryId,
                                                Long subCategoryId,
                                                Boolean reservedOnly,
                                                Boolean consignedOnly,
                                                String search,
                                                String status,
                                                Integer page,
                                                Integer size,
                                                String sortBy,
                                                String sortDir) {
        int safePage = page != null && page >= 0 ? page : 0;
        int safeSize = size != null && size > 0 ? Math.min(size, 100) : 20;

        Sort sort = resolveSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        StockFilter filter = new StockFilter();
        filter.setBrandId(brandId);
        filter.setColorId(colorId);
        filter.setWarehouseId(warehouseId);
        filter.setCategoryId(categoryId);
        filter.setSubCategoryId(subCategoryId);
        filter.setReservedOnly(Boolean.TRUE.equals(reservedOnly));
        filter.setConsignedOnly(Boolean.TRUE.equals(consignedOnly));
        filter.setSearch(search != null && !search.isBlank() ? search.trim() : null);
        filter.setStatus(StockFilter.Status.from(status != null ? status : "all"));

        Page<Stock> stocks = stockService.getStocks(filter, pageable);
        List<StockDto> content = stocks.getContent().stream().map(this::toDto).toList();
        return new PagedResponse<>(
                content,
                stocks.getNumber(),
                stocks.getSize(),
                stocks.getTotalElements(),
                stocks.getTotalPages(),
                stocks.isFirst(),
                stocks.isLast()
        );
    }

    @Tool(description = "List low-stock items. Optionally filter by warehouseId.")
    public List<StockDto> lowStock(Long warehouseId) {
        List<Stock> stocks = warehouseId != null
                ? stockService.getLowStockItemsByWarehouse(warehouseId)
                : stockService.getLowStockItems();
        return stocks.stream().map(this::toDto).toList();
    }

    @Tool(description = "List out-of-stock items across all warehouses.")
    public List<StockDto> outOfStock() {
        return stockService.getOutOfStockItems().stream().map(this::toDto).toList();
    }

    @Tool(description = "Get stock for a specific product in a specific warehouse.")
    public StockDto getStockByProductAndWarehouse(Long productId, Long warehouseId) {
        if (productId == null || warehouseId == null) {
            return null;
        }
        return stockService.getStockByProductAndWarehouse(productId, warehouseId)
                .map(this::toDto)
                .orElse(null);
    }

    @Tool(description = "Get total stock quantity by filters (brand/color/warehouse/category/search/status). Use this to answer 'Does this brand have stock?' reliably.")
    public Long totalStockQuantity(Long brandId,
                                   Long colorId,
                                   Long warehouseId,
                                   Long categoryId,
                                   Long subCategoryId,
                                   Boolean reservedOnly,
                                   Boolean consignedOnly,
                                   String search,
                                   String status) {
        StockFilter filter = new StockFilter();
        filter.setBrandId(brandId);
        filter.setColorId(colorId);
        filter.setWarehouseId(warehouseId);
        filter.setCategoryId(categoryId);
        filter.setSubCategoryId(subCategoryId);
        filter.setReservedOnly(Boolean.TRUE.equals(reservedOnly));
        filter.setConsignedOnly(Boolean.TRUE.equals(consignedOnly));
        filter.setSearch(search != null && !search.isBlank() ? search.trim() : null);
        filter.setStatus(StockFilter.Status.from(status != null ? status : "all"));
        return stockService.getTotalQuantityByFilter(filter);
    }

    @Tool(description = "Get total stock quantity in Emanet Depo. Optional customerQuery narrows by customer name/phone; optional warehouseId narrows to a specific emanet warehouse. This is NOT the same as 'emanete ayrılmış miktar' in normal depolar.")
    public Long totalEmanetDepotStockQuantity(@Nullable String customerQuery,
                                              @Nullable String warehouseIdStr) {
        // Parse warehouseId - handle empty string from Ollama
        Long warehouseId = null;
        if (warehouseIdStr != null && !warehouseIdStr.isBlank()) {
            try {
                warehouseId = Long.parseLong(warehouseIdStr.trim());
            } catch (NumberFormatException e) {
                // Invalid format, treat as null
                warehouseId = null;
            }
        }
        
        String q = customerQuery != null && !customerQuery.isBlank() ? customerQuery.trim() : "";
        boolean searchEnabled = !q.isBlank();
        String patternRaw = searchEnabled ? "%" + q + "%" : "%";
        String patternLower = searchEnabled ? "%" + q.toLowerCase(Locale.ROOT) + "%" : "%";
        String digits = q.replaceAll("\\D", "");
        boolean digitsEnabled = !digits.isBlank();
        String digitsPattern = digitsEnabled ? "%" + digits + "%" : "%";

        Long sum = stockRepository.sumEmanetDepotQuantity(
                WarehouseType.EMANET_DEPO,
                warehouseId,
                searchEnabled,
                patternRaw,
                patternLower,
                digitsEnabled,
                digitsPattern
        );
        return sum != null ? sum : 0L;
    }

    public static class EmanetProductTotals {
        public Long totalQuantity;
        public Long totalAvailable;
    }

    @Tool(description = "Get customer-independent totals for a product in Emanet Depo. If multiple customers have records, this still returns the total. Optional warehouseId narrows to a specific emanet warehouse.")
    public EmanetProductTotals emanetDepotTotalsForProduct(Long productId, @Nullable Long warehouseId) {
        if (productId == null) return null;
        Long totalQty = stockRepository.sumEmanetDepotQuantityByProduct(WarehouseType.EMANET_DEPO, warehouseId, productId);
        Long totalAvail = stockRepository.sumEmanetDepotAvailableByProduct(WarehouseType.EMANET_DEPO, warehouseId, productId);
        EmanetProductTotals out = new EmanetProductTotals();
        out.totalQuantity = totalQty != null ? totalQty : 0L;
        out.totalAvailable = totalAvail != null ? totalAvail : 0L;
        return out;
    }

    @Tool(description = "List Emanet Depo stock records for a customer name or phone. Optional warehouseId narrows to a specific emanet warehouse.")
    public PagedResponse<StockDto> listEmanetStocksByCustomer(@Nullable String customerQuery,
                                                              @Nullable String warehouseIdStr,
                                                              @Nullable String pageStr,
                                                              @Nullable String sizeStr) {
        // Parse warehouseId - handle empty string from Ollama
        Long warehouseId = null;
        if (warehouseIdStr != null && !warehouseIdStr.isBlank()) {
            try {
                warehouseId = Long.parseLong(warehouseIdStr.trim());
            } catch (NumberFormatException e) {
                warehouseId = null;
            }
        }
        
        // Parse page and size - handle empty string from Ollama
        int safePage = 0;
        if (pageStr != null && !pageStr.isBlank()) {
            try {
                int parsed = Integer.parseInt(pageStr.trim());
                safePage = parsed >= 0 ? parsed : 0;
            } catch (NumberFormatException e) {
                safePage = 0;
            }
        }
        
        int safeSize = 20;
        if (sizeStr != null && !sizeStr.isBlank()) {
            try {
                int parsed = Integer.parseInt(sizeStr.trim());
                safeSize = Math.min(Math.max(parsed, 1), 100);
            } catch (NumberFormatException e) {
                safeSize = 20;
            }
        }
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "lastUpdated"));

        String q = customerQuery != null ? customerQuery.trim() : "";
        boolean searchEnabled = !q.isBlank();
        String patternRaw = searchEnabled ? "%" + q + "%" : "%";
        String patternLower = searchEnabled ? "%" + q.toLowerCase(Locale.ROOT) + "%" : "%";
        String digits = q.replaceAll("\\D", "");
        boolean digitsEnabled = !digits.isBlank();
        String digitsPattern = digitsEnabled ? "%" + digits + "%" : "%";

        Page<Stock> result = stockRepository.findEmanetByCustomer(
                WarehouseType.EMANET_DEPO,
                warehouseId,
                searchEnabled,
                patternRaw,
                patternLower,
                digitsEnabled,
                digitsPattern,
                pageable
        );

        List<StockDto> content = result.getContent().stream().map(this::toDto).toList();
        return new PagedResponse<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    public static class EmanetCustomerOption {
        public String name;
        public String phoneMasked;
        /** a safe lookup hint (digits-only, typically last 4) */
        public String phoneHint;
    }

    @Tool(description = "Find matching customers in Emanet Depo by partial name or phone. Use this when the user might refer to multiple customers (e.g., same first name). Returns a short list of options (name + masked phone) so you can ask 'hangisi?'.")
    public List<EmanetCustomerOption> findEmanetCustomerOptions(@Nullable String customerQuery,
                                                                @Nullable String warehouseIdStr,
                                                                @Nullable String limitStr) {
        // Parse warehouseId - handle empty string from Ollama
        Long warehouseId = null;
        if (warehouseIdStr != null && !warehouseIdStr.isBlank()) {
            try {
                warehouseId = Long.parseLong(warehouseIdStr.trim());
            } catch (NumberFormatException e) {
                warehouseId = null;
            }
        }
        
        // Parse limit - handle empty string from Ollama
        int safeLimit = 5;
        if (limitStr != null && !limitStr.isBlank()) {
            try {
                int parsed = Integer.parseInt(limitStr.trim());
                safeLimit = Math.min(Math.max(parsed, 1), 10);
            } catch (NumberFormatException e) {
                safeLimit = 5;
            }
        }
        Pageable pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.ASC, "customerName"));

        String q = customerQuery != null ? customerQuery.trim() : "";
        boolean searchEnabled = !q.isBlank();
        String patternRaw = searchEnabled ? "%" + q + "%" : "%";
        String patternLower = searchEnabled ? "%" + q.toLowerCase(Locale.ROOT) + "%" : "%";
        String digits = q.replaceAll("\\D", "");
        boolean digitsEnabled = !digits.isBlank();
        String digitsPattern = digitsEnabled ? "%" + digits + "%" : "%";

        List<EmanetCustomerCandidate> result = stockRepository.findEmanetCustomerCandidates(
                WarehouseType.EMANET_DEPO,
                warehouseId,
                searchEnabled,
                patternRaw,
                patternLower,
                digitsEnabled,
                digitsPattern,
                pageable
        );

        return result.stream().map(this::toCustomerOption).toList();
    }

    private EmanetCustomerOption toCustomerOption(EmanetCustomerCandidate c) {
        String name = c != null && c.getCustomerName() != null ? c.getCustomerName().trim() : "";
        String phone = c != null && c.getCustomerPhone() != null ? c.getCustomerPhone().trim() : "";

        String phoneDigits = phone.replaceAll("\\D", "");
        String last4 = phoneDigits.length() >= 4 ? phoneDigits.substring(phoneDigits.length() - 4) : phoneDigits;

        EmanetCustomerOption opt = new EmanetCustomerOption();
        opt.name = !name.isBlank() ? name : "İsimsiz Müşteri";
        opt.phoneHint = !last4.isBlank() ? last4 : null;
        opt.phoneMasked = !last4.isBlank() ? ("***" + last4) : null;
        return opt;
    }

    private StockDto toDto(Stock s) {
        StockDto dto = new StockDto();
        dto.id = s.getId();
        dto.quantity = s.getQuantity();
        dto.reservedQuantity = s.getReservedQuantity();
        dto.consignedQuantity = s.getConsignedQuantity();
        dto.minStockLevel = s.getMinStockLevel();
        dto.availableQuantity = s.getAvailableQuantity();
        dto.lastUpdated = s.getLastUpdated();
        dto.additionNote = s.getAdditionNote();
        dto.customerName = s.getCustomerName();
        dto.customerPhone = s.getCustomerPhone();
        if (s.getProduct() != null) {
            StockDto.ProductDto p = new StockDto.ProductDto();
            p.id = s.getProduct().getId();
            p.name = s.getProduct().getName();
            p.sku = s.getProduct().getSku();
            dto.product = p;
        }
        if (s.getWarehouse() != null) {
            StockDto.WarehouseDto w = new StockDto.WarehouseDto();
            w.id = s.getWarehouse().getId();
            w.name = s.getWarehouse().getName();
            w.location = s.getWarehouse().getLocation();
            w.warehouseType = s.getWarehouse().getWarehouseType() != null ? s.getWarehouse().getWarehouseType().name() : null;
            dto.warehouse = w;
        }
        return dto;
    }

    private Sort resolveSort(String sortBy, String sortDir) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        if ("quantity".equalsIgnoreCase(sortBy)) {
            return Sort.by(direction, "quantity");
        }
        if ("warehouse".equalsIgnoreCase(sortBy)) {
            return JpaSort.unsafe(direction, "warehouse.name");
        }
        if ("product".equalsIgnoreCase(sortBy)) {
            return JpaSort.unsafe(direction, "product.name");
        }
        return Sort.by(direction, "lastUpdated");
    }
}


