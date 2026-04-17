package com.warehouse.service;

import com.warehouse.dto.store.CheckoutValidationResponse;
import com.warehouse.dto.store.GuestPlaceOrderRequest;
import com.warehouse.dto.store.PlaceOrderRequest;
import com.warehouse.dto.store.PlaceOrderResponse;

public interface CheckoutService {
    CheckoutValidationResponse validateCheckout(Long customerId);
    PlaceOrderResponse placeOrder(Long customerId, PlaceOrderRequest request, String ipAddress, String userAgent);

    /**
     * Misafir (üye olmayan) müşteri için sipariş oluşturur.
     * Otomatik olarak bir müşteri kaydı oluşturur (emailVerified=false) ve
     * müşteriye "hesabını tamamla" e-postası gönderir.
     */
    PlaceOrderResponse placeGuestOrder(GuestPlaceOrderRequest request, String ipAddress, String userAgent);
}
