package com.warehouse.controller;

import com.warehouse.dto.PagedResponse;
import com.warehouse.entity.StockEvent;
import com.warehouse.enums.StockEventSource;
import com.warehouse.enums.StockEventType;
import com.warehouse.repository.StockEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/stock-events")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStockEventController {

    private final StockEventRepository stockEventRepository;
    private final com.warehouse.repository.ProductRepository productRepository;

    public AdminStockEventController(StockEventRepository stockEventRepository,
                                      com.warehouse.repository.ProductRepository productRepository) {
        this.stockEventRepository = stockEventRepository;
        this.productRepository = productRepository;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<Map<String, Object>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String search) {

        StockEventSource sourceEnum =
                com.warehouse.util.EnumParams.parse(StockEventSource.class, source, "Kaynak");
        StockEventType typeEnum =
                com.warehouse.util.EnumParams.parse(StockEventType.class, eventType, "Hareket tipi");
        LocalDateTime start = startDate != null && !startDate.isBlank() ? LocalDate.parse(startDate).atStartOfDay() : null;
        LocalDateTime end = endDate != null && !endDate.isBlank() ? LocalDate.parse(endDate).plusDays(1).atStartOfDay() : null;
        String searchTerm = search != null && !search.isBlank() ? search : null;

        Page<StockEvent> result = stockEventRepository.findAll(
            com.warehouse.repository.StockEventSpecifications.withFilters(sourceEnum, typeEnum, productId, start, end, searchTerm),
            PageRequest.of(page, size, Sort.by("createdAt").descending()));

        // Enrich with product names + SKUs
        Set<Long> productIds = result.getContent().stream()
            .map(StockEvent::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> productNames = new HashMap<>();
        Map<Long, String> productSkus = new HashMap<>();
        if (!productIds.isEmpty()) {
            productRepository.findAllById(productIds).forEach(p -> {
                productNames.put(p.getId(), p.getName());
                productSkus.put(p.getId(), p.getSku());
            });
        }

        List<Map<String, Object>> dtos = result.getContent().stream().map(e -> {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", e.getId());
            dto.put("stockId", e.getStockId());
            dto.put("productId", e.getProductId());
            dto.put("productName", productNames.getOrDefault(e.getProductId(), ""));
            dto.put("productSku", productSkus.get(e.getProductId()));
            dto.put("eventType", e.getEventType().name());
            dto.put("oldValue", e.getOldValue());
            dto.put("newValue", e.getNewValue());
            dto.put("source", e.getSource().name());
            dto.put("sourceDetail", e.getSourceDetail());
            dto.put("orderNumber", e.getOrderNumber());
            dto.put("createdAt", e.getCreatedAt());
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(new PagedResponse<>(dtos, result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast()));
    }

    @GetMapping("/sources")
    public ResponseEntity<List<String>> getSources() {
        return ResponseEntity.ok(Arrays.stream(StockEventSource.values()).map(Enum::name).collect(Collectors.toList()));
    }

    @GetMapping("/types")
    public ResponseEntity<List<String>> getTypes() {
        return ResponseEntity.ok(Arrays.stream(StockEventType.values()).map(Enum::name).collect(Collectors.toList()));
    }
}
