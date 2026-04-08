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
    private long customerTokenExpirationDays = 7;
    private long refreshTokenExpirationDays = 30;
    private long accountLockoutMinutes = 15;
    private int maxFailedLoginAttempts = 5;
    private long cartExpirationDays = 30;
}


