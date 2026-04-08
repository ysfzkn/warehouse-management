package com.warehouse.service;

import com.warehouse.dto.store.CartDto;

public interface CartService {
    CartDto getCart(Long customerId, String sessionId);
    CartDto addItem(Long customerId, String sessionId, Long productId, int quantity);
    CartDto updateItem(Long customerId, String sessionId, Long cartItemId, int quantity);
    CartDto removeItem(Long customerId, String sessionId, Long cartItemId);
    CartDto applyCoupon(Long customerId, String sessionId, String couponCode);
    CartDto removeCoupon(Long customerId, String sessionId);
    void clearCart(Long customerId, String sessionId);
}
