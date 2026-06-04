package com.warehouse.job;

import com.warehouse.service.SiteSettingService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Sitemap ping job (Phase 3 NICE-TO-HAVE).
 *
 * <p>Once a week, pings our sitemap URL to the Google Search Console + Bing IndexNow +
 * Yandex ping endpoints. This lets search engines discover new product/category
 * changes more quickly.</p>
 *
 * <p>Google: https://www.google.com/ping?sitemap=https://siteniz.com/sitemap.xml
 * Bing IndexNow: https://www.bing.com/IndexNow?... (modern alternative)
 * Yandex: https://webmaster.yandex.com/ping?sitemap=...</p>
 *
 * <p>If the {@code seo_canonical_domain} setting is empty, the job does nothing.</p>
 */
@Component
public class SitemapPingJob {

    private static final Logger log = LoggerFactory.getLogger(SitemapPingJob.class);

    private final SiteSettingService settingService;
    private final RestTemplate restTemplate = new RestTemplate();

    public SitemapPingJob(SiteSettingService settingService) {
        this.settingService = settingService;
    }

    /** Monday morning at 04:00 (Turkey) — low-traffic hour. */
    @Scheduled(cron = "0 0 4 * * MON", zone = "Europe/Istanbul")
    @SchedulerLock(name = "sitemapPing", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void pingSearchEngines() {
        String domain = settingService.getSetting("seo_canonical_domain");
        if (domain == null || domain.isBlank()) {
            log.info("[Sitemap] Ping atlandı — seo_canonical_domain ayarlanmamış.");
            return;
        }
        domain = domain.trim().replaceAll("/+$", "");
        if (!domain.startsWith("http")) domain = "https://" + domain;
        String sitemapUrl = domain + "/sitemap.xml";
        String encoded = java.net.URLEncoder.encode(sitemapUrl, java.nio.charset.StandardCharsets.UTF_8);

        ping("Google", "https://www.google.com/ping?sitemap=" + encoded);
        ping("Bing",   "https://www.bing.com/ping?sitemap=" + encoded);
        ping("Yandex", "https://webmaster.yandex.com/ping?sitemap=" + encoded);

        log.info("[Sitemap] Ping işlemi tamamlandı (sitemap={})", sitemapUrl);
    }

    private void ping(String engineName, String pingUrl) {
        try {
            restTemplate.getForObject(pingUrl, String.class);
            log.info("[Sitemap] {} ping başarılı", engineName);
        } catch (Exception e) {
            log.warn("[Sitemap] {} ping başarısız: {}", engineName, e.getMessage());
        }
    }
}
