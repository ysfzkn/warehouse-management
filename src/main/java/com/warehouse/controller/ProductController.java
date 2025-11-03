package com.warehouse.controller;

import com.warehouse.entity.Product;
import com.warehouse.dto.BulkPriceUpdateRequest;
import com.warehouse.dto.ProductDto;
import com.warehouse.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductService productService;

    @Autowired
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductDto>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(products.stream().map(this::toDto).toList());
    }

    @GetMapping("/active")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductDto>> getAllActiveProducts() {
        List<Product> products = productService.getAllActiveProducts();
        return ResponseEntity.ok(products.stream().map(this::toDto).toList());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProductDto> getProductById(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(product -> ResponseEntity.ok(toDto(product)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/with-stocks")
    @Transactional(readOnly = true)
    public ResponseEntity<ProductDto> getProductByIdWithStocks(@PathVariable Long id) {
        return productService.getProductByIdWithStocks(id)
                .map(product -> ResponseEntity.ok(toDto(product)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sku/{sku}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProductDto> getProductBySku(@PathVariable String sku) {
        return productService.getProductBySku(sku)
                .map(product -> ResponseEntity.ok(toDto(product)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{categoryId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductDto>> getProductsByCategory(@PathVariable Long categoryId) {
        List<Product> products = productService.getProductsByCategory(categoryId);
        return ResponseEntity.ok(products.stream().map(this::toDto).toList());
    }

    @GetMapping("/search")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductDto>> searchProducts(@RequestParam String name) {
        List<Product> products = productService.searchProductsByName(name);
        return ResponseEntity.ok(products.stream().map(this::toDto).toList());
    }

    @GetMapping("/filter")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductDto>> filterProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long colorId) {
        List<Product> products = productService.filterProductsByBrandAndColor(brandId, colorId);
        return ResponseEntity.ok(products.stream().map(this::toDto).toList());
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

    @PostMapping
    public ResponseEntity<Product> createProduct(@Valid @RequestBody Product product) {
        Product createdProduct = productService.createProduct(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable Long id, @Valid @RequestBody Product product) {
        Product updatedProduct = productService.updateProduct(id, product);
        return ResponseEntity.ok(updatedProduct);
    }

    @PutMapping("/bulk-price")
    public ResponseEntity<?> bulkPrice(@Valid @RequestBody BulkPriceUpdateRequest request) {
        int affected = productService.bulkAdjustPrices(request);
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("affected", affected);
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
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
        if (p.getCategory() != null) {
            dto.categoryId = p.getCategory().getId();
            dto.categoryName = p.getCategory().getName();
        }
        if (p.getBrand() != null) {
            dto.brandId = p.getBrand().getId();
            dto.brandName = p.getBrand().getName();
        }
        if (p.getColor() != null) {
            dto.colorId = p.getColor().getId();
            dto.colorName = p.getColor().getName();
        }
        return dto;
    }
}
