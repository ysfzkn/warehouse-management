package com.warehouse.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * Registers a {@link ShallowEtagHeaderFilter} scoped to the public catalog
 * endpoints. It computes an MD5 ETag over each response body and, when the client
 * sends a matching {@code If-None-Match}, returns an empty <em>304 Not Modified</em>
 * instead of the full payload — saving bandwidth on unchanged product/category data.
 *
 * <p>Scoped to catalog URLs only so the body-buffering cost is never paid on
 * large or streaming responses (images, exports, SSE) or on user-specific endpoints.
 * Works together with {@link com.warehouse.security.CacheControlFilter}, which sets
 * the {@code Cache-Control} freshness window.
 */
@Configuration
public class CatalogCacheConfig {

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> catalogEtagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> reg =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        reg.addUrlPatterns(
                "/api/store/products", "/api/store/products/*",
                "/api/store/categories", "/api/store/categories/*",
                "/api/store/pages", "/api/store/pages/*");
        reg.setName("catalogEtagFilter");
        reg.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return reg;
    }
}
