package com.warehouse.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * ShedLock — multi-instance scheduled job locking.
 *
 * <p>When Railway is scaled up, the same job must not run in parallel on
 * multiple instances (duplicate email sends, duplicate invoice issuance, etc.).
 * By keeping a record in the shedlock table, ShedLock guarantees that a job runs
 * on only one instance at a time.</p>
 *
 * <p>defaultLockAtMostFor = the maximum time the lock is held if the job crashes.
 * All jobs must finish before this duration; otherwise the lock is released
 * automatically (to avoid orphan locks).</p>
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M") // 10min default lock TTL
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
                        .usingDbTime() // Use DB time (prevents clock skew)
                        .build()
        );
    }
}
