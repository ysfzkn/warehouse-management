package com.warehouse.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.oauth.google")
@Getter
@Setter
public class GoogleOAuthProperties {
    private String clientId = "";
    private String clientSecret = "";
    private String redirectUri = "http://localhost:3000/auth/google/callback";
}
