package com.warehouse.service;

import com.warehouse.constants.SettingKeys;
import com.warehouse.constants.ShippingConstants;
import com.warehouse.entity.CargoProvider;
import com.warehouse.entity.CartItem;
import com.warehouse.entity.Product;
import com.warehouse.service.cargo.CargoPriceQuoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * What the shipping on an order costs — the one place that answers it.
 *
 * <p>There were four answers before this class, and they did not agree. The basket read a flat
 * site setting; the checkout validation endpoint used a hardcoded 29,99; the storefront's shipping
 * step displayed {@code baseCost} on its own; and the order that was actually charged added
 * {@code costPerDesi × desi} on top. With the tariffs in use — every carrier carries a per-desi
 * rate — a white-goods basket was shown one shipping price and billed another: an 80 desi parcel
 * on a 2,00 ₺/desi tariff is 160 ₺ of shipping that never appeared on the screen the customer
 * agreed to.
 *
 * <p>So the rule lives here and the screens ask for it rather than reproducing it. The order of
 * questions is fixed:
 *
 * <ol>
 *   <li>Does free shipping apply? One store-wide threshold, set in Site Ayarları → Kargo
 *       Ücretlendirme. Ours to promise, so it beats a live carrier price as well.</li>
 *   <li>Is there a live carrier price for this address? Only when live pricing is switched on and
 *       the destination is known.</li>
 *   <li>Otherwise the carrier's own tariff: base + desi × per-desi rate.</li>
 *   <li>With no carrier at all: the {@code default_shipping_cost} setting, then the constant.</li>
 * </ol>
 *
 * <p>A zero threshold means "no free shipping", never "everything ships free". {@link
 * CargoProvider#calculateShippingCost} used to read it the other way, so saving 0 in that field
 * would have made every order ship free.
 */
@Service
public class ShippingPriceService {

    private static final Logger logger = LoggerFactory.getLogger(ShippingPriceService.class);

    /** Volumetric divisor used across Turkish carriers: (L × W × H) / 3000. */
    private static final BigDecimal VOLUMETRIC_DIVISOR = new BigDecimal("3000");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final SiteSettingService settingService;
    private final CargoPriceQuoteService priceQuoteService;

    public ShippingPriceService(SiteSettingService settingService,
                                 CargoPriceQuoteService priceQuoteService) {
        this.settingService = settingService;
        this.priceQuoteService = priceQuoteService;
    }

    /** Where the number came from, so a screen can say so and a log can explain a surprise. */
    public enum Source { FREE, LIVE, TARIFF, DEFAULT }

    /**
     * A shipping price and everything a screen needs to show it.
     *
     * @param cost shipping excluding VAT; zero when free shipping applies
     * @param vat  VAT on that shipping, at the carrier's rate
     */
    public record Quote(BigDecimal cost, BigDecimal vat, boolean free, Source source) {
        public BigDecimal total() {
            return cost.add(vat);
        }
    }

    /**
     * The parcel size a basket ships as: physical weight or volume, whichever is larger, per item.
     *
     * <p>This is the figure the carrier charges on, so the basket, the checkout screen and the
     * order have to derive it the same way — it used to be computed inline in the order path only,
     * which is why no screen could show a desi-based price even in principle.
     */
    public BigDecimal desiOf(Collection<CartItem> items) {
        if (items == null || items.isEmpty()) return BigDecimal.ZERO;

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : items) {
            Product product = item.getProduct();
            if (product == null) continue;

            BigDecimal weight = product.getWeight() != null
                    ? BigDecimal.valueOf(product.getWeight()) : BigDecimal.ZERO;
            BigDecimal volumetric = BigDecimal.ZERO;
            if (product.getLengthCm() != null && product.getWidthCm() != null
                    && product.getHeightCm() != null) {
                volumetric = BigDecimal.valueOf(product.getLengthCm())
                        .multiply(BigDecimal.valueOf(product.getWidthCm()))
                        .multiply(BigDecimal.valueOf(product.getHeightCm()))
                        .divide(VOLUMETRIC_DIVISOR, 2, RoundingMode.HALF_UP);
            }
            total = total.add(weight.max(volumetric).multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        return total;
    }

    /**
     * The shipping price for this basket, with this carrier, to this address.
     *
     * @param provider the chosen carrier, or null before one is chosen
     * @param city     destination province; null skips live pricing
     * @param district destination district; null skips live pricing
     */
    public Quote quote(CargoProvider provider, BigDecimal subtotal, BigDecimal desi,
                        String city, String district) {
        BigDecimal basket = subtotal != null ? subtotal : BigDecimal.ZERO;

        BigDecimal threshold = freeShippingThreshold();
        if (threshold != null && basket.compareTo(threshold) >= 0) {
            return new Quote(BigDecimal.ZERO, BigDecimal.ZERO, true, Source.FREE);
        }

        BigDecimal live = livePrice(provider, desi, city, district);
        if (live != null) {
            return withVat(live, provider, Source.LIVE);
        }

        if (provider != null) {
            return withVat(tariff(provider, desi), provider, Source.TARIFF);
        }
        return withVat(defaultCost(), null, Source.DEFAULT);
    }

    /** Shipping for a basket where no carrier has been chosen yet. */
    public Quote quote(BigDecimal subtotal) {
        return quote(null, subtotal, BigDecimal.ZERO, null, null);
    }

    // ── steps ──

    /**
     * The one threshold the shop promises free shipping above. Null when it offers none.
     *
     * <p>Every carrier used to carry its own and it overrode the setting, so the number typed in
     * Site Ayarları was not the number customers got — which one applied depended on the carrier
     * they happened to pick. The column stays for a future carrier-specific campaign; the price
     * decision does not read it.
     */
    public BigDecimal freeShippingThreshold() {
        String global = settingService.getSetting(SettingKeys.FREE_SHIPPING_THRESHOLD);
        if (global != null && !global.isBlank()) {
            try {
                BigDecimal parsed = new BigDecimal(global.trim());
                return isPositive(parsed) ? parsed : null;
            } catch (NumberFormatException e) {
                logger.warn("[Kargo] free_shipping_threshold sayı değil: {}", global);
            }
        }
        // Boş alan "ücretsiz kargo yok" demek. Sabitteki 500'e düşmek, kimsenin girmediği bir
        // sözü sessizce vermek olurdu; alanı yanlışlıkla boşaltmanın bedeli de görünmez bir
        // gelir kaybı olurdu. Boşsa ücret alınır ve bu panelde görünür.
        return null;
    }

    /** The carrier's real price for this route, when live pricing is on and the address is known. */
    private BigDecimal livePrice(CargoProvider provider, BigDecimal desi, String city, String district) {
        if (provider == null || city == null || district == null) return null;
        if (!priceQuoteService.isEnabled()) return null;
        try {
            String slug = provider.getKargonomiSlug();
            if (slug != null && !slug.isBlank()) {
                return priceQuoteService.priceFor(city, district, desi, slug).orElse(null);
            }
            // No carrier mapping: the cheapest offer beats charging a tariff that may be nowhere
            // near what the delivery actually costs.
            return priceQuoteService.cheapest(city, district, desi)
                    .map(quote -> quote.price())
                    .orElse(null);
        } catch (Exception e) {
            logger.warn("[Kargo] canlı fiyat alınamadı, tarifeye düşülüyor: {}", e.toString());
            return null;
        }
    }

    /** base + desi × per-desi, the tariff written on the cargo companies screen. */
    private BigDecimal tariff(CargoProvider provider, BigDecimal desi) {
        BigDecimal cost = provider.getBaseCost() != null ? provider.getBaseCost() : BigDecimal.ZERO;
        if (provider.getCostPerDesi() != null && isPositive(desi)) {
            cost = cost.add(provider.getCostPerDesi().multiply(desi));
        }
        return cost.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultCost() {
        String global = settingService.getSetting(SettingKeys.DEFAULT_SHIPPING_COST);
        if (global != null && !global.isBlank()) {
            try {
                return new BigDecimal(global.trim());
            } catch (NumberFormatException e) {
                logger.warn("[Kargo] default_shipping_cost sayı değil: {}", global);
            }
        }
        return ShippingConstants.DEFAULT_SHIPPING_COST;
    }

    private Quote withVat(BigDecimal cost, CargoProvider provider, Source source) {
        BigDecimal net = cost != null ? cost.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal rate = provider != null ? provider.getVatRate() : null;
        BigDecimal vat = (rate == null || !isPositive(net))
                ? BigDecimal.ZERO
                : net.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return new Quote(net, vat, false, source);
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
