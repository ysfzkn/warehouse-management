package com.warehouse.controller.store;

import com.warehouse.dto.PagedResponse;
import com.warehouse.dto.store.StoreProductDto;
import com.warehouse.entity.BundleItem;
import com.warehouse.entity.Product;
import com.warehouse.entity.ProductImage;
import com.warehouse.entity.ProductType;
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
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/store/products")
public class StoreProductController {

    /** Default warranty shown on every product when no product/category value is set. */
    private static final int DEFAULT_WARRANTY_MONTHS = 24;

    private final ProductService productService;
    private final StockService stockService;
    private final ReviewRepository reviewRepository;
    private final com.warehouse.repository.StockRepository stockRepository;
    private final com.warehouse.service.StockNotificationService stockNotificationService;
    private final com.warehouse.security.JwtService jwtService;

    public StoreProductController(ProductService productService,
                                   StockService stockService,
                                   ReviewRepository reviewRepository,
                                   com.warehouse.repository.StockRepository stockRepository,
                                   com.warehouse.service.StockNotificationService stockNotificationService,
                                   com.warehouse.security.JwtService jwtService) {
        this.productService = productService;
        this.stockService = stockService;
        this.reviewRepository = reviewRepository;
        this.stockRepository = stockRepository;
        this.stockNotificationService = stockNotificationService;
        this.jwtService = jwtService;
    }

    /**
     * Notify when back in stock: the customer subscribes to be notified when the product is restocked.
     * For a logged-in customer the email is taken from the token; guests send the email in the body.
     */
    @PostMapping("/{id}/notify-me")
    public ResponseEntity<java.util.Map<String, Object>> notifyMe(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body,
            jakarta.servlet.http.HttpServletRequest request) {

        Long customerId = com.warehouse.util.CustomerTokenExtractor.extractCustomerId(request, jwtService);
        String email = body != null ? body.getOrDefault("email", "") : "";
        email = email != null ? email.trim() : "";

        // For a logged-in user we can get the email from the token (optional — the frontend already sends it)
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
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String productType) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        // Optional product-type filter (e.g. ?productType=BUNDLE for sets only)
        ProductType typeFilter = null;
        if (productType != null && !productType.isBlank()) {
            try {
                typeFilter = ProductType.valueOf(productType.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // unknown value → no type filter
            }
        }

        // Support multi-select: brandIds/colorIds take precedence over single brandId/colorId
        java.util.List<Long> effectiveBrandIds = brandIds != null && !brandIds.isEmpty() ? brandIds : (brandId != null ? java.util.List.of(brandId) : null);
        java.util.List<Long> effectiveColorIds = colorIds != null && !colorIds.isEmpty() ? colorIds : (colorId != null ? java.util.List.of(colorId) : null);

        Page<Product> productPage;
        if (effectiveBrandIds != null || effectiveColorIds != null) {
            productPage = productService.getAllActiveProductsMultiFilter(pageable, search, categoryId, effectiveBrandIds, effectiveColorIds);
        } else {
            productPage = productService.getAllActiveProducts(pageable, search, categoryId, null, null, typeFilter);
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

        // ── N+1 prevention: stock + reviews for ALL products on the page in a single query ──
        List<Long> productIds = productPage.getContent().stream()
                .map(Product::getId).collect(Collectors.toList());
        Map<Long, Integer> stockMap = batchStockAvailability(productIds);
        Map<Long, double[]> reviewMap = batchReviewStats(productIds); // [avgRating, count]

        List<StoreProductDto> dtos = productPage.getContent().stream()
            .map(p -> toStoreDto(p, stockMap, reviewMap))
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

    /**
     * Single product (slug detail) — runs its own queries.
     * For lists, use the batch version {@link #toStoreDto(Product, Map, Map)}.
     */
    private StoreProductDto toStoreDto(Product product) {
        int totalAvailable = 0;
        try {
            List<Stock> stocks = stockService.getStocksByProduct(product.getId());
            totalAvailable = stocks.stream().mapToInt(Stock::getAvailableQuantity).sum();
        } catch (Exception ignored) {}
        Double avgRating = null;
        long reviewCount = 0;
        try {
            avgRating = reviewRepository.getAverageRatingByProductId(product.getId());
            reviewCount = reviewRepository.countApprovedByProductId(product.getId());
        } catch (Exception ignored) {}
        return buildDto(product, totalAvailable, avgRating, reviewCount, true);
    }

    /**
     * Batch version — stock and review info is read from maps that were
     * pre-fetched in bulk. No N+1. The list endpoint uses this.
     */
    private StoreProductDto toStoreDto(Product product,
                                        Map<Long, Integer> stockMap,
                                        Map<Long, double[]> reviewMap) {
        int totalAvailable = stockMap.getOrDefault(product.getId(), 0);
        double[] rv = reviewMap.get(product.getId());
        Double avgRating = (rv != null && rv[1] > 0) ? rv[0] : null;
        long reviewCount = (rv != null) ? (long) rv[1] : 0;
        // List view: omit technical specs to keep the payload lean (detail-only).
        return buildDto(product, totalAvailable, avgRating, reviewCount, false);
    }

    /** Page-based total available stock for all products (single query). */
    private Map<Long, Integer> batchStockAvailability(List<Long> productIds) {
        Map<Long, Integer> map = new java.util.HashMap<>();
        if (productIds == null || productIds.isEmpty()) return map;
        try {
            for (Object[] row : stockRepository.sumAvailableByProductIds(productIds)) {
                Long pid = ((Number) row[0]).longValue();
                int avail = row[1] != null ? ((Number) row[1]).intValue() : 0;
                map.put(pid, avail);
            }
        } catch (Exception ignored) {}
        return map;
    }

    /** Review statistics for all products (single query). Value: [avgRating, count]. */
    private Map<Long, double[]> batchReviewStats(List<Long> productIds) {
        Map<Long, double[]> map = new java.util.HashMap<>();
        if (productIds == null || productIds.isEmpty()) return map;
        try {
            for (Object[] row : reviewRepository.getRatingStatsForProducts(productIds)) {
                Long pid = ((Number) row[0]).longValue();
                double avg = row[1] != null ? ((Number) row[1]).doubleValue() : 0;
                double cnt = row[2] != null ? ((Number) row[2]).doubleValue() : 0;
                map.put(pid, new double[]{ avg, cnt });
            }
        } catch (Exception ignored) {}
        return map;
    }

    /** Shared DTO builder — stock and review values are passed as parameters. */
    private StoreProductDto buildDto(Product product, int totalAvailable, Double avgRating, long reviewCount, boolean includeSpecs) {
        boolean isBundle = product.getProductType() == ProductType.BUNDLE;

        // For a bundle, availability is DERIVED from its members (no stock of its own):
        // how many complete sets can be assembled = min over members of (memberAvailable / qtyInSet).
        Integer bundleItemCount = null;
        java.math.BigDecimal membersOriginalTotal = null;
        List<StoreProductDto.StoreBundleItem> bundleMemberDtos = null;
        int effectiveAvailable = totalAvailable;
        if (isBundle) {
            List<BundleItem> members = safe(product::getBundleItems);
            if (members != null && !members.isEmpty()) {
                bundleItemCount = members.size();
                int minSets = Integer.MAX_VALUE;
                java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
                if (includeSpecs) bundleMemberDtos = new java.util.ArrayList<>();
                for (BundleItem bi : members) {
                    Product m = bi.getProduct();
                    if (m == null) continue;
                    int qty = bi.getQuantity() != null && bi.getQuantity() > 0 ? bi.getQuantity() : 1;
                    int memberAvail = memberAvailable(m.getId());
                    minSets = Math.min(minSets, memberAvail / qty);
                    // Gift members ship with the set (so they count toward availability)
                    // but are free → excluded from the members' price total.
                    if (!bi.isGift()) {
                        java.math.BigDecimal mPrice = effectivePrice(m);
                        if (mPrice != null) sum = sum.add(mPrice.multiply(java.math.BigDecimal.valueOf(qty)));
                    }
                    if (includeSpecs) {
                        bundleMemberDtos.add(StoreProductDto.StoreBundleItem.builder()
                            .productId(m.getId())
                            .slug(m.getSlug())
                            .name(m.getName())
                            .primaryImageUrl(primaryImageUrlFor(m))
                            .quantity(qty)
                            .price(m.getPrice())
                            .salePrice(m.getSalePrice())
                            .stockStatus(memberAvail > 0 ? "IN_STOCK" : "OUT_OF_STOCK")
                            .gift(bi.isGift())
                            .build());
                    }
                }
                effectiveAvailable = (minSets == Integer.MAX_VALUE) ? 0 : minSets;
                membersOriginalTotal = sum;
            } else {
                effectiveAvailable = 0;
            }
        }

        String stockStatus = effectiveAvailable > 0 ? "IN_STOCK" : "OUT_OF_STOCK";
        if (effectiveAvailable > 0 && effectiveAvailable <= 5) {
            stockStatus = "LOW_STOCK";
        }
        totalAvailable = effectiveAvailable;

        // Effective warranty: product → category → parent category (first non-null wins).
        Integer warrantyMonths = product.getWarrantyMonths();
        String warrantyText = product.getWarrantyText();
        if (warrantyMonths == null && warrantyText == null) {
            com.warehouse.entity.Category cat = safe(product::getCategory);
            if (cat != null && (cat.getWarrantyMonths() != null || cat.getWarrantyText() != null)) {
                warrantyMonths = cat.getWarrantyMonths();
                warrantyText = cat.getWarrantyText();
            } else if (cat != null) {
                com.warehouse.entity.Category parent = safe(cat::getParent);
                if (parent != null) {
                    warrantyMonths = parent.getWarrantyMonths();
                    warrantyText = parent.getWarrantyText();
                }
            }
        }
        // Site-wide default: every product shows at least this warranty unless a
        // product/category value overrides it.
        if (warrantyMonths == null && (warrantyText == null || warrantyText.isBlank())) {
            warrantyMonths = DEFAULT_WARRANTY_MONTHS;
        }

        // Images — convert disk paths to accessible HTTP URLs.
        // Gallery images (slot == null) feed the normal gallery + primary image; the
        // three dedicated slot images (slot 1/2/3) feed the "triple image set" block.
        List<StoreProductDto.ImageDto> imageDtos = List.of();
        List<StoreProductDto.ImageDto> setImageDtos = List.of();
        String primaryImageUrl = null;
        if (product.getImages() != null) {
            try {
                java.util.function.Function<ProductImage, StoreProductDto.ImageDto> toDto = img ->
                    StoreProductDto.ImageDto.builder()
                        .id(img.getId())
                        .url("/api/admin/products/images/" + img.getId() + "/view")
                        .thumbnailUrl("/api/admin/products/images/" + img.getId() + "/view?thumbnail=true")
                        .width(img.getWidth())
                        .height(img.getHeight())
                        .sortOrder(img.getSortOrder())
                        .primary(img.isPrimary())
                        .slot(img.getSlot())
                        .build();
                imageDtos = product.getImages().stream()
                    .filter(com.warehouse.util.ProductImageUtil::isGalleryImage)
                    .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                    .map(toDto)
                    .collect(Collectors.toList());
                setImageDtos = product.getImages().stream()
                    .filter(img -> img.getSlot() != null)
                    .sorted(java.util.Comparator.comparingInt(ProductImage::getSlot))
                    .map(toDto)
                    .collect(Collectors.toList());
                primaryImageUrl = com.warehouse.util.ProductImageUtil.displayCover(product.getImages())
                    .map(img -> "/api/admin/products/images/" + img.getId() + "/view?thumbnail=true")
                    .orElse(null);
            } catch (Exception ignored) {}
        }

        // Color variants — detail view only: the same product in other colors, as a swatch row.
        // Includes the current product so the frontend can render and highlight it.
        List<StoreProductDto.ColorVariantDto> colorVariants = null;
        if (includeSpecs && product.getVariantGroupId() != null) {
            List<Product> members = productService.getActiveVariantSiblings(product.getVariantGroupId());
            if (members != null && members.size() > 1) {
                List<Long> memberIds = members.stream().map(Product::getId).collect(Collectors.toList());
                Map<Long, Integer> memberStock = batchStockAvailability(memberIds);
                colorVariants = members.stream()
                    .map(m -> StoreProductDto.ColorVariantDto.builder()
                        .productId(m.getId())
                        .slug(m.getSlug())
                        .colorName(safe(() -> m.getColor() != null ? m.getColor().getName() : null))
                        .colorHexCode(safe(() -> m.getColor() != null ? m.getColor().getHexCode() : null))
                        .primaryImageUrl(primaryImageUrlFor(m))
                        .inStock(memberStock.getOrDefault(m.getId(), 0) > 0)
                        .current(m.getId().equals(product.getId()))
                        .build())
                    .collect(Collectors.toList());
            }
        }

        // Reviews — avgRating + reviewCount came in as parameters (batch or single)
        return StoreProductDto.builder()
            .id(product.getId())
            .slug(product.getSlug())
            .name(product.getName())
            .description(product.getDescription())
            .shortDescription(product.getShortDescription())
            .warrantyMonths(warrantyMonths)
            .warrantyText(warrantyText)
            .technicalSpecs(includeSpecs ? product.getTechnicalSpecs() : null)
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
            .colorHexCode(safe(() -> product.getColor() != null ? product.getColor().getHexCode() : null))
            .colorVariants(colorVariants)
            .stockStatus(stockStatus)
            .availableQuantity(totalAvailable)
            .images(imageDtos)
            .setImages(setImageDtos)
            .primaryImageUrl(primaryImageUrl)
            .averageRating(avgRating)
            .reviewCount(reviewCount)
            .productType(product.getProductType() != null ? product.getProductType().name() : ProductType.SIMPLE.name())
            .bundleItemCount(bundleItemCount)
            .membersOriginalTotal(membersOriginalTotal)
            .bundleItems(bundleMemberDtos)
            .build();
    }

    /** Total available units of a member product across all warehouses. */
    private int memberAvailable(Long productId) {
        try {
            return stockService.getStocksByProduct(productId).stream()
                .mapToInt(Stock::getAvailableQuantity).sum();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Effective (sale-aware) unit price of a product. */
    private static java.math.BigDecimal effectivePrice(Product p) {
        java.math.BigDecimal sale = p.getSalePrice();
        if (sale != null && sale.compareTo(java.math.BigDecimal.ZERO) > 0) return sale;
        return p.getPrice();
    }

    /** Thumbnail URL of a product's primary image (or first by sort order). */
    private String primaryImageUrlFor(Product p) {
        try {
            return com.warehouse.util.ProductImageUtil.displayCover(p.getImages())
                .map(img -> "/api/admin/products/images/" + img.getId() + "/view?thumbnail=true")
                .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T safe(java.util.function.Supplier<T> supplier) {
        try { return supplier.get(); } catch (Exception e) { return null; }
    }
}
