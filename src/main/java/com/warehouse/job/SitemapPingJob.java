package com.warehouse.job;

import com.warehouse.service.SiteSettingService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Sitemap ping job (Faz 3 NICE-TO-HAVE).
 *
 * <p>Haftada bir Google Search Console + Bing IndexNow + Yandex ping endpoint'lerine
 * sitemap URL'imizi pingler. Bu sayede arama motorları yeni ürün/kategori
 * değişikliklerini daha hızlı keşfeder.</p>
 *
 * <p>Google: https://www.google.com/ping?sitemap=https://siteniz.com/sitemap.xml
 * Bing IndexNow: https://www.bing.com/IndexNow?... (modern alternatif)
 * Yandex: https://webmaster.yandex.com/ping?sitemap=...</p>
 *
 * <p>{@code seo_canonical_domain} setting boşsa job hiçbir şey yapmaz.</p>
 */
@Component
public class SitemapPingJob {

    private static final Logger log = LoggerFactory.getLogger(SitemapPingJob.class);

    private final SiteSettingService settingService;
    private final RestTemplate restTemplate = new RestTemplate();

    public SitemapPingJob(SiteSettingService settingService) {
        this.settingService = settingService;
    }

    /** Pazartesi sabah 04:00'te (Türkiye) — düşük trafik saati. */
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
