package com.warehouse.controller.store;

import com.warehouse.dto.store.*;
import com.warehouse.entity.CargoProvider;
import com.warehouse.repository.CargoProviderRepository;
import com.warehouse.service.CheckoutService;
import com.warehouse.util.CustomerTokenExtractor;
import com.warehouse.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/store/checkout")
public class StoreCheckoutController {

    private final CheckoutService checkoutService;
    private final JwtService jwtService;
    private final CargoProviderRepository cargoProviderRepository;

    public StoreCheckoutController(CheckoutService checkoutService, JwtService jwtService,
                                    CargoProviderRepository cargoProviderRepository) {
        this.checkoutService = checkoutService;
        this.jwtService = jwtService;
        this.cargoProviderRepository = cargoProviderRepository;
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

    @PostMapping("/place-order")
    public ResponseEntity<PlaceOrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest body,
                                                          HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        return ResponseEntity.ok(checkoutService.placeOrder(customerId, body, request.getRemoteAddr(), request.getHeader("User-Agent")));
    }
}
