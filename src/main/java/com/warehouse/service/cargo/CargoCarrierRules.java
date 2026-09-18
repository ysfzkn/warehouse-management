package com.warehouse.service.cargo;

import com.warehouse.entity.CargoProvider;
import com.warehouse.util.Locales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Whether a carrier may be used for a particular delivery.
 *
 * <p>"Cheapest wins" is only true until the cheapest carrier is the one that does not serve the
 * district, or refuses the parcel size — then it is the most expensive option there is, because
 * the parcel comes back and goes out again. Two constraints per carrier cover almost all of it
 * in practice: a size ceiling and a list of places it does not go.
 *
 * <p>Rules are on {@link CargoProvider}, so they are edited on the cargo companies screen that
 * already exists rather than in a new rules engine nobody maintains.
 */
@Service
public class CargoCarrierRules {

    private static final Logger logger = LoggerFactory.getLogger(CargoCarrierRules.class);

    /**
     * Can this carrier take this parcel to this address?
     *
     * @param desi total parcel size; null skips the size check
     */
    public boolean canCarry(CargoProvider provider, String city, String district, BigDecimal desi) {
        if (provider == null) return false;
        return withinSizeLimit(provider, desi) && servesDestination(provider, city, district);
    }

    /** Why the carrier was rejected, for the admin-facing message. Null when it was not. */
    public String rejectionReason(CargoProvider provider, String city, String district, BigDecimal desi) {
        if (provider == null) return "Kargo firması tanımsız";
        if (!withinSizeLimit(provider, desi)) {
            return provider.getName() + " en fazla " + provider.getMaxDesi() + " desi kabul ediyor";
        }
        if (!servesDestination(provider, city, district)) {
            return provider.getName() + " " + city + " / " + district + " adresine gitmiyor";
        }
        return null;
    }

    private boolean withinSizeLimit(CargoProvider provider, BigDecimal desi) {
        BigDecimal max = provider.getMaxDesi();
        if (max == null || max.signum() <= 0 || desi == null) return true;
        return desi.compareTo(max) <= 0;
    }

    /**
     * Matches both a whole province ("Hakkari") and a single district ("Şırnak/Cizre"), with
     * Turkish characters folded so the list can be typed however the admin types it.
     */
    private boolean servesDestination(CargoProvider provider, String city, String district) {
        String excluded = provider.getExcludedDistricts();
        if (excluded == null || excluded.isBlank()) return true;
        if (city == null || city.isBlank()) return true;

        String normalizedCity = normalize(city);
        String normalizedPair = normalizedCity + "/" + normalize(district != null ? district : "");

        for (String rawEntry : excluded.split(",")) {
            String entry = normalize(rawEntry);
            if (entry.isEmpty()) continue;

            if (entry.contains("/")) {
                if (entry.equals(normalizedPair)) {
                    logger.debug("[Kargo kuralı] {} → {} hariç tutulmuş", provider.getName(), rawEntry);
                    return false;
                }
            } else if (entry.equals(normalizedCity)) {
                logger.debug("[Kargo kuralı] {} → {} iline gitmiyor", provider.getName(), rawEntry);
                return false;
            }
        }
        return true;
    }

    /** "Şırnak / Cizre" and "sirnak/cizre" must be the same entry. */
    static String normalize(String value) {
        if (value == null) return "";
        String lower = value.trim().toLowerCase(Locales.TR)
                .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
                .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c');
        lower = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return lower.replaceAll("\\s*/\\s*", "/").replaceAll("\\s+", " ").trim();
    }
}
