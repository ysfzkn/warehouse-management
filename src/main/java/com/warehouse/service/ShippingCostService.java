package com.warehouse.service;

import com.warehouse.constants.ShippingConstants;
import com.warehouse.entity.CargoProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Shipping cost calculation service.
 *
 * <p>Previously it worked with the hardcoded {@code ShippingConstants} values; this
 * service reads configurable values from SiteSetting and determines the price based on
 * the selected {@link CargoProvider}.</p>
 *
 * <p>Calculation logic:
 * <ol>
 *   <li>If the provider has a {@code freeShippingThreshold} field and the subtotal is above the threshold → 0</li>
 *   <li>Otherwise the global {@code free_shipping_threshold} site setting is checked</li>
 *   <li>If the threshold is not exceeded: provider.baseCost if > 0, otherwise the site setting {@code default_shipping_cost}</li>
 *   <li>If none apply, fallback: {@link ShippingConstants#DEFAULT_SHIPPING_COST}</li>
 * </ol>
 * Desi/weight-based surcharges (provider.costPerDesi) are not implemented in the MVP —
 * most e-commerce uses a flat fee; left open for future expansion.</p>
 */
@Service
public class ShippingCostService {

    private final SiteSettingService settingService;

    public ShippingCostService(SiteSettingService settingService) {
        this.settingService = settingService;
    }

    /**
     * Calculates the shipping cost for a subtotal + (optional) cargo provider.
     * @param subtotal cart amount (VAT included)
     * @param provider the selected cargo company (may be null)
     * @return BigDecimal — shipping cost (0 = free)
     */
    public BigDecimal calculate(BigDecimal subtotal, CargoProvider provider) {
        if (subtotal == null) subtotal = BigDecimal.ZERO;

        BigDecimal threshold = resolveThreshold(provider);
        if (threshold != null && threshold.compareTo(BigDecimal.ZERO) > 0
                && subtotal.compareTo(threshold) >= 0) {
            return BigDecimal.ZERO;
        }
        return resolveBaseCost(provider);
    }

    /** Threshold: provider's → site setting's → null. */
    private BigDecimal resolveThreshold(CargoProvider provider) {
        if (provider != null && provider.getFreeShippingThreshold() != null
                && provider.getFreeShippingThreshold().compareTo(BigDecimal.ZERO) > 0) {
            return provider.getFreeShippingThreshold();
        }
        String global = settingService.getSetting("free_shipping_threshold");
        if (global != null && !global.isBlank()) {
            try { return new BigDecimal(global.trim()); } catch (NumberFormatException ignored) {}
        }
        return ShippingConstants.FREE_SHIPPING_THRESHOLD;
    }

    /** Base cost: provider.baseCost → site setting → ShippingConstants. */
    private BigDecimal resolveBaseCost(CargoProvider provider) {
        if (provider != null && provider.getBaseCost() != null
                && provider.getBaseCost().compareTo(BigDecimal.ZERO) > 0) {
            return provider.getBaseCost();
        }
        String global = settingService.getSetting("default_shipping_cost");
        if (global != null && !global.isBlank()) {
            try { return new BigDecimal(global.trim()); } catch (NumberFormatException ignored) {}
        }
        return ShippingConstants.DEFAULT_SHIPPING_COST;
    }
}
