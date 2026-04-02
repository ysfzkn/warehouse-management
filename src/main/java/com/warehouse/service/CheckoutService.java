package com.warehouse.service;

import com.warehouse.dto.store.CheckoutValidationResponse;
import com.warehouse.dto.store.PlaceOrderRequest;
import com.warehouse.dto.store.PlaceOrderResponse;

public interface CheckoutService {
    CheckoutValidationResponse validateCheckout(Long customerId);
    PlaceOrderResponse placeOrder(Long customerId, PlaceOrderRequest request, String ipAddress, String userAgent);
}
