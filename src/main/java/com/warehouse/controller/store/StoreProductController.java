package com.warehouse.controller.store;

import com.warehouse.dto.PagedResponse;
import com.warehouse.dto.store.StoreProductDto;
import com.warehouse.entity.Product;
import com.warehouse.entity.ProductImage;
import com.warehouse.entity.Stock;
import com.warehouse.repository.ReviewRepository;
import com.warehouse.service.ProductService;
import com.warehouse.service.StockService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/store/products")
public class StoreProductController {

    private final ProductService productService;
    private final StockService stockService;
    private final ReviewRepository reviewRepository;
    private final com.warehouse.service.StockNotificationService stockNotificationService;
    private final com.warehouse.security.JwtService jwtService;

    public StoreProductController(ProductService productService,
                                   StockService stockService,
                                   ReviewRepository reviewRepository,
                                   com.warehouse.service.StockNotificationService stockNotificationService,
                                   com.warehouse.security.JwtService jwtService) {
        this.productService = productService;
        this.stockService = stockService;
        this.reviewRepository = reviewRepository;
        this.stockNotificationService = stockNotificationService;
        this.jwtService = jwtService;
    }

    /**
     * Stokta yoksa bildir: müşteri ürün tekrar stoğa girdiğinde haber almak için abone olur.
     * Giriş yapmış müşteri için token'dan email alınır, misafirler body'de email gönderir.
     */
    @PostMapping("/{id}/notify-me")
    public ResponseEntity<java.util.Map<String, Object>> notifyMe(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body,
            jakarta.servlet.http.HttpServletRequest request) {

        Long customerId = com.warehouse.util.CustomerTokenExtractor.extractCustomerId(request, jwtService);
        String email = body != null ? body.getOrDefault("email", "") : "";
        email = email != null ? email.trim() : "";

        // Giriş yapmış kullanıcıda token'dan email'i alabiliriz (opsiyonel — frontend zaten gönderir)
        if (email.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "E-posta adresi zorunludur."));
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Geçerli bir e-posta adresi giriniz."));
        }

        try {
            boolean newSubscription = stockNotificationService.subscribe(id, email, customerId);
            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "alreadySubscribed", !newSubscription,
                "message", newSubscription
                    ? "Ürün stoklara geldiğinde size bildireceğiz."
                    : "Bu ürün için zaten bir bildirim aboneliğiniz var."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<PagedResponse<StoreProductDto>> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long colorId,
            @RequestParam(required = false) java.util.List<Long> brandIds,
            @RequestParam(required = false) java.util.List<Long> colorIds,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        // Support multi-select: brandIds/colorIds take precedence over single brandId/colorId
        java.util.List<Long> effectiveBrandIds = brandIds != null && !brandIds.isEmpty() ? brandIds : (brandId != null ? java.util.List.of(brandId) : null);
        java.util.List<Long> effectiveColorIds = colorIds != null && !colorIds.isEmpty() ? colorIds : (colorId != null ? java.util.List.of(colorId) : null);

        Page<Product> productPage;
        if (effectiveBrandIds != null || effectiveColorIds != null) {
            productPage = productService.getAllActiveProductsMultiFilter(pageable, search, categoryId, effectiveBrandIds, effectiveColorIds);
        } else {
            productPage = productService.getAllActiveProducts(pageable, search, categoryId, null, null);
        }

        // Apply price filter in-memory (simpler than complex JPA spec for now)
        if (minPrice != null || maxPrice != null) {
            final java.math.BigDecimal min = minPrice != null ? minPrice : java.math.BigDecimal.ZERO;
            final java.math.BigDecimal max = maxPrice != null ? maxPrice : new java.math.BigDecimal("999999999");
            java.util.List<Product> filtered = productPage.getContent().stream()
                .filter(p -> {
                    java.math.BigDecimal effectivePrice = p.getSalePrice() != null && p.getSalePrice().compareTo(java.math.BigDecimal.ZERO) > 0 ? p.getSalePrice() : p.getPrice();
                    return effectivePrice != null && effectivePrice.compareTo(min) >= 0 && effectivePrice.compareTo(max) <= 0;
                }).collect(java.util.stream.Collectors.toList());
            productPage = new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
        }

        List<StoreProductDto> dtos = productPage.getContent().stream()
            .map(this::toStoreDto)
            .collect(Collectors.toList());

        PagedResponse<StoreProductDto> response = new PagedResponse<>(
            dtos,
            productPage.getNumber(),
            productPage.getSize(),
            productPage.getTotalElements(),
            productPage.getTotalPages(),
            productPage.isFirst(),
            productPage.isLast()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{slug}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<StoreProductDto> getProductBySlug(@PathVariable String slug) {
        Product product = productService.getProductBySlug(slug);
        return ResponseEntity.ok(toStoreDto(product));
    }

    private StoreProductDto toStoreDto(Product product) {
        // Calculate stock availability
        int totalAvailable = 0;
        try {
            List<Stock> stocks = stockService.getStocksByProduct(product.getId());
            totalAvailable = stocks.stream()
                .mapToInt(Stock::getAvailableQuantity)
                .sum();
        } catch (Exception ignored) {}

        String stockStatus = totalAvailable > 0 ? "IN_STOCK" : "OUT_OF_STOCK";
        if (totalAvailable > 0 && totalAvailable <= 5) {
            stockStatus = "LOW_STOCK";
        }

        // Images — convert disk paths to accessible HTTP URLs
        List<StoreProductDto.ImageDto> imageDtos = List.of();
        String primaryImageUrl = null;
        if (product.getImages() != null) {
            try {
                imageDtos = product.getImages().stream()
                    .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                    .map(img -> StoreProductDto.ImageDto.builder()
                        .id(img.getId())
                        .url("/api/admin/products/images/" + img.getId() + "/view")
                        .thumbnailUrl("/api/admin/products/images/" + img.getId() + "/view?thumbnail=true")
                        .width(img.getWidth())
                        .height(img.getHeight())
                        .sortOrder(img.getSortOrder())
                        .primary(img.isPrimary())
                        .build())
                    .collect(Collectors.toList());
                primaryImageUrl = product.getImages().stream()
                    .filter(ProductImage::isPrimary)
                    .findFirst()
                    .or(() -> product.getImages().stream().findFirst())
                    .map(img -> "/api/admin/products/images/" + img.getId() + "/view?thumbnail=true")
                    .orElse(null);
            } catch (Exception ignored) {}
        }

        // Reviews
        Double avgRating = null;
        long reviewCount = 0;
        try {
            avgRating = reviewRepository.getAverageRatingByProductId(product.getId());
            reviewCount = reviewRepository.countApprovedByProductId(product.getId());
        } catch (Exception ignored) {}

        return StoreProductDto.builder()
            .id(product.getId())
            .slug(product.getSlug())
            .name(product.getName())
            .description(product.getDescription())
            .shortDescription(product.getShortDescription())
            .sku(product.getSku())
            .price(product.getPrice())
            .salePrice(product.getSalePrice())
            .saleStart(product.getSaleStart())
            .saleEnd(product.getSaleEnd())
            .vatRate(product.getVatRate())
            .sctRate(product.getSctRate())
            .shippingRate(product.getShippingRate())
            .weight(product.getWeight())
            .lengthCm(product.getLengthCm())
            .widthCm(product.getWidthCm())
            .heightCm(product.getHeightCm())
            .featured(product.isFeatured())
            .isNew(product.isNew())
            .categoryName(safe(() -> product.getCategory() != null ? product.getCategory().getName() : null))
            .categorySlug(safe(() -> product.getCategory() != null ? product.getCategory().getSlug() : null))
            .brandName(safe(() -> product.getBrand() != null ? product.getBrand().getName() : null))
            .brandSlug(safe(() -> product.getBrand() != null ? product.getBrand().getSlug() : null))
            .colorName(safe(() -> product.getColor() != null ? product.getColor().getName() : null))
            .stockStatus(stockStatus)
            .availableQuantity(totalAvailable)
            .images(imageDtos)
            .primaryImageUrl(primaryImageUrl)
            .averageRating(avgRating)
            .reviewCount(reviewCount)
            .build();
    }

    private <T> T safe(java.util.function.Supplier<T> supplier) {
        try { return supplier.get(); } catch (Exception e) { return null; }
    }
}
