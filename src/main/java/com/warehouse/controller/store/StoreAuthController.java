package com.warehouse.controller.store;

import com.warehouse.dto.store.CustomerLoginRequest;
import com.warehouse.dto.store.CustomerLoginResponse;
import com.warehouse.dto.store.CustomerRegisterRequest;
import com.warehouse.dto.store.GoogleAuthRequest;
import com.warehouse.security.CaptchaService;
import com.warehouse.security.ClientIpResolver;
import com.warehouse.security.JwtService;
import com.warehouse.service.CustomerAuthService;
import jakarta.servlet.http.Cookie;
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
    private final CaptchaService captchaService;
    private final ClientIpResolver clientIpResolver;
    private final JwtService jwtService;

    public StoreAuthController(CustomerAuthService customerAuthService,
                               CaptchaService captchaService,
                               ClientIpResolver clientIpResolver,
                               JwtService jwtService) {
        this.customerAuthService = customerAuthService;
        this.captchaService = captchaService;
        this.clientIpResolver = clientIpResolver;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody CustomerRegisterRequest request,
                                       @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
                                       HttpServletRequest httpRequest) {
        // CAPTCHA (feature flag from env; default off)
        if (!captchaService.verify(captchaToken, clientIpResolver.resolve(httpRequest))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "CAPTCHA doğrulaması başarısız. Lütfen tekrar deneyin."));
        }
        CustomerLoginResponse response = customerAuthService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .headers(buildAuthCookies(response.getToken(), response.getRefreshToken()))
            .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<CustomerLoginResponse> login(@Valid @RequestBody CustomerLoginRequest request,
                                                        HttpServletRequest httpRequest) {
        String ip = clientIpResolver.resolve(httpRequest);
        CustomerLoginResponse response = customerAuthService.login(request, ip);
        return ResponseEntity.ok()
            .headers(buildAuthCookies(response.getToken(), response.getRefreshToken()))
            .body(response);
    }

    /**
     * Rotates the session. The refresh token is read from the HttpOnly cookie when the
     * body does not carry one, so a browser client never has to keep it in JavaScript.
     */
    @PostMapping("/refresh")
    public ResponseEntity<CustomerLoginResponse> refresh(@RequestBody(required = false) Map<String, String> body,
                                                          HttpServletRequest httpRequest) {
        String refreshToken = body != null ? body.get("refreshToken") : null;
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = readCookie(httpRequest, "refresh_token");
        }
        CustomerLoginResponse response = customerAuthService.refreshToken(refreshToken);
        return ResponseEntity.ok()
            .headers(buildAuthCookies(response.getToken(), response.getRefreshToken()))
            .body(response);
    }

    /**
     * Ends the session server-side: the access token goes on the revocation denylist,
     * the refresh token is revoked in the database and both cookies are cleared.
     *
     * <p>There was previously no logout endpoint at all — "signing out" only removed the
     * token from browser storage, leaving any copy of it valid for up to seven days.</p>
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest httpRequest) {
        String header = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            jwtService.revoke(header.substring(7));
        }
        String cookieToken = readCookie(httpRequest, "access_token");
        if (cookieToken != null) {
            jwtService.revoke(cookieToken);
        }
        customerAuthService.revokeRefreshToken(readCookie(httpRequest, "refresh_token"));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, expiredCookie("access_token", "/api/store").toString());
        headers.add(HttpHeaders.SET_COOKIE, expiredCookie("refresh_token", "/api/store/auth").toString());
        return ResponseEntity.ok().headers(headers).body(Map.of("message", "Oturum kapatıldı."));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestBody Map<String, String> body) {
        customerAuthService.verifyEmail(body.get("token"));
        return ResponseEntity.ok(Map.of("message", "E-posta adresiniz başarıyla doğrulandı."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body,
                                              @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
                                              HttpServletRequest httpRequest) {
        if (!captchaService.verify(captchaToken, clientIpResolver.resolve(httpRequest))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "CAPTCHA doğrulaması başarısız."));
        }
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
     * Completes the account created after a guest checkout.
     * Sets the password, verifies the email, and logs the user in automatically.
     */
    @PostMapping("/complete-account")
    public ResponseEntity<CustomerLoginResponse> completeAccount(@RequestBody Map<String, String> body,
                                                                   HttpServletRequest httpRequest) {
        String ip = clientIpResolver.resolve(httpRequest);
        CustomerLoginResponse response = customerAuthService.completeGuestAccount(
                body.get("token"), body.get("password"), ip);
        return ResponseEntity.ok()
            .headers(buildAuthCookies(response.getToken(), response.getRefreshToken()))
            .body(response);
    }

    @PostMapping("/google")
    public ResponseEntity<CustomerLoginResponse> googleAuth(@RequestBody GoogleAuthRequest request,
                                                             HttpServletRequest httpRequest) {
        String ip = clientIpResolver.resolve(httpRequest);
        CustomerLoginResponse response = customerAuthService.loginWithGoogle(
            request.getCode(), request.getRedirectUri(), ip);
        return ResponseEntity.ok()
            .headers(buildAuthCookies(response.getToken(), response.getRefreshToken()))
            .body(response);
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static ResponseCookie expiredCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(path)
                .maxAge(0)
                .build();
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
