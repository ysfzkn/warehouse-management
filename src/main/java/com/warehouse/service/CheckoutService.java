package com.warehouse.service;

import com.warehouse.dto.store.CheckoutValidationResponse;
import com.warehouse.dto.store.GuestPlaceOrderRequest;
import com.warehouse.dto.store.PlaceOrderRequest;
import com.warehouse.dto.store.PlaceOrderResponse;

public interface CheckoutService {
    CheckoutValidationResponse validateCheckout(Long customerId);
    PlaceOrderResponse placeOrder(Long customerId, PlaceOrderRequest request, String ipAddress, String userAgent);

    /**
     * Creates an order for a guest (non-member) customer.
     * Automatically creates a customer record (emailVerified=false) and
     * sends the customer a "complete your account" email.
     */
    PlaceOrderResponse placeGuestOrder(GuestPlaceOrderRequest request, String ipAddress, String userAgent);
}
