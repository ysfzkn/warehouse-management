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
     * Misafir checkout sonrası oluşturulan hesabı tamamlar:
     * - Şifre oluşturur
     * - E-postayı doğrulanmış olarak işaretler
     * - Kullanıcıyı login yapar (JWT döner)
     *
     * Token 7 gün geçerlidir.
     */
    CustomerLoginResponse completeGuestAccount(String token, String newPassword, String ipAddress);
}
