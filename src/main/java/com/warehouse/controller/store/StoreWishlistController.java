package com.warehouse.controller.store;

import com.warehouse.entity.Customer;
import com.warehouse.entity.Product;
import com.warehouse.entity.Wishlist;
import com.warehouse.repository.CustomerRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.WishlistRepository;
import com.warehouse.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/store/wishlist")
@Transactional(readOnly = true)
public class StoreWishlistController {

    private final WishlistRepository wishlistRepo;
    private final CustomerRepository customerRepo;
    private final ProductRepository productRepo;
    private final JwtService jwtService;

    public StoreWishlistController(WishlistRepository wishlistRepo, CustomerRepository customerRepo,
                                    ProductRepository productRepo, JwtService jwtService) {
        this.wishlistRepo = wishlistRepo;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
        this.jwtService = jwtService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(HttpServletRequest request) {
        Long cid = extractCustomerId(request);
        if (cid == null) return ResponseEntity.ok(java.util.List.of());
        List<Map<String, Object>> dtos = wishlistRepo.findByCustomerId(cid).stream().map(w -> {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", w.getId());
            dto.put("productId", w.getProduct().getId());
            dto.put("productName", w.getProduct().getName());
            dto.put("productSlug", w.getProduct().getSlug());
            dto.put("productPrice", w.getProduct().getPrice());
            dto.put("productSalePrice", w.getProduct().getSalePrice());
            dto.put("productSku", w.getProduct().getSku());
            dto.put("createdAt", w.getCreatedAt());
            // Primary image
            String imageUrl = null;
            try {
                var primary = com.warehouse.util.ProductImageUtil.displayCover(w.getProduct().getImages()).orElse(null);
                if (primary != null) {
                    imageUrl = "/api/admin/products/images/" + primary.getId() + "/view?thumbnail=true";
                }
            } catch (Exception ignored) {}
            dto.put("imageUrl", imageUrl);
            return dto;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/{productId}")
    @Transactional
    public ResponseEntity<?> add(HttpServletRequest request, @PathVariable Long productId) {
        Long cid = extractCustomerId(request);
        if (cid == null) return ResponseEntity.badRequest().body(Map.of("message", "Giriş yapmanız gerekiyor."));
        if (wishlistRepo.existsByCustomerIdAndProductId(cid, productId)) {
            return ResponseEntity.ok(Map.of("message", "Ürün zaten favorilerde."));
        }
        Customer customer = customerRepo.findById(cid).orElse(null);
        Product product = productRepo.findById(productId).orElse(null);
        if (customer == null || product == null) return ResponseEntity.notFound().build();
        Wishlist w = new Wishlist();
        w.setCustomer(customer);
        w.setProduct(product);
        wishlistRepo.save(w);
        return ResponseEntity.ok(Map.of("message", "Ürün favorilere eklendi."));
    }

    @DeleteMapping("/{productId}")
    @Transactional
    public ResponseEntity<?> remove(HttpServletRequest request, @PathVariable Long productId) {
        Long cid = extractCustomerId(request);
        if (cid == null) return ResponseEntity.badRequest().body(Map.of("message", "Giriş yapmanız gerekiyor."));
        wishlistRepo.deleteByCustomerIdAndProductId(cid, productId);
        return ResponseEntity.ok(Map.of("message", "Ürün favorilerden çıkarıldı."));
    }

    private Long extractCustomerId(HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
            else if (request.getCookies() != null) {
                for (var c : request.getCookies()) { if ("access_token".equals(c.getName())) { token = c.getValue(); break; } }
            }
            return token != null ? jwtService.extractCustomerId(token) : null;
        } catch (Exception e) { return null; }
    }
}
