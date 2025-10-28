package com.warehouse.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.admin")
@Getter
@Setter
public class AuthProperties {

    private String username = "admin";
    private String password = "admin";
}

