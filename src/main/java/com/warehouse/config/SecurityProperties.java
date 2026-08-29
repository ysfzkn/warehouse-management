package com.warehouse.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.security")
@Getter
@Setter
public class SecurityProperties {
    private String jwtSecret = "change-this-secret";
    private long jwtExpirationMinutes = 480; // 8 hours
    /**
     * Admin token lifetime in hours. Historically {@code app.security.jwt-expiration-hours}
     * was set in every properties file and wired to {@code JWT_EXPIRATION_HOURS}, but no
     * matching field existed here — Spring silently discarded it and every admin token
     * lived for the 8-hour {@link #jwtExpirationMinutes} default instead. Binding it
     * properly makes the documented environment variable actually take effect.
     */
    private Long jwtExpirationHours;
    private long customerTokenExpirationDays = 7;
    private long refreshTokenExpirationDays = 30;
    private long accountLockoutMinutes = 15;
    private int maxFailedLoginAttempts = 5;
    private long cartExpirationDays = 30;

    /** Effective admin token lifetime: the hours property wins when set. */
    public long resolvedJwtExpirationMinutes() {
        if (jwtExpirationHours != null && jwtExpirationHours > 0) {
            return jwtExpirationHours * 60;
        }
        return jwtExpirationMinutes;
    }
}


