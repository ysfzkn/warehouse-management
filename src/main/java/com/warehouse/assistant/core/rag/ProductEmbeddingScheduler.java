package com.warehouse.assistant.core.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly full re-index of all product embeddings. Runs at 03:17 every day
 * (17 minutes past the hour to avoid Railway's :00/:30 traffic spikes).
 * <p>
 * The backfill is idempotent: products whose text content hasn't changed
 * since the last embedding are skipped (content-hash check), so repeated
 * runs burn near-zero Azure tokens.
 */
@Component
@Profile("!test")
public class ProductEmbeddingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProductEmbeddingScheduler.class);

    private final ProductEmbeddingBackfillService backfillService;

    public ProductEmbeddingScheduler(ProductEmbeddingBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @Scheduled(cron = "0 17 3 * * *")
    public void nightlyReindex() {
        log.info("Nightly product embedding backfill started.");
        try {
            int count = backfillService.backfillAll();
            log.info("Nightly product embedding backfill completed. Processed: {}", count);
        } catch (Exception e) {
            log.error("Nightly product embedding backfill failed: {}", e.getMessage(), e);
        }
    }
}
