package com.warehouse.service;

import com.warehouse.dto.store.CustomerLoginRequest;
import com.warehouse.dto.store.CustomerLoginResponse;
import com.warehouse.dto.store.CustomerRegisterRequest;

public interface CustomerAuthService {
    CustomerLoginResponse register(CustomerRegisterRequest request);
    CustomerLoginResponse login(CustomerLoginRequest request, String ipAddress);
    CustomerLoginResponse refreshToken(String refreshToken);
    void verifyEmail(String token);
    CustomerLoginResponse loginWithGoogle(String code, String redirectUri, String ipAddress);
}
