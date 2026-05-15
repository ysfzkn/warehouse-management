package com.warehouse.service;

import com.warehouse.constants.ShippingConstants;
import com.warehouse.entity.CargoProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Kargo ücreti hesaplama servisi.
 *
 * <p>Mevcut {@code ShippingConstants} hardcoded değerleri ile çalışıyordu; bu
 * servis SiteSetting'ten yapılandırılabilir değerleri okur ve seçili
 * {@link CargoProvider}'a göre fiyatı belirler.</p>
 *
 * <p>Hesaplama mantığı:
 * <ol>
 *   <li>Provider'ın {@code freeShippingThreshold} alanı varsa ve subtotal eşik üstündeyse → 0</li>
 *   <li>Yoksa global {@code free_shipping_threshold} site setting'i kontrol edilir</li>
 *   <li>Eşik aşılmadıysa: provider.baseCost > 0 ise o, değilse site setting {@code default_shipping_cost}</li>
 *   <li>Hiçbiri yoksa fallback: {@link ShippingConstants#DEFAULT_SHIPPING_COST}</li>
 * </ol>
 * Desi/ağırlık bazlı ek ücretlendirme (provider.costPerDesi) MVP'de uygulanmaz —
 * çoğu e-ticarette flat fee kullanılır, gelecek genişleme için açık bırakıldı.</p>
 */
@Service
public class ShippingCostService {

    private final SiteSettingService settingService;

    public ShippingCostService(SiteSettingService settingService) {
        this.settingService = settingService;
    }

    /**
     * Subtotal + (opsiyonel) cargo provider için kargo ücretini hesaplar.
     * @param subtotal sepet tutarı (KDV dahil)
     * @param provider seçili kargo şirketi (null olabilir)
     * @return BigDecimal — kargo bedeli (0 = ücretsiz)
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

    /** Eşik: provider'ın → site setting'in → null. */
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
