package com.warehouse.controller;

import com.warehouse.entity.Product;
import com.warehouse.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/active")
    public ResponseEntity<List<Product>> getAllActiveProducts() {
        List<Product> products = productService.getAllActiveProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(product -> ResponseEntity.ok(product))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/with-stocks")
    public ResponseEntity<Product> getProductByIdWithStocks(@PathVariable Long id) {
        return productService.getProductByIdWithStocks(id)
                .map(product -> ResponseEntity.ok(product))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<Product> getProductBySku(@PathVariable String sku) {
        return productService.getProductBySku(sku)
                .map(product -> ResponseEntity.ok(product))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<Product>> getProductsByCategory(@PathVariable Long categoryId) {
        try {
            List<Product> products = productService.getProductsByCategory(categoryId);
            return ResponseEntity.ok(products);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<Product>> searchProducts(@RequestParam String name) {
        List<Product> products = productService.searchProductsByName(name);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/filter")
    public ResponseEntity<List<Product>> filterProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long colorId) {
        List<Product> products = productService.filterProductsByBrandAndColor(brandId, colorId);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}/desi")
    public ResponseEntity<?> getProductDesiAndShipping(@PathVariable Long id) {
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
    public ResponseEntity<?> createProduct(@Valid @RequestBody Product product) {
        try {
            Product createdProduct = productService.createProduct(product);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @Valid @RequestBody Product product) {
        try {
            Product updatedProduct = productService.updateProduct(id, product);
            return ResponseEntity.ok(updatedProduct);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        try {
            productService.deleteProduct(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivateProduct(@PathVariable Long id) {
        try {
            productService.deactivateProduct(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<?> activateProduct(@PathVariable Long id) {
        try {
            productService.activateProduct(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
