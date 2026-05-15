package com.warehouse.controller.store;

import com.warehouse.dto.store.*;
import com.warehouse.repository.CargoProviderRepository;
import com.warehouse.security.IdempotencyStore;
import com.warehouse.service.CheckoutService;
import com.warehouse.util.CustomerTokenExtractor;
import com.warehouse.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/store/checkout")
public class StoreCheckoutController {

    private static final Logger log = LoggerFactory.getLogger(StoreCheckoutController.class);

    private static final String NS_AUTH_CHECKOUT = "checkout-auth";
    private static final String NS_GUEST_CHECKOUT = "checkout-guest";

    private final CheckoutService checkoutService;
    private final JwtService jwtService;
    private final CargoProviderRepository cargoProviderRepository;
    private final IdempotencyStore idempotencyStore;

    public StoreCheckoutController(CheckoutService checkoutService, JwtService jwtService,
                                    CargoProviderRepository cargoProviderRepository,
                                    IdempotencyStore idempotencyStore) {
        this.checkoutService = checkoutService;
        this.jwtService = jwtService;
        this.cargoProviderRepository = cargoProviderRepository;
        this.idempotencyStore = idempotencyStore;
    }

    /**
     * Public endpoint: Active cargo providers for checkout selection.
     */
    @GetMapping("/cargo-providers")
    public ResponseEntity<List<Map<String, Object>>> getCargoProviders() {
        List<Map<String, Object>> providers = cargoProviderRepository.findByActiveTrueOrderBySortOrderAsc()
            .stream().map(p -> {
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("id", p.getId());
                dto.put("name", p.getName());
                dto.put("code", p.getCode());
                dto.put("logoUrl", p.getLogoUrl());
                dto.put("baseCost", p.getBaseCost());
                dto.put("costPerDesi", p.getCostPerDesi());
                dto.put("freeShippingThreshold", p.getFreeShippingThreshold());
                dto.put("estimatedDeliveryDays", p.getEstimatedDeliveryDays());
                dto.put("vatRate", p.getVatRate());
                return dto;
            }).collect(Collectors.toList());
        return ResponseEntity.ok(providers);
    }

    @PostMapping("/validate")
    public ResponseEntity<CheckoutValidationResponse> validate(HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        return ResponseEntity.ok(checkoutService.validateCheckout(customerId));
    }

    /**
     * Authenticated checkout. Çift tıklamaya karşı Idempotency-Key header'ı
     * destekler — aynı key 24 saat boyunca aynı sonucu döner.
     */
    @PostMapping("/place-order")
    public ResponseEntity<?> placeOrder(@Valid @RequestBody PlaceOrderRequest body,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                         HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        return processWithIdempotency(NS_AUTH_CHECKOUT, idempotencyKey,
                () -> serializeOrder(checkoutService.placeOrder(customerId, body, request.getRemoteAddr(), request.getHeader("User-Agent"))));
    }

    /**
     * Misafir (üye olmayan) müşteri için sipariş oluşturur. Idempotency-Key destekler.
     */
    @PostMapping("/guest-checkout")
    public ResponseEntity<?> guestCheckout(@Valid @RequestBody GuestPlaceOrderRequest body,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                            HttpServletRequest request) {
        return processWithIdempotency(NS_GUEST_CHECKOUT, idempotencyKey,
                () -> serializeOrder(checkoutService.placeGuestOrder(body, request.getRemoteAddr(), request.getHeader("User-Agent"))));
    }

    /**
     * Idempotency wrapper: aynı key ile gelen istekte önce cache'i kontrol eder,
     * varsa onu döner; yoksa supplier'ı çalıştırıp sonucu cache'ler.
     */
    private ResponseEntity<?> processWithIdempotency(String namespace, String key,
                                                      java.util.function.Supplier<Map<String, Object>> action) {
        // Key yoksa direkt çalıştır (geriye uyumluluk; idempotency opsiyonel)
        if (key == null || key.isBlank()) {
            return ResponseEntity.ok(action.get());
        }
        // Önce cache'te var mı?
        Map<String, Object> cached = idempotencyStore.get(namespace, key);
        if (cached != null) {
            log.info("Idempotency hit: ns={}, key={}", namespace, key);
            return ResponseEntity.ok().header("Idempotency-Replay", "true").body(cached);
        }
        // Concurrent duplicate'ları engelle
        if (!idempotencyStore.tryAcquire(namespace, key)) {
            return ResponseEntity.status(409).body(Map.of(
                    "message", "Bu işlem hâlâ devam ediyor. Lütfen birkaç saniye bekleyip tekrar deneyin.",
                    "code", "IDEMPOTENCY_IN_FLIGHT"
            ));
        }
        try {
            Map<String, Object> result = action.get();
            idempotencyStore.put(namespace, key, result);
            return ResponseEntity.ok(result);
        } finally {
            idempotencyStore.release(namespace, key);
        }
    }

    /** PlaceOrderResponse → Map (idempotency cache JSON-uyumlu olmalı). */
    private Map<String, Object> serializeOrder(PlaceOrderResponse r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", r.getOrderId());
        m.put("orderNumber", r.getOrderNumber());
        m.put("status", r.getStatus());
        m.put("grandTotal", r.getGrandTotal());
        m.put("paymentUrl", r.getPaymentUrl());
        return m;
    }
}
