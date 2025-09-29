package com.warehouse.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
public class InfoController {

    @Value("${spring.datasource.url:NOT_SET}")
    private String datasourceUrl;

    @Value("${spring.profiles.active:NOT_SET}")
    private String activeProfiles;

    @Value("${DATABASE_URL:NOT_SET}")
    private String databaseUrl;

    @Value("${DB_USERNAME:NOT_SET}")
    private String dbUsername;

    @Value("${DB_PASSWORD:NOT_SET}")
    private String dbPassword;

    @GetMapping("/api/info")
    public Map<String, String> getEnvironmentInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("activeProfiles", activeProfiles);
        info.put("datasourceUrl", datasourceUrl);
        info.put("databaseUrl", databaseUrl);
        info.put("dbUsername", dbUsername);
        info.put("dbPassword", dbPassword.startsWith("NOT_SET") ? "NOT_SET" : "SET");
        info.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return info;
    }
}
