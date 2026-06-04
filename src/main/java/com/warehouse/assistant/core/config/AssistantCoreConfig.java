package com.warehouse.assistant.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Central Spring configuration for the assistant core.
 * <p>
 * Most beans under {@code com.warehouse.assistant.core.*} are component-scanned
 * directly; this class only exists to enable {@link AssistantProperties} and
 * async execution (used by {@code ConversationLogger} to persist messages off
 * the request thread).
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantCoreConfig {
}
