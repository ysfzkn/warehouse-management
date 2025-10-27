package com.warehouse;

import com.warehouse.filter.SimpleAuthFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
public class WarehouseManagementApplication {

    @PostConstruct
    public void init() {
        // Set default timezone to Turkey (GMT+3)
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"));
    }

    public static void main(String[] args) {
        SpringApplication.run(WarehouseManagementApplication.class, args);
    }

    @Bean
    public FilterRegistrationBean<SimpleAuthFilter> simpleAuthFilterRegistration(SimpleAuthFilter filter) {
        FilterRegistrationBean<SimpleAuthFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(filter);
        reg.addUrlPatterns("/api/*");
        reg.setOrder(1);
        return reg;
    }

}
