package com.warehouse.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * The HTTP client every outbound integration uses.
 *
 * <p>Eight services were each calling {@code new RestTemplate()}, which has **no timeouts at
 * all**. A carrier, SMS gateway or e-invoice provider that accepts a connection and then stops
 * responding holds the calling thread indefinitely; enough of those and the request threads are
 * gone, which takes down endpoints that have nothing to do with the integration that stalled.
 * That is how a third party's bad afternoon becomes our outage.
 *
 * <p>The timeouts below are deliberately short. These calls sit in front of a user waiting on a
 * page or a scheduled job that will run again shortly — waiting a minute helps nobody. Work that
 * genuinely must survive a slow provider belongs in the outbox, where it is retried, rather than
 * in a longer timeout.
 */
@Configuration
public class RestTemplateConfig {

    /** Long enough for a TLS handshake over a poor link, short enough to fail fast. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** Kargonomi's price comparison is the slowest call we make and answers well inside this. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // setConnectTimeout / setReadTimeout on Spring Boot 3.3; the shorter names arrive in 3.4.
        return builder
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .build();
    }
}
