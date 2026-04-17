package com.warehouse.controller.store;

import com.warehouse.dto.store.CustomerLoginRequest;
import com.warehouse.dto.store.CustomerLoginResponse;
import com.warehouse.dto.store.CustomerRegisterRequest;
import com.warehouse.dto.store.GoogleAuthRequest;
import com.warehouse.service.CustomerAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/store/auth")
public class StoreAuthController {

    private final CustomerAuthService customerAuthService;

    public StoreAuthController(CustomerAuthService customerAuthService) {
        this.customerAuthService = customerAuthService;
    }

    @PostMapping("/register")
    public ResponseEntity<CustomerLoginResponse> register(@Valid @RequestBody CustomerRegisterRequest request) {
        CustomerLoginResponse response = customerAuthService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .headers(buildAuthCookies(response.getToken(), response.getRefreshToken()))
            .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<CustomerLoginResponse> login(@Valid @RequestBody CustomerLoginRequest request,
                                                        HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        CustomerLoginResponse response = customerAuthService.login(request, ip);
        return ResponseEntity.ok()
            .headers(buildAuthCookies(response.getToken(), response.getRefreshToken()))
            .body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<CustomerLoginResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        return ResponseEntity.ok(customerAuthService.refreshToken(refreshToken));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestBody Map<String, String> body) {
        customerAuthService.verifyEmail(body.get("token"));
        return ResponseEntity.ok(Map.of("message", "E-posta adresiniz başarıyla doğrulandı."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> body) {
        customerAuthService.requestPasswordReset(body.get("email"));
        // Always return success to prevent email enumeration attacks
        return ResponseEntity.ok(Map.of("message", "Şifre sıfırlama bağlantısı e-posta adresinize gönderildi."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> body) {
        customerAuthService.resetPassword(body.get("token"), body.get("password"));
        return ResponseEntity.ok(Map.of("message", "Şifreniz başarıyla değiştirildi. Yeni şifrenizle giriş yapabilirsiniz."));
    }

    /**
     * Misafir checkout sonrası oluşturulan hesabı tamamlar.
     * Şifreyi belirler, e-postayı doğrular ve otomatik login yapar.
     */
    @PostMapping("/complete-account")
    public ResponseEntity<CustomerLoginResponse> completeAccount(@RequestBody Map<String, String> body,
                                                                   HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        CustomerLoginResponse response = customerAuthService.completeGuestAccount(
                body.get("token"), body.get("password"), ip);
        return ResponseEntity.ok()
            .headers(buildAuthCookies(response.getToken(), response.getRefreshToken()))
            .body(response);
    }

    @PostMapping("/google")
    public ResponseEntity<CustomerLoginResponse> googleAuth(@RequestBody GoogleAuthRequest request,
                                                             HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        CustomerLoginResponse response = customerAuthService.loginWithGoogle(
            request.getCode(), request.getRedirectUri(), ip);
        return ResponseEntity.ok()
            .headers(buildAuthCookies(response.getToken(), response.getRefreshToken()))
            .body(response);
    }

    private HttpHeaders buildAuthCookies(String token, String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE,
            ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/store")
                .maxAge(7 * 24 * 3600)
                .build().toString());
        headers.add(HttpHeaders.SET_COOKIE,
            ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/store/auth")
                .maxAge(30 * 24 * 3600)
                .build().toString());
        return headers;
    }
}
