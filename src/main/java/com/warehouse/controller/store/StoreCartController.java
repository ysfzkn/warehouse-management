package com.warehouse.controller.store;

import com.warehouse.dto.store.*;
import com.warehouse.service.CartService;
import com.warehouse.util.CustomerTokenExtractor;
import com.warehouse.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store/cart")
public class StoreCartController {

    private final CartService cartService;
    private final JwtService jwtService;

    public StoreCartController(CartService cartService, JwtService jwtService) {
        this.cartService = cartService;
        this.jwtService = jwtService;
    }

    @GetMapping
    public ResponseEntity<CartDto> getCart(HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        String sessionId = request.getHeader("X-Session-Id");
        return ResponseEntity.ok(cartService.getCart(customerId, sessionId));
    }

    @PostMapping("/items")
    public ResponseEntity<CartDto> addItem(@Valid @RequestBody AddToCartRequest body, HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        String sessionId = request.getHeader("X-Session-Id");
        return ResponseEntity.ok(cartService.addItem(customerId, sessionId, body.getProductId(), body.getQuantity()));
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<CartDto> updateItem(@PathVariable Long itemId,
                                               @Valid @RequestBody UpdateCartItemRequest body,
                                               HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        String sessionId = request.getHeader("X-Session-Id");
        return ResponseEntity.ok(cartService.updateItem(customerId, sessionId, itemId, body.getQuantity()));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CartDto> removeItem(@PathVariable Long itemId, HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        String sessionId = request.getHeader("X-Session-Id");
        return ResponseEntity.ok(cartService.removeItem(customerId, sessionId, itemId));
    }

    @PostMapping("/coupon")
    public ResponseEntity<CartDto> applyCoupon(@Valid @RequestBody ApplyCouponRequest body, HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        String sessionId = request.getHeader("X-Session-Id");
        return ResponseEntity.ok(cartService.applyCoupon(customerId, sessionId, body.getCode()));
    }

    @DeleteMapping("/coupon")
    public ResponseEntity<CartDto> removeCoupon(HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        String sessionId = request.getHeader("X-Session-Id");
        return ResponseEntity.ok(cartService.removeCoupon(customerId, sessionId));
    }
}
