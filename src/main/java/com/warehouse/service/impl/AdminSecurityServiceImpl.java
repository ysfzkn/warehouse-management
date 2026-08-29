package com.warehouse.service.impl;

import com.warehouse.entity.AdminSecurityCode;
import com.warehouse.enums.RoleName;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.AdminSecurityCodeRepository;
import com.warehouse.service.AdminSecurityService;
import com.warehouse.util.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@Transactional
public class AdminSecurityServiceImpl implements AdminSecurityService {

    private static final Logger logger = LoggerFactory.getLogger(AdminSecurityServiceImpl.class);

    /** Namespaces security-code attempts so they never collide with login attempts. */
    private static final String SECURITY_CODE_ATTEMPT_PREFIX = "seccode:";

    /** Minimum length for a new security code. Five digits is 100k guesses — not a factor. */
    private static final int MIN_SECURITY_CODE_LENGTH = 8;

    private final AdminSecurityCodeRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final com.warehouse.security.AdminLoginAttemptTracker attemptTracker;

    @Value("${app.admin.security-code:}")
    private String initialSecurityCode;

    public AdminSecurityServiceImpl(AdminSecurityCodeRepository repository,
                                    PasswordEncoder passwordEncoder,
                                    com.warehouse.security.AdminLoginAttemptTracker attemptTracker) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.attemptTracker = attemptTracker;
    }

    @Override
    public void requireSecurityCodeForAdmin(String securityCode) {
        if (!RoleName.ADMIN.name().equalsIgnoreCase(CurrentUser.getRole())) {
            return;
        }

        AdminSecurityCode code = initializeIfNeeded();
        if (code == null) {
            logger.warn("Admin security code requested but not configured");
            throw new WarehouseManagementException(ErrorCode.ADMIN_SECURITY_CODE_REQUIRED, "Yönetici güvenlik şifresi tanımlı değil. Lütfen sistem yöneticisine bildirin.");
        }

        if (securityCode == null || securityCode.isBlank()) {
            throw new WarehouseManagementException(ErrorCode.ADMIN_SECURITY_CODE_REQUIRED, "Güvenlik şifresi zorunludur.");
        }

        // The security code is the second factor guarding destructive admin actions, but
        // it had no attempt limit at all — a caller already holding an admin token could
        // grind a short numeric code offline-fast against this endpoint. It is now subject
        // to the same 5-attempts-then-lock policy as the login form. The bcrypt comparison
        // that follows is deliberately slow, so an unbounded loop was also a cheap way to
        // saturate the CPU.
        String attemptKey = SECURITY_CODE_ATTEMPT_PREFIX + CurrentUser.usernameOrSystem();
        long lockedUntil = attemptTracker.lockedUntilMillis(attemptKey);
        if (lockedUntil > 0) {
            long minutes = Math.max(1, (lockedUntil - System.currentTimeMillis()) / 60_000);
            throw new WarehouseManagementException(ErrorCode.INVALID_ADMIN_SECURITY_CODE,
                    "Çok fazla hatalı güvenlik şifresi denemesi. " + minutes + " dakika sonra tekrar deneyin.");
        }

        if (!passwordEncoder.matches(securityCode, code.getCodeHash())) {
            attemptTracker.recordFailure(attemptKey);
            logger.warn("Hatalı yönetici güvenlik şifresi denemesi: user={}", CurrentUser.usernameOrSystem());
            throw new WarehouseManagementException(ErrorCode.INVALID_ADMIN_SECURITY_CODE, "Güvenlik şifresi hatalı.");
        }
        attemptTracker.recordSuccess(attemptKey);
    }

    @Override
    public void changeSecurityCode(String currentCode, String newCode, String confirmNewCode) {
        AdminSecurityCode existing = initializeIfNeeded();
        if (existing == null) {
            throw new WarehouseManagementException(ErrorCode.ADMIN_SECURITY_CODE_REQUIRED);
        }

        if (currentCode == null || currentCode.isBlank()) {
            throw new WarehouseManagementException(ErrorCode.ADMIN_SECURITY_CODE_CURRENT_REQUIRED);
        }
        if (newCode == null || newCode.isBlank()) {
            throw new WarehouseManagementException(ErrorCode.ADMIN_SECURITY_CODE_NEW_REQUIRED);
        }
        if (!newCode.equals(confirmNewCode)) {
            throw new WarehouseManagementException(ErrorCode.ADMIN_SECURITY_CODE_MISMATCH);
        }
        if (newCode.length() < MIN_SECURITY_CODE_LENGTH) {
            throw new WarehouseManagementException(ErrorCode.ADMIN_SECURITY_CODE_TOO_SHORT);
        }

        if (!passwordEncoder.matches(currentCode, existing.getCodeHash())) {
            throw new WarehouseManagementException(ErrorCode.INVALID_ADMIN_SECURITY_CODE);
        }

        existing.setCodeHash(passwordEncoder.encode(newCode));
        existing.setUpdatedAt(OffsetDateTime.now());
        existing.setUpdatedBy(CurrentUser.usernameOrSystem());
        repository.save(existing);
        logger.info("Admin security code updated by {}", existing.getUpdatedBy());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSecurityCodeConfigured() {
        return repository.count() > 0 || (initialSecurityCode != null && !initialSecurityCode.isBlank());
    }

    /**
     * Ensures a code exists in DB if an env/default code was provided.
     */
    private AdminSecurityCode initializeIfNeeded() {
        return repository.findTopByOrderByIdAsc()
                .orElseGet(() -> {
                    if (initialSecurityCode == null || initialSecurityCode.isBlank()) {
                        return null;
                    }
                    AdminSecurityCode code = new AdminSecurityCode();
                    code.setCodeHash(passwordEncoder.encode(initialSecurityCode));
                    code.setUpdatedAt(OffsetDateTime.now());
                    code.setUpdatedBy("system");
                    logger.info("Admin security code initialized from environment variable");
                    return repository.save(code);
                });
    }
}

