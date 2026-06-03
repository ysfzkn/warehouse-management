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
    void requestPasswordReset(String email);
    void resetPassword(String token, String newPassword);

    /**
     * Completes the account created after guest checkout:
     * - Sets a password
     * - Marks the email as verified
     * - Logs the user in (returns a JWT)
     *
     * The token is valid for 7 days.
     */
    CustomerLoginResponse completeGuestAccount(String token, String newPassword, String ipAddress);
}
