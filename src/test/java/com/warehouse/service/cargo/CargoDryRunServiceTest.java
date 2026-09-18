package com.warehouse.service.cargo;

import com.warehouse.constants.SettingKeys;
import com.warehouse.service.SiteSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The trial has one promise to keep: it must not cost anything and must not leave anything behind.
 *
 * <p>Kargonomi charges on {@code confirm-shipping-price}, so the draft must never be confirmed,
 * and the draft it opens has to be deleted on every path out — including the ones where the price
 * comparison failed, which is exactly when an early return is easiest to write by accident. A
 * trial that quietly accumulates drafts in the carrier's panel is worse than no trial.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CargoDryRunServiceTest {

    @Mock private SiteSettingService settingService;
    @Mock private CargoApiService cargoApiService;
    @Mock private KargonomiCargoProvider provider;

    private CargoDryRunService dryRun;

    private static final String DRAFT_ID = "9911";
    private static final BigDecimal ONE_DESI = BigDecimal.ONE;

    @BeforeEach
    void setUp() {
        dryRun = new CargoDryRunService(settingService, cargoApiService);
        when(cargoApiService.getActiveProvider()).thenReturn(provider);
        when(provider.isEnabled()).thenReturn(true);
        when(settingService.getSetting(SettingKeys.SENDER_CITY)).thenReturn("İstanbul");
        when(settingService.getSetting(SettingKeys.SENDER_DISTRICT)).thenReturn("Üsküdar");
    }

    @Test
    @DisplayName("Başarılı deneme fiyatları getirir ve taslağı siler")
    void aSuccessfulTrialReturnsPricesAndCleansUp() {
        when(provider.createDraftShipment(any())).thenReturn(DRAFT_ID);
        when(provider.fetchPriceComparison(DRAFT_ID)).thenReturn(List.of(
                new KargonomiCargoProvider.CarrierQuote(4, "aras", "Aras Kargo",
                        new BigDecimal("55.00"), 2)));
        when(provider.deleteShipment(DRAFT_ID)).thenReturn(true);

        CargoDryRunService.Result result = dryRun.run("İstanbul", "Kadıköy", ONE_DESI);

        assertThat(result.success()).isTrue();
        assertThat(result.quotes()).hasSize(1);
        verify(provider).deleteShipment(DRAFT_ID);
    }

    /**
     * {@code createShipment} is the only public way to reach {@code confirm-shipping-price}, which
     * is the call Kargonomi charges for — the confirm step itself is private and unreachable from
     * here. Asserting it is never used is what keeps "free" true as this service changes.
     */
    @Test
    @DisplayName("Deneme ücretli gönderi oluşturma yolunu hiç kullanmaz")
    void theTrialNeverTakesThePathThatSpends() {
        when(provider.createDraftShipment(any())).thenReturn(DRAFT_ID);
        when(provider.fetchPriceComparison(DRAFT_ID)).thenReturn(List.of());
        when(provider.deleteShipment(DRAFT_ID)).thenReturn(true);

        dryRun.run("İstanbul", "Kadıköy", ONE_DESI);

        verify(provider, never()).createShipment(any());
    }

    /**
     * An early return here is the easy mistake, and its cost is invisible: drafts pile up in the
     * carrier's panel and may count against a quota nobody told us about.
     */
    @Test
    @DisplayName("Fiyat alınamasa bile taslak silinir")
    void theDraftIsDeletedEvenWhenPricingFails() {
        when(provider.createDraftShipment(any())).thenReturn(DRAFT_ID);
        when(provider.fetchPriceComparison(DRAFT_ID))
                .thenThrow(new IllegalStateException("Kargonomi 500"));
        when(provider.deleteShipment(DRAFT_ID)).thenReturn(true);

        try {
            dryRun.run("İstanbul", "Kadıköy", ONE_DESI);
        } catch (RuntimeException expected) {
            // The failure may surface; the cleanup must happen regardless.
        }

        verify(provider).deleteShipment(DRAFT_ID);
    }

    @Test
    @DisplayName("Gönderici adresi boşken taslak hiç açılmaz")
    void nothingIsOpenedWithoutASenderAddress() {
        when(settingService.getSetting(SettingKeys.SENDER_CITY)).thenReturn("");

        CargoDryRunService.Result result = dryRun.run("İstanbul", "Kadıköy", ONE_DESI);

        assertThat(result.success()).isFalse();
        assertThat(result.steps()).anySatisfy(step ->
                assertThat(step.label()).isEqualTo("Gönderici adresi"));
        verify(provider, never()).createDraftShipment(any());
    }

    /**
     * Every failure has to say where the chain stopped, because the administrator reading this has
     * no other view into what the carrier rejected.
     */
    @Test
    @DisplayName("Taslak açılamazsa hangi adımda durduğu yazılır")
    void aRefusedDraftNamesTheStepThatFailed() {
        when(provider.createDraftShipment(any())).thenReturn(null);

        CargoDryRunService.Result result = dryRun.run("İstanbul", "Bilinmeyenİlçe", ONE_DESI);

        assertThat(result.success()).isFalse();
        assertThat(result.steps())
                .filteredOn(step -> !step.ok())
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.label()).isEqualTo("Taslak gönderi");
                    assertThat(step.detail()).isNotBlank();
                });
        verify(provider, never()).deleteShipment(anyString());
    }
}
