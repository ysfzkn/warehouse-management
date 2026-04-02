package com.warehouse.controller;

import com.warehouse.entity.Product;
import com.warehouse.dto.BulkDeleteResponse;
import com.warehouse.dto.BulkPriceUpdateRequest;
import com.warehouse.dto.ProductDto;
import com.warehouse.service.ProductService;
import com.warehouse.service.ProductImageService;
import com.warehouse.entity.ProductImage;
import com.warehouse.service.StockService;
import com.warehouse.service.PhotoStorageService;
import com.warehouse.service.AdminSecurityService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import com.warehouse.dto.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/products")
public class ProductController {

    private final ProductService productService;
    private final StockService stockService;
    private final ProductImageService productImageService;
    private final PhotoStorageService photoStorageService;
    private final AdminSecurityService adminSecurityService;

    @Autowired
    public ProductController(ProductService productService,
                             StockService stockService,
                             ProductImageService productImageService,
                             PhotoStorageService photoStorageService,
                             AdminSecurityService adminSecurityService) {
        this.productService = productService;
        this.stockService = stockService;
        this.productImageService = productImageService;
        this.photoStorageService = photoStorageService;
        this.adminSecurityService = adminSecurityService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAllProducts(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false, defaultValue = "updatedAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long colorId) {
        if (page != null && size != null) {
            // Paginated response
            int safePage = Math.max(0, page);
            int safeSize = Math.max(1, Math.min(size, 250));
            Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
            Pageable pageable = PageRequest.of(safePage, safeSize, sort);
            Page<Product> productPage = productService.getAllProducts(pageable, search, categoryId, brandId, colorId);
            List<ProductDto> content = mapProductsWithTotals(productPage.getContent());
            PagedResponse<ProductDto> response = new PagedResponse<>(
                    content,
                    productPage.getNumber(),
                    productPage.getSize(),
                    productPage.getTotalElements(),
                    productPage.getTotalPages(),
                    productPage.isFirst(),
                    productPage.isLast()
            );
            return ResponseEntity.ok(response);
        } else {
            // Non-paginated response (backward compatibility)
            Page<Product> productPage = productService.getAllProducts(Pageable.unpaged(), search, categoryId, brandId, colorId);
            return ResponseEntity.ok(mapProductsWithTotals(productPage.getContent()));
        }
    }

    @GetMapping("/active")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductDto>> getAllActiveProducts() {
        List<Product> products = productService.getAllActiveProducts();
        return ResponseEntity.ok(mapProductsWithTotals(products));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProductDto> getProductById(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(product -> ResponseEntity.ok(toDtoWithTotal(product)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/with-stocks")
    @Transactional(readOnly = true)
    public ResponseEntity<ProductDto> getProductByIdWithStocks(@PathVariable Long id) {
        return productService.getProductByIdWithStocks(id)
                .map(product -> ResponseEntity.ok(toDtoWithTotal(product)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sku/{sku}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProductDto> getProductBySku(@PathVariable String sku) {
        return productService.getProductBySku(sku)
                .map(product -> ResponseEntity.ok(toDtoWithTotal(product)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{categoryId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductDto>> getProductsByCategory(@PathVariable Long categoryId) {
        List<Product> products = productService.getProductsByCategory(categoryId);
        return ResponseEntity.ok(mapProductsWithTotals(products));
    }

    @GetMapping("/search")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductDto>> searchProducts(@RequestParam String name) {
        List<Product> products = productService.searchProductsByName(name);
        return ResponseEntity.ok(mapProductsWithTotals(products));
    }

    @GetMapping("/filter")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductDto>> filterProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long colorId) {
        List<Product> products = productService.filterProductsByBrandAndColor(brandId, colorId);
        return ResponseEntity.ok(mapProductsWithTotals(products));
    }

    @GetMapping("/{id}/desi")
    public ResponseEntity<?> getProductDesi(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(p -> {
                    double w = p.getWidthCm() != null ? p.getWidthCm() : 0.0;
                    double l = p.getLengthCm() != null ? p.getLengthCm() : 0.0;
                    double h = p.getHeightCm() != null ? p.getHeightCm() : 0.0;
                    double desi = (h * w * l) / 3000.0;
                    java.math.BigDecimal rate = p.getShippingRate() != null ? p.getShippingRate() : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal shippingCost = rate.multiply(java.math.BigDecimal.valueOf(desi));
                    java.util.Map<String, Object> resp = new java.util.HashMap<>();
                    resp.put("productId", p.getId());
                    resp.put("name", p.getName());
                    resp.put("sku", p.getSku());
                    resp.put("widthCm", w);
                    resp.put("lengthCm", l);
                    resp.put("heightCm", h);
                    resp.put("desi", desi);
                    resp.put("shippingRate", rate);
                    resp.put("shippingCost", shippingCost);
                    return ResponseEntity.ok(resp);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/images")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getProductImages(@PathVariable Long id) {
        List<ProductImage> images = productImageService.getImagesForProduct(id);
        List<Map<String, Object>> dtos = images.stream().map(img -> {
            Map<String, Object> dto = new java.util.LinkedHashMap<>();
            dto.put("id", img.getId());
            dto.put("fileName", img.getFileName());
            dto.put("relativePath", img.getRelativePath());
            dto.put("thumbnailPath", img.getThumbnailPath());
            dto.put("sortOrder", img.getSortOrder());
            dto.put("primary", img.isPrimary());
            dto.put("contentType", img.getContentType());
            dto.put("sizeBytes", img.getSizeBytes());
            dto.put("width", img.getWidth());
            dto.put("height", img.getHeight());
            return dto;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/{id}/images")
    public ResponseEntity<Map<String, Object>> uploadProductImage(@PathVariable Long id,
                                                           @RequestParam("file") MultipartFile file,
                                                           @RequestParam(name = "primary", defaultValue = "false") boolean primary) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Dosya boş."));
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of("message", "Sadece görsel dosyaları yüklenebilir."));
        }
        try {
            ProductImage image = productImageService.addImageToProduct(
                    id,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getInputStream(),
                    primary
            );
            // Return simple DTO to avoid LazyInitializationException on product.category
            Map<String, Object> dto = new java.util.LinkedHashMap<>();
            dto.put("id", image.getId());
            dto.put("fileName", image.getFileName());
            dto.put("sortOrder", image.getSortOrder());
            dto.put("primary", image.isPrimary());
            dto.put("contentType", image.getContentType());
            dto.put("sizeBytes", image.getSizeBytes());
            dto.put("width", image.getWidth());
            dto.put("height", image.getHeight());
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Görsel yüklenemedi: " + e.getMessage()));
        }
    }

    @PutMapping("/images/{imageId}/set-primary")
    public ResponseEntity<Map<String, String>> setImagePrimary(@PathVariable Long imageId) {
        productImageService.setPrimaryImage(imageId);
        return ResponseEntity.ok(Map.of("message", "Birincil görsel güncellendi."));
    }

    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<Void> deleteProductImage(@PathVariable Long imageId) {
        productImageService.deleteImage(imageId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/images/{imageId}/view")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> viewProductImage(@PathVariable Long imageId,
                                                   @RequestParam(name = "thumbnail", defaultValue = "false") boolean thumbnail) {
        ProductImage image = productImageService.getImageOrThrow(imageId);
        String path = thumbnail ? image.getThumbnailPath() : image.getRelativePath();
        try (java.io.InputStream is = thumbnail
                ? photoStorageService.openThumbnailStream(path)
                : photoStorageService.openPhotoStream(path)) {
            byte[] bytes = is.readAllBytes();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(image.getContentType()));
            headers.setContentLength(bytes.length);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + image.getFileName() + "\"");
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping
    public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody Product product) {
        Product createdProduct = productService.createProduct(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(createdProduct));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable Long id, @Valid @RequestBody Product product) {
        Product updatedProduct = productService.updateProduct(id, product);
        return ResponseEntity.ok(toDto(updatedProduct));
    }

    @PutMapping("/bulk-price")
    public ResponseEntity<?> bulkPrice(@Valid @RequestBody BulkPriceUpdateRequest request) {
        int affected = productService.bulkAdjustPrices(request);
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("affected", affected);
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id,
                                              @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<BulkDeleteResponse> deleteProducts(@RequestBody List<Long> ids,
                                                             @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        BulkDeleteResponse response = productService.deleteProducts(ids);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateProduct(@PathVariable Long id) {
        productService.deactivateProduct(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<Void> activateProduct(@PathVariable Long id) {
        productService.activateProduct(id);
        return ResponseEntity.ok().build();
    }

    private ProductDto toDto(Product p) {
        ProductDto dto = new ProductDto();
        dto.id = p.getId();
        dto.name = p.getName();
        dto.sku = p.getSku();
        dto.description = p.getDescription();
        dto.price = p.getPrice();
        dto.active = p.isActive();
        dto.weight = p.getWeight();
        dto.dimensions = p.getDimensions();
        dto.lengthCm = p.getLengthCm();
        dto.widthCm = p.getWidthCm();
        dto.heightCm = p.getHeightCm();
        dto.shippingRate = p.getShippingRate();
        dto.vatRate = p.getVatRate();
        dto.sctRate = p.getSctRate();
        dto.createdAt = p.getCreatedAt();
        dto.updatedAt = p.getUpdatedAt();
        if (p.getCategory() != null) {
            dto.categoryId = p.getCategory().getId();
            dto.categoryName = p.getCategory().getName();
            dto.categoryParentId = p.getCategory().getParent() != null ? p.getCategory().getParent().getId() : null;
            dto.categoryParentName = p.getCategory().getParent() != null ? p.getCategory().getParent().getName() : null;

            ProductDto.CategoryInfo categoryInfo = new ProductDto.CategoryInfo();
            categoryInfo.id = p.getCategory().getId();
            categoryInfo.name = p.getCategory().getName();
            if (p.getCategory().getParent() != null) {
                ProductDto.ParentInfo parentInfo = new ProductDto.ParentInfo();
                parentInfo.id = p.getCategory().getParent().getId();
                parentInfo.name = p.getCategory().getParent().getName();
                categoryInfo.parent = parentInfo;
            }
            dto.category = categoryInfo;
        }
        if (p.getBrand() != null) {
            dto.brandId = p.getBrand().getId();
            dto.brandName = p.getBrand().getName();
            ProductDto.BrandInfo brandInfo = new ProductDto.BrandInfo();
            brandInfo.id = p.getBrand().getId();
            brandInfo.name = p.getBrand().getName();
            dto.brand = brandInfo;
        }
        if (p.getColor() != null) {
            dto.colorId = p.getColor().getId();
            dto.colorName = p.getColor().getName();
            ProductDto.ColorInfo colorInfo = new ProductDto.ColorInfo();
            colorInfo.id = p.getColor().getId();
            colorInfo.name = p.getColor().getName();
            colorInfo.hexCode = p.getColor().getHexCode();
            dto.color = colorInfo;
        }
        return dto;
    }

    private List<ProductDto> mapProductsWithTotals(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        List<Long> ids = products.stream()
                .map(Product::getId)
                .toList();
        Map<Long, Long> totals = stockService.getTotalQuantitiesByProductIds(ids);
        return products.stream()
                .map(product -> {
                    ProductDto dto = toDto(product);
                    dto.totalQuantity = totals.getOrDefault(product.getId(), 0L);
                    return dto;
                })
                .toList();
    }

    private ProductDto toDtoWithTotal(Product product) {
        ProductDto dto = toDto(product);
        dto.totalQuantity = stockService.getTotalQuantityByProduct(product.getId());
        return dto;
    }
}
