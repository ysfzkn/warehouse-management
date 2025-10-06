package com.warehouse;

import com.warehouse.filter.SimpleAuthFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class WarehouseManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(WarehouseManagementApplication.class, args);
    }

    @Bean
    public FilterRegistrationBean<SimpleAuthFilter> simpleAuthFilter(SimpleAuthFilter filter) {
        FilterRegistrationBean<SimpleAuthFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(filter);
        reg.addUrlPatterns("/api/*");
        reg.setOrder(1);
        return reg;
    }

}
