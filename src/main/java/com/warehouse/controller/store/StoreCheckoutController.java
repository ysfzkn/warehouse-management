package com.warehouse.controller.store;

import com.warehouse.dto.store.*;
import com.warehouse.repository.CargoProviderRepository;
import com.warehouse.service.ShippingPriceService;
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
    private final com.warehouse.service.ShippingPriceService shippingPriceService;
    private final IdempotencyStore idempotencyStore;
    private final com.warehouse.security.ClientIpResolver clientIpResolver;
    private final com.warehouse.service.cargo.CargoCarrierRules carrierRules;

    public StoreCheckoutController(CheckoutService checkoutService, JwtService jwtService,
                                    CargoProviderRepository cargoProviderRepository,
                                    com.warehouse.service.ShippingPriceService shippingPriceService,
                                    IdempotencyStore idempotencyStore,
                                    com.warehouse.security.ClientIpResolver clientIpResolver,
                                    com.warehouse.service.cargo.CargoCarrierRules carrierRules) {
        this.checkoutService = checkoutService;
        this.jwtService = jwtService;
        this.cargoProviderRepository = cargoProviderRepository;
        this.shippingPriceService = shippingPriceService;
        this.idempotencyStore = idempotencyStore;
        this.clientIpResolver = clientIpResolver;
        this.carrierRules = carrierRules;
    }

    /**
     * Public endpoint: Active cargo providers for checkout selection.
     */
    /**
     * Public endpoint: the carriers a customer may choose, each with the price that will be
     * charged for this basket.
     *
     * <p>The price used to be left to the storefront, which showed {@code baseCost} alone — no
     * per-desi surcharge, no live carrier price. The order charged both, so a white-goods basket
     * was billed well above the figure the customer agreed to. The number is computed here now,
     * by the same service the order uses, and the screen only displays it.
     *
     * @param subtotal basket total, needed to tell whether free shipping applies
     * @param desi     parcel size of the basket, as the cart reports it
     */
    @GetMapping("/cargo-providers")
    public ResponseEntity<List<Map<String, Object>>> getCargoProviders(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) java.math.BigDecimal desi,
            @RequestParam(required = false) java.math.BigDecimal subtotal) {

        List<Map<String, Object>> providers = cargoProviderRepository.findByActiveTrueOrderBySortOrderAsc()
            .stream()
            // A carrier that does not serve this district, or refuses this parcel size, is not
            // an option — offering it only produces a shipment that comes back.
            .filter(p -> city == null || carrierRules.canCarry(p, city, district, desi))
            .map(p -> {
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("id", p.getId());
                dto.put("name", p.getName());
                dto.put("code", p.getCode());
                dto.put("logoUrl", p.getLogoUrl());
                dto.put("baseCost", p.getBaseCost());
                dto.put("costPerDesi", p.getCostPerDesi());
                // The store-wide promise, not this carrier's old column — the storefront
                // prints it as "… üzeri ücretsiz kargo" and it has to be the real number.
                dto.put("freeShippingThreshold", shippingPriceService.freeShippingThreshold());
                dto.put("estimatedDeliveryDays", p.getEstimatedDeliveryDays());
                dto.put("vatRate", p.getVatRate());

                ShippingPriceService.Quote quote =
                        shippingPriceService.quote(p, subtotal, desi, city, district);
                dto.put("price", quote.cost());
                dto.put("priceVat", quote.vat());
                dto.put("priceTotal", quote.total());
                dto.put("free", quote.free());
                // "live" tells the screen it may show a carrier-quoted delivery time as well.
                dto.put("priceSource", quote.source().name().toLowerCase());
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
     * Authenticated checkout. Supports an Idempotency-Key header to guard against
     * double-clicks — the same key returns the same result for 24 hours.
     */
    @PostMapping("/place-order")
    public ResponseEntity<?> placeOrder(@Valid @RequestBody PlaceOrderRequest body,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                         HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        return processWithIdempotency(NS_AUTH_CHECKOUT, idempotencyKey,
                () -> serializeOrder(checkoutService.placeOrder(customerId, body, clientIpResolver.resolve(request), request.getHeader("User-Agent"))));
    }

    /**
     * Creates an order for a guest (non-registered) customer. Supports Idempotency-Key.
     */
    @PostMapping("/guest-checkout")
    public ResponseEntity<?> guestCheckout(@Valid @RequestBody GuestPlaceOrderRequest body,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                            HttpServletRequest request) {
        return processWithIdempotency(NS_GUEST_CHECKOUT, idempotencyKey,
                () -> serializeOrder(checkoutService.placeGuestOrder(body, clientIpResolver.resolve(request), request.getHeader("User-Agent"))));
    }

    /**
     * Idempotency wrapper: for a request with the same key, first checks the cache and
     * returns the cached result if present; otherwise runs the supplier and caches the result.
     */
    private ResponseEntity<?> processWithIdempotency(String namespace, String key,
                                                      java.util.function.Supplier<Map<String, Object>> action) {
        // If there is no key, run directly (backward compatibility; idempotency is optional)
        if (key == null || key.isBlank()) {
            return ResponseEntity.ok(action.get());
        }
        // Is it already in the cache?
        Map<String, Object> cached = idempotencyStore.get(namespace, key);
        if (cached != null) {
            log.info("Idempotency hit: ns={}, key={}", namespace, key);
            return ResponseEntity.ok().header("Idempotency-Replay", "true").body(cached);
        }
        // Prevent concurrent duplicates
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

    /** PlaceOrderResponse → Map (the idempotency cache must be JSON-compatible). */
    private Map<String, Object> serializeOrder(PlaceOrderResponse r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", r.getOrderId());
        m.put("orderNumber", r.getOrderNumber());
        m.put("status", r.getStatus());
        m.put("grandTotal", r.getGrandTotal());
        m.put("paymentUrl", r.getPaymentUrl());
        // Handed to the browser that placed the order and required by
        // POST /api/store/payment/initialize. This is the only response that ever
        // carries it, which is what makes it a proof of ownership.
        m.put("paymentToken", r.getPaymentToken());
        return m;
    }
}
