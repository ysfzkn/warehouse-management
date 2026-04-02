package com.warehouse.controller.store;

import com.warehouse.dto.store.*;
import com.warehouse.service.CheckoutService;
import com.warehouse.util.CustomerTokenExtractor;
import com.warehouse.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store/checkout")
public class StoreCheckoutController {

    private final CheckoutService checkoutService;
    private final JwtService jwtService;

    public StoreCheckoutController(CheckoutService checkoutService, JwtService jwtService) {
        this.checkoutService = checkoutService;
        this.jwtService = jwtService;
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
