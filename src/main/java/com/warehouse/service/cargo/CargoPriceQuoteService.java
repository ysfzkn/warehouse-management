package com.warehouse.service.cargo;

import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the carriers would actually charge to deliver this basket to this address.
 *
 * <p>Until now the storefront quoted a flat fee (or a desi tariff typed into
 * {@code cargo_providers}) while the real cost only appeared on the Kargonomi invoice weeks
 * later. Kargonomi will price a shipment, but only one that exists — so a quote means creating
 * a draft, reading the comparison, and deleting the draft again.
 *
 * <p><b>Off by default.</b> Every cache miss creates and deletes a draft on Kargonomi's side,
 * and whether that is rate-limited or billed is not documented. Ask Kargonomi before switching
 * {@code cargo_checkout_live_pricing} on.
 *
 * <p>What keeps the draft count down is the cache key: price depends on the destination district
 * and the size of the parcel, not on who is buying. Quotes are therefore shared across every
 * customer shipping a similar parcel to the same district, and the placeholder recipient on the
 * draft means no customer's details are posted for a price check.
 */
@Service
public class CargoPriceQuoteService {

    private static final Logger logger = LoggerFactory.getLogger(CargoPriceQuoteService.class);

    private static final long DEFAULT_CACHE_MINUTES = 720;   // 12 hours

    /** Placeholder recipient for the throwaway draft; passes Kargonomi's field validation. */
    private static final String QUOTE_NAME = "Fiyat Sorgulama";
    private static final String QUOTE_PHONE = "5000000000";
    private static final String QUOTE_ADDRESS = "Fiyat sorgulama amacli olusturulan gecici kayit";

    private final SiteSettingService settingService;
    private final CargoApiService cargoApiService;

    private final Map<String, CachedQuotes> cache = new ConcurrentHashMap<>();

    public CargoPriceQuoteService(SiteSettingService settingService, CargoApiService cargoApiService) {
        this.settingService = settingService;
        this.cargoApiService = cargoApiService;
    }

    private record CachedQuotes(List<KargonomiCargoProvider.CarrierQuote> quotes, long expiresAt) {}

    public boolean isEnabled() {
        return cargoApiService.isEnabled()
                && "true".equalsIgnoreCase(settingService.getSetting("cargo_checkout_live_pricing"))
                && cargoApiService.getActiveProvider() instanceof KargonomiCargoProvider;
    }

    /**
     * Live prices for delivering a parcel of {@code desi} to this district, one per carrier.
     * Empty when live pricing is off, the address is incomplete, or the carrier did not answer —
     * every caller must be able to fall back to its own tariff.
     */
    public List<KargonomiCargoProvider.CarrierQuote> quotes(String city, String district, BigDecimal desi) {
        if (!isEnabled()) return List.of();
        if (city == null || city.isBlank() || district == null || district.isBlank()) return List.of();
        if (desi == null || desi.signum() <= 0) desi = BigDecimal.ONE;

        String key = cacheKey(city, district, desi);
        CachedQuotes cached = cache.get(key);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
            return cached.quotes();
        }

        List<KargonomiCargoProvider.CarrierQuote> quotes = fetchQuotes(city, district, bucketDesi(desi));
        cache.put(key, new CachedQuotes(quotes,
                System.currentTimeMillis() + cacheMinutes() * 60_000));
        evictExpired();
        return quotes;
    }

    /** The price for one specific carrier, when it offers this route at all. */
    public Optional<BigDecimal> priceFor(String city, String district, BigDecimal desi, String carrierSlug) {
        if (carrierSlug == null || carrierSlug.isBlank()) return Optional.empty();
        return quotes(city, district, desi).stream()
                .filter(q -> carrierSlug.equalsIgnoreCase(q.slug()))
                .map(KargonomiCargoProvider.CarrierQuote::price)
                .findFirst();
    }

    /** The cheapest offer for this route, for a "from X TL" line. */
    public Optional<KargonomiCargoProvider.CarrierQuote> cheapest(String city, String district, BigDecimal desi) {
        return quotes(city, district, desi).stream()
                .min((a, b) -> a.price().compareTo(b.price()));
    }

    // ─────────────────────────────────────────────────────────────

    private List<KargonomiCargoProvider.CarrierQuote> fetchQuotes(String city, String district, BigDecimal desi) {
        if (!(cargoApiService.getActiveProvider() instanceof KargonomiCargoProvider provider)) {
            return List.of();
        }

        CargoShipmentRequest probe = CargoShipmentRequest.builder()
                .orderNumber(null)                       // no barcode: this draft is not an order
                .recipientName(QUOTE_NAME)
                .recipientPhone(QUOTE_PHONE)
                .recipientAddress(QUOTE_ADDRESS)
                .recipientCity(city)
                .recipientDistrict(district)
                .recipientCountryCode("TR")
                .packageCount(1)
                .totalDesi(desi)
                .packages(List.of(new CargoShipmentRequest.PackagePlan(desi, null)))
                .build();

        String draftId = provider.createDraftShipment(probe);
        if (draftId == null) return List.of();

        try {
            List<KargonomiCargoProvider.CarrierQuote> quotes = provider.fetchPriceComparison(draftId);
            logger.debug("[Kargo fiyat] {}/{} {} desi → {} teklif", city, district, desi, quotes.size());
            return quotes;
        } finally {
            // Always clean up, including when the comparison failed: an abandoned draft is
            // clutter in the Kargonomi panel and possibly counts against a quota.
            if (!provider.deleteShipment(draftId)) {
                logger.warn("[Kargo fiyat] geçici taslak silinemedi: {}", draftId);
            }
        }
    }

    /**
     * Rounds the parcel up to a size band, so a thousand slightly different baskets to the same
     * district share one quote instead of opening a thousand drafts. Small parcels get whole-desi
     * bands where price changes fastest; larger ones get bands of five.
     */
    static BigDecimal bucketDesi(BigDecimal desi) {
        BigDecimal ceil = desi.setScale(0, RoundingMode.CEILING);
        if (ceil.compareTo(BigDecimal.TEN) <= 0) return ceil;
        BigDecimal five = new BigDecimal("5");
        return ceil.divide(five, 0, RoundingMode.CEILING).multiply(five);
    }

    private String cacheKey(String city, String district, BigDecimal desi) {
        return normalize(city) + "|" + normalize(district) + "|" + bucketDesi(desi).toPlainString();
    }

    /** Folds Turkish characters and case so "İstanbul" and "istanbul" share a cache entry. */
    private static String normalize(String value) {
        String lower = value.trim()
                .replace('İ', 'i').replace('I', 'i').replace('ı', 'i')
                .replace('Ş', 's').replace('ş', 's')
                .replace('Ğ', 'g').replace('ğ', 'g')
                .replace('Ü', 'u').replace('ü', 'u')
                .replace('Ö', 'o').replace('ö', 'o')
                .replace('Ç', 'c').replace('ç', 'c')
                .toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private long cacheMinutes() {
        String raw = settingService.getSetting("cargo_price_cache_minutes");
        if (raw != null && !raw.isBlank()) {
            try {
                long value = Long.parseLong(raw.trim());
                if (value > 0) return value;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return DEFAULT_CACHE_MINUTES;
    }

    private void evictExpired() {
        if (cache.size() < 500) return;
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }
}
