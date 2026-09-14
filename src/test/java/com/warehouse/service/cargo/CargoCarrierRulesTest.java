package com.warehouse.service.cargo;

import com.warehouse.entity.CargoProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which carrier may take which parcel where.
 *
 * <p>The point of these rules is that "cheapest" stops being cheapest the moment the parcel comes
 * back: a carrier that does not serve the district, or will not take the size, must not be offered.
 */
class CargoCarrierRulesTest {

    private CargoCarrierRules rules;

    @BeforeEach
    void setUp() {
        rules = new CargoCarrierRules();
    }

    private CargoProvider carrier(String name, String maxDesi, String excluded) {
        CargoProvider p = new CargoProvider();
        p.setName(name);
        if (maxDesi != null) p.setMaxDesi(new BigDecimal(maxDesi));
        p.setExcludedDistricts(excluded);
        return p;
    }

    @Test
    @DisplayName("Kuralsız firma her yere, her boyutta gider")
    void unconstrainedCarrierAlwaysQualifies() {
        CargoProvider p = carrier("Yurtiçi", null, null);

        assertThat(rules.canCarry(p, "İstanbul", "Kadıköy", new BigDecimal("120"))).isTrue();
        assertThat(rules.rejectionReason(p, "İstanbul", "Kadıköy", new BigDecimal("120"))).isNull();
    }

    @Test
    @DisplayName("Desi sınırını aşan gönderi o firmaya verilmiyor")
    void refusesParcelsOverTheSizeLimit() {
        CargoProvider p = carrier("Sürat", "50", null);

        assertThat(rules.canCarry(p, "İstanbul", "Kadıköy", new BigDecimal("50"))).isTrue();
        assertThat(rules.canCarry(p, "İstanbul", "Kadıköy", new BigDecimal("50.01"))).isFalse();
        assertThat(rules.rejectionReason(p, "İstanbul", "Kadıköy", new BigDecimal("80")))
                .contains("en fazla 50 desi");
    }

    @Test
    @DisplayName("Gitmediği il elenmiş oluyor")
    void excludesAWholeProvince() {
        CargoProvider p = carrier("Aras", null, "Hakkari, Şırnak");

        assertThat(rules.canCarry(p, "Hakkari", "Yüksekova", BigDecimal.ONE)).isFalse();
        assertThat(rules.canCarry(p, "İstanbul", "Kadıköy", BigDecimal.ONE)).isTrue();
    }

    @Test
    @DisplayName("Tek ilçe hariç tutulabiliyor, o ilin gerisi açık kalıyor")
    void excludesASingleDistrictWithoutTheProvince() {
        CargoProvider p = carrier("MNG", null, "Şırnak/Cizre");

        assertThat(rules.canCarry(p, "Şırnak", "Cizre", BigDecimal.ONE)).isFalse();
        assertThat(rules.canCarry(p, "Şırnak", "Silopi", BigDecimal.ONE)).isTrue();
    }

    @Test
    @DisplayName("Liste nasıl yazılırsa yazılsın eşleşiyor")
    void matchesHoweverTheListWasTyped() {
        CargoProvider p = carrier("PTT", null, " SIRNAK / cizre ,hakkari");

        assertThat(rules.canCarry(p, "Şırnak", "Cizre", BigDecimal.ONE)).isFalse();
        assertThat(rules.canCarry(p, "HAKKARİ", "Merkez", BigDecimal.ONE)).isFalse();
        assertThat(rules.canCarry(p, "Şırnak", "İdil", BigDecimal.ONE)).isTrue();
    }

    @Test
    @DisplayName("Desi bilinmiyorsa boyut kuralı engel olmuyor")
    void anUnknownSizeIsNotAReasonToRefuse() {
        CargoProvider p = carrier("Sürat", "50", null);

        assertThat(rules.canCarry(p, "İstanbul", "Kadıköy", null)).isTrue();
    }
}
