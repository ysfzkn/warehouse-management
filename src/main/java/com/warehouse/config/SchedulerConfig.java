package com.warehouse.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * ShedLock — multi-instance scheduled job kilitlemesi.
 *
 * <p>Railway scale-up edildiğinde aynı job birden fazla instance'ta paralel
 * çalışmamalı (çift e-posta gönderimi, çift fatura kesimi vb.). ShedLock,
 * shedlock tablosunda kayıt tutarak bir job'ın aynı anda tek instance'ta
 * çalışmasını garanti eder.</p>
 *
 * <p>defaultLockAtMostFor = job'ın crash etmesi durumunda kilidin maksimum
 * tutulacağı süre. Tüm job'lar bu süreden önce bitmeli, aksi halde otomatik
 * unlock olur (orphan lock olmasın).</p>
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M") // 10dk default lock TTL
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
                        .usingDbTime() // DB time'ı kullan (clock skew önler)
                        .build()
        );
    }
}
