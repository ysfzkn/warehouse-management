package com.warehouse.service.impl;

import com.warehouse.dto.store.CustomerLoginRequest;
import com.warehouse.dto.store.CustomerLoginResponse;
import com.warehouse.dto.store.CustomerRegisterRequest;
import com.warehouse.entity.Customer;
import com.warehouse.entity.CustomerRefreshToken;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.exception.ErrorCode;
import com.warehouse.repository.CustomerRepository;
import com.warehouse.repository.CustomerRefreshTokenRepository;
import com.warehouse.security.JwtService;
import com.warehouse.service.CustomerAuthService;
import com.warehouse.config.SecurityProperties;
import com.warehouse.service.EmailService;
import com.warehouse.service.GoogleOAuthService;
import com.warehouse.util.PasswordPolicyValidator;
import com.warehouse.enums.CustomerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class CustomerAuthServiceImpl implements CustomerAuthService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerAuthServiceImpl.class);

    private final CustomerRepository customerRepository;
    private final CustomerRefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleOAuthService googleOAuthService;
    private final SecurityProperties securityProperties;
    private final EmailService emailService;

    public CustomerAuthServiceImpl(CustomerRepository customerRepository,
                                    CustomerRefreshTokenRepository refreshTokenRepository,
                                    PasswordEncoder passwordEncoder,
                                    JwtService jwtService,
                                    GoogleOAuthService googleOAuthService,
                                    SecurityProperties securityProperties,
                                    EmailService emailService) {
        this.customerRepository = customerRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleOAuthService = googleOAuthService;
        this.securityProperties = securityProperties;
        this.emailService = emailService;
    }

    @Override
    public CustomerLoginResponse register(CustomerRegisterRequest request) {
        PasswordPolicyValidator.validate(request.getPassword());

        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new WarehouseManagementException(ErrorCode.DUPLICATE_KEY, "Bu e-posta adresi zaten kayıtlı.");
        }

        if (!request.isKvkkConsent()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "KVKK onayı zorunludur.");
        }

        Customer customer = new Customer();
        customer.setEmail(request.getEmail());
        customer.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setPhone(request.getPhone());
        customer.setKvkkConsent(true);
        customer.setKvkkConsentAt(LocalDateTime.now());
        customer.setMarketingConsent(request.isMarketingConsent());
        if (request.isMarketingConsent()) {
            customer.setMarketingConsentAt(LocalDateTime.now());
        }
        customer.setEmailVerifyToken(UUID.randomUUID().toString());
        customer.setEmailVerifySentAt(LocalDateTime.now());
        // Email verification required - will be verified via link
        customer.setEmailVerified(false);

        customer = customerRepository.save(customer);

        // Send activation email (async — won't block registration)
        try {
            emailService.sendEmailVerification(customer.getEmail(), customer.getFirstName(), customer.getEmailVerifyToken());
        } catch (Exception e) {
            logger.warn("Failed to send verification email to {}: {}", customer.getEmail(), e.getMessage());
        }

        String token = jwtService.generateCustomerToken(customer.getId(), customer.getEmail());
        String refreshToken = createRefreshToken(customer);

        return new CustomerLoginResponse(
            customer.getId(), customer.getEmail(), customer.getFirstName(),
            token, refreshToken
        );
    }

    @Override
    public CustomerLoginResponse login(CustomerLoginRequest request, String ipAddress) {
        Customer customer = customerRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.AUTH_ERROR, "Geçersiz e-posta veya şifre."));

        if (!customer.isActive()) {
            throw new WarehouseManagementException(ErrorCode.AUTH_ERROR, "Hesabınız devre dışı bırakılmıştır.");
        }

        if (customer.getStatus() == CustomerStatus.BLACKLISTED) {
            throw new WarehouseManagementException(ErrorCode.AUTH_ERROR, "Hesabınız askıya alınmıştır. Lütfen müşteri hizmetleri ile iletişime geçin.");
        }

        if (customer.getLockedUntil() != null && customer.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new WarehouseManagementException(ErrorCode.AUTH_ERROR, "Hesap kilitli. Daha sonra tekrar deneyin.");
        }

        if (!passwordEncoder.matches(request.getPassword(), customer.getPasswordHash())) {
            customer.setFailedLoginCount(customer.getFailedLoginCount() + 1);
            if (customer.getFailedLoginCount() >= securityProperties.getMaxFailedLoginAttempts()) {
                customer.setLockedUntil(LocalDateTime.now().plusMinutes(securityProperties.getAccountLockoutMinutes()));
            }
            customerRepository.save(customer);
            throw new WarehouseManagementException(ErrorCode.AUTH_ERROR, "Geçersiz e-posta veya şifre.");
        }

        // Successful login
        customer.setFailedLoginCount(0);
        customer.setLockedUntil(null);
        customer.setLastLoginAt(LocalDateTime.now());
        customer.setLastLoginIp(ipAddress);
        customerRepository.save(customer);

        String token = jwtService.generateCustomerToken(customer.getId(), customer.getEmail());
        String refreshToken = createRefreshToken(customer);

        return new CustomerLoginResponse(
            customer.getId(), customer.getEmail(), customer.getFirstName(),
            token, refreshToken
        );
    }

    @Override
    public CustomerLoginResponse refreshToken(String refreshTokenStr) {
        CustomerRefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedAtIsNull(refreshTokenStr)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.AUTH_ERROR, "Oturum süresi dolmuş. Lütfen tekrar giriş yapın."));

        if (!refreshToken.isValid()) {
            throw new WarehouseManagementException(ErrorCode.AUTH_ERROR, "Oturumunuzun süresi dolmuş. Lütfen tekrar giriş yapın.");
        }

        Customer customer = refreshToken.getCustomer();

        // Revoke old token and create new pair
        refreshToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        String newToken = jwtService.generateCustomerToken(customer.getId(), customer.getEmail());
        String newRefreshToken = createRefreshToken(customer);

        return new CustomerLoginResponse(
            customer.getId(), customer.getEmail(), customer.getFirstName(),
            newToken, newRefreshToken
        );
    }

    @Override
    public void verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Doğrulama bağlantısı geçersiz.");
        }
        var optCustomer = customerRepository.findByEmailVerifyToken(token);
        if (optCustomer.isEmpty()) {
            // Token not found — might be already verified
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Bu bağlantı daha önce kullanılmış veya süresi dolmuş. Hesabınız zaten doğrulanmış olabilir.");
        }
        Customer customer = optCustomer.get();
        if (customer.isEmailVerified()) {
            // Already verified — clear token and return success-like
            customer.setEmailVerifyToken(null);
            customerRepository.save(customer);
            return; // Don't throw error — just succeed silently
        }
        customer.setEmailVerified(true);
        customer.setEmailVerifyToken(null);
        customerRepository.save(customer);
        logger.info("Email verified for customer: {}", customer.getEmail());
    }

    @Override
    public CustomerLoginResponse loginWithGoogle(String code, String redirectUri, String ipAddress) {
        Map<String, Object> userInfo = googleOAuthService.exchangeCodeForUserInfo(code, redirectUri);

        String email = (String) userInfo.get("email");
        String givenName = (String) userInfo.getOrDefault("given_name", "");
        String familyName = (String) userInfo.getOrDefault("family_name", "");

        // Find or create customer
        Customer customer = customerRepository.findByEmail(email).orElse(null);

        if (customer == null) {
            // Auto-register via Google
            customer = new Customer();
            customer.setEmail(email);
            customer.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            customer.setFirstName(givenName.isBlank() ? "Google" : givenName);
            customer.setLastName(familyName.isBlank() ? "User" : familyName);
            customer.setEmailVerified(true); // Google emails are pre-verified
            customer.setKvkkConsent(true);
            customer.setKvkkConsentAt(LocalDateTime.now());
            customer = customerRepository.save(customer);
            logger.info("New customer auto-registered via Google: {}", email);
        } else {
            // Existing customer checks
            if (customer.getStatus() == CustomerStatus.BLACKLISTED) {
                throw new WarehouseManagementException(ErrorCode.AUTH_ERROR, "Hesabınız askıya alınmıştır. Lütfen müşteri hizmetleri ile iletişime geçin.");
            }
            if (!customer.isActive()) {
                throw new WarehouseManagementException(ErrorCode.AUTH_ERROR, "Hesabınız devre dışı bırakılmıştır.");
            }
            // Ensure email is verified for Google users
            if (!customer.isEmailVerified()) {
                customer.setEmailVerified(true);
            }
        }

        // Update login info
        customer.setFailedLoginCount(0);
        customer.setLockedUntil(null);
        customer.setLastLoginAt(LocalDateTime.now());
        customer.setLastLoginIp(ipAddress);
        customerRepository.save(customer);

        String token = jwtService.generateCustomerToken(customer.getId(), customer.getEmail());
        String refreshToken = createRefreshToken(customer);

        return new CustomerLoginResponse(
            customer.getId(), customer.getEmail(), customer.getFirstName(),
            token, refreshToken
        );
    }

    private String createRefreshToken(Customer customer) {
        CustomerRefreshToken refreshToken = new CustomerRefreshToken();
        refreshToken.setCustomer(customer);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(securityProperties.getRefreshTokenExpirationDays()));
        refreshTokenRepository.save(refreshToken);
        return refreshToken.getToken();
    }

    @Override
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "E-posta adresi gereklidir.");
        }
        customerRepository.findByEmail(email.trim().toLowerCase()).ifPresent(customer -> {
            // Rate limit: don't send if last request was < 2 minutes ago
            if (customer.getPasswordResetSentAt() != null
                && customer.getPasswordResetSentAt().plusMinutes(2).isAfter(LocalDateTime.now())) {
                return;
            }
            String token = UUID.randomUUID().toString();
            customer.setPasswordResetToken(token);
            customer.setPasswordResetSentAt(LocalDateTime.now());
            customerRepository.save(customer);

            try {
                emailService.sendPasswordReset(customer.getEmail(), customer.getFirstName(), token);
            } catch (Exception e) {
                logger.error("Password reset email failed for {}: {}", email, e.getMessage());
            }
        });
        // Always return success to prevent email enumeration
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Geçersiz sıfırlama bağlantısı.");
        }
        Customer customer = customerRepository.findByPasswordResetToken(token)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Geçersiz veya süresi dolmuş sıfırlama bağlantısı."));

        // Token expires after 1 hour
        if (customer.getPasswordResetSentAt() == null
            || customer.getPasswordResetSentAt().plusHours(1).isBefore(LocalDateTime.now())) {
            customer.setPasswordResetToken(null);
            customer.setPasswordResetSentAt(null);
            customerRepository.save(customer);
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Sıfırlama bağlantısının süresi dolmuş. Lütfen yeni bir talep oluşturun.");
        }

        PasswordPolicyValidator.validate(newPassword);

        customer.setPasswordHash(passwordEncoder.encode(newPassword));
        customer.setPasswordResetToken(null);
        customer.setPasswordResetSentAt(null);
        customerRepository.save(customer);

        // Send confirmation email
        try {
            emailService.sendPasswordResetConfirmation(customer.getEmail(), customer.getFirstName());
        } catch (Exception e) {
            logger.warn("Password reset confirmation email failed: {}", e.getMessage());
        }

        logger.info("Password reset completed for customer: {}", customer.getEmail());
    }

    @Override
    public CustomerLoginResponse completeGuestAccount(String token, String newPassword, String ipAddress) {
        if (token == null || token.isBlank()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Geçersiz bağlantı.");
        }
        Customer customer = customerRepository.findByPasswordResetToken(token)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Geçersiz veya süresi dolmuş hesap tamamlama bağlantısı."));

        // Guest account completion token is valid for 7 days
        if (customer.getPasswordResetSentAt() == null
            || customer.getPasswordResetSentAt().plusDays(7).isBefore(LocalDateTime.now())) {
            customer.setPasswordResetToken(null);
            customer.setPasswordResetSentAt(null);
            customerRepository.save(customer);
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Hesap tamamlama bağlantısının süresi dolmuş. Lütfen şifremi unuttum bağlantısını kullanın.");
        }

        PasswordPolicyValidator.validate(newPassword);

        // Set the password and mark the email as verified (the customer owns the token, so has access to the email)
        customer.setPasswordHash(passwordEncoder.encode(newPassword));
        customer.setPasswordResetToken(null);
        customer.setPasswordResetSentAt(null);
        customer.setEmailVerified(true);
        customer.setEmailVerifyToken(null);
        customer.setLastLoginAt(LocalDateTime.now());
        customer.setLastLoginIp(ipAddress);
        customerRepository.save(customer);

        logger.info("Guest account completed and verified: {}", customer.getEmail());

        // Generate JWT + refresh token (log the user in directly)
        String jwtToken = jwtService.generateCustomerToken(customer.getId(), customer.getEmail());
        String refreshToken = createRefreshToken(customer);

        return new CustomerLoginResponse(
            customer.getId(), customer.getEmail(), customer.getFirstName(),
            jwtToken, refreshToken
        );
    }
}
