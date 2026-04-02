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

    public StoreProductController(ProductService productService,
                                   StockService stockService,
                                   ReviewRepository reviewRepository) {
        this.productService = productService;
        this.stockService = stockService;
        this.reviewRepository = reviewRepository;
    }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<PagedResponse<StoreProductDto>> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long colorId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> productPage = productService.getAllActiveProducts(pageable, search, categoryId, brandId, colorId);

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
            .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
            .categorySlug(product.getCategory() != null ? product.getCategory().getSlug() : null)
            .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
            .brandSlug(product.getBrand() != null ? product.getBrand().getSlug() : null)
            .colorName(product.getColor() != null ? product.getColor().getName() : null)
            .stockStatus(stockStatus)
            .availableQuantity(totalAvailable)
            .images(imageDtos)
            .primaryImageUrl(primaryImageUrl)
            .averageRating(avgRating)
            .reviewCount(reviewCount)
            .build();
    }
}
