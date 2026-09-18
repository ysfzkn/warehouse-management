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
    @Mock private KargonomiGeoLookupService geoLookup;

    private CargoDryRunService dryRun;

    private static final String DRAFT_ID = "9911";
    private static final BigDecimal ONE_DESI = BigDecimal.ONE;
    private static final int[] KNOWN_GEO = { 34, 1234 };

    @BeforeEach
    void setUp() {
        dryRun = new CargoDryRunService(settingService, cargoApiService,
                new CargoSenderProfile(settingService), geoLookup);
        when(cargoApiService.getActiveProvider()).thenReturn(provider);
        when(provider.isEnabled()).thenReturn(true);
        when(geoLookup.lookupStateAndCity(anyString(), anyString())).thenReturn(KNOWN_GEO);
        // Kargonomi altısını da istiyor; eksik olan biri isteğin tamamını reddettiriyor.
        when(settingService.getSetting(SettingKeys.SENDER_NAME)).thenReturn("Deneme Ticaret A.Ş.");
        when(settingService.getSetting(SettingKeys.SENDER_PHONE)).thenReturn("0555 111 22 33");
        when(settingService.getSetting(SettingKeys.SENDER_ADDRESS)).thenReturn("Merkez Mah. No 1");
        when(settingService.getSetting(SettingKeys.SENDER_CITY)).thenReturn("İstanbul");
        when(settingService.getSetting(SettingKeys.SENDER_DISTRICT)).thenReturn("Üsküdar");
        when(settingService.getSetting(SettingKeys.SENDER_TAX_NUMBER)).thenReturn("1234567890");
    }

    @Test
    @DisplayName("Başarılı deneme fiyatları getirir ve taslağı siler")
    void aSuccessfulTrialReturnsPricesAndCleansUp() {
        when(provider.openDraft(any())).thenReturn(opened(DRAFT_ID));
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
        when(provider.openDraft(any())).thenReturn(opened(DRAFT_ID));
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
        when(provider.openDraft(any())).thenReturn(opened(DRAFT_ID));
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

    /**
     * Kargonomi refuses the whole request when any of its six sender fields is missing, so finding
     * out at the draft step wastes a round trip and reports the wrong cause. The tax number is the
     * one most easily overlooked: it was never sent at all until the carrier named it.
     */
    @Test
    @DisplayName("Vergi no eksikse taslak hiç açılmaz ve eksik alan söylenir")
    void anIncompleteSenderIsCaughtBeforeAnyRequestIsMade() {
        when(settingService.getSetting(SettingKeys.SENDER_TAX_NUMBER)).thenReturn("");
        when(settingService.getSetting(SettingKeys.INVOICE_COMPANY_TAX_ID)).thenReturn("");

        CargoDryRunService.Result result = dryRun.run("İstanbul", "Kadıköy", ONE_DESI);

        assertThat(result.success()).isFalse();
        assertThat(result.steps())
                .filteredOn(step -> !step.ok())
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.label()).isEqualTo("Gönderici bilgileri");
                    assertThat(step.detail()).contains("vergi/kimlik no");
                });
        verify(provider, never()).openDraft(any());
    }

    /** The invoice tax id already on file stands in, so nobody types the same number twice. */
    @Test
    @DisplayName("Kargo vergi no boşsa fatura vergi no'su kullanılır")
    void theInvoiceTaxIdStandsInWhenNoCargoSpecificOneIsSet() {
        when(settingService.getSetting(SettingKeys.SENDER_TAX_NUMBER)).thenReturn("");
        when(settingService.getSetting(SettingKeys.INVOICE_COMPANY_TAX_ID)).thenReturn("9876543210");
        when(provider.openDraft(any())).thenReturn(opened(DRAFT_ID));
        when(provider.fetchPriceComparison(DRAFT_ID)).thenReturn(List.of(
                new KargonomiCargoProvider.CarrierQuote(4, "aras", "Aras Kargo",
                        new BigDecimal("55.00"), 2)));
        when(provider.deleteShipment(DRAFT_ID)).thenReturn(true);

        assertThat(dryRun.run("İstanbul", "Kadıköy", ONE_DESI).success()).isTrue();
    }

    /**
     * Every failure has to say where the chain stopped, because the administrator reading this has
     * no other view into what the carrier rejected.
     */
    @Test
    @DisplayName("Taslak açılamazsa hangi adımda durduğu yazılır")
    void aRefusedDraftNamesTheStepThatFailed() {
        when(provider.openDraft(any()))
                .thenReturn(refused("Kargonomi reddetti (HTTP 422): alıcı adresi eksik."));

        CargoDryRunService.Result result = dryRun.run("İstanbul", "Bilinmeyenİlçe", ONE_DESI);

        assertThat(result.success()).isFalse();
        assertThat(result.steps())
                .filteredOn(step -> !step.ok())
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.label()).isEqualTo("Taslak gönderi");
                    assertThat(step.detail()).contains("HTTP 422", "alıcı adresi eksik");
                });
        verify(provider, never()).deleteShipment(anyString());
    }

    private static KargonomiCargoProvider.DraftAttempt opened(String id) {
        return new KargonomiCargoProvider.DraftAttempt(id, null);
    }

    private static KargonomiCargoProvider.DraftAttempt refused(String reason) {
        return new KargonomiCargoProvider.DraftAttempt(null, reason);
    }

    /**
     * Kargonomi keeps its own province and district list, and silently drops a sender whose
     * district it spells differently — then reports all six sender fields as missing, which sends
     * the administrator to settings that are in fact filled in correctly. Catching it here names
     * the one thing that is wrong.
     */
    @Test
    @DisplayName("Gönderici ilçesi Kargonomi'de yoksa taslak açılmadan söylenir")
    void anUnrecognisedSenderDistrictIsNamedBeforeTheDraft() {
        when(geoLookup.lookupStateAndCity("NİĞDE", "MERKEZ")).thenReturn(null);
        when(settingService.getSetting(SettingKeys.SENDER_CITY)).thenReturn("NİĞDE");
        when(settingService.getSetting(SettingKeys.SENDER_DISTRICT)).thenReturn("MERKEZ");

        CargoDryRunService.Result result = dryRun.run("İstanbul", "Kadıköy", ONE_DESI);

        assertThat(result.success()).isFalse();
        assertThat(result.steps())
                .filteredOn(step -> !step.ok())
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.label()).isEqualTo("Gönderici il/ilçe");
                    assertThat(step.detail()).contains("NİĞDE", "MERKEZ");
                });
        verify(provider, never()).openDraft(any());
    }

    /**
     * With a warehouse configured Kargonomi takes {@code warehouse_id} and none of the six sender
     * fields are sent at all, so demanding them would fail a trial that would really succeed.
     */
    @Test
    @DisplayName("Depo tanımlıysa gönderici alanları aranmaz")
    void aConfiguredWarehouseReplacesTheSenderFields() {
        when(settingService.getSetting(SettingKeys.KARGONOMI_WAREHOUSE_ID)).thenReturn("42");
        when(settingService.getSetting(SettingKeys.SENDER_NAME)).thenReturn("");
        when(settingService.getSetting(SettingKeys.SITE_NAME)).thenReturn("");
        when(settingService.getSetting(SettingKeys.SENDER_TAX_NUMBER)).thenReturn("");
        when(settingService.getSetting(SettingKeys.INVOICE_COMPANY_TAX_ID)).thenReturn("");
        when(provider.openDraft(any())).thenReturn(opened(DRAFT_ID));
        when(provider.fetchPriceComparison(DRAFT_ID)).thenReturn(List.of(
                new KargonomiCargoProvider.CarrierQuote(4, "aras", "Aras Kargo",
                        new BigDecimal("55.00"), 2)));
        when(provider.deleteShipment(DRAFT_ID)).thenReturn(true);

        assertThat(dryRun.run("İstanbul", "Kadıköy", ONE_DESI).success()).isTrue();
    }

    /**
     * The carrier refused a real trial with "Gönderici Telefon 1 (Mobil) 10 rakam olmalıdır",
     * which is only readable once someone knows what we actually sent. The trial now says so
     * before the request leaves.
     */
    @Test
    @DisplayName("Telefon on haneye inmiyorsa taslak açılmadan söylenir")
    void aPhoneThatDoesNotReduceToTenDigitsStopsTheTrial() {
        when(settingService.getSetting(SettingKeys.SENDER_PHONE))
                .thenReturn("0555 111 22 33 dahili 12");

        CargoDryRunService.Result result = dryRun.run("İstanbul", "Kadıköy", ONE_DESI);

        assertThat(result.success()).isFalse();
        assertThat(result.steps())
                .filteredOn(step -> !step.ok())
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.label()).isEqualTo("Gönderici telefon");
                    assertThat(step.detail()).contains("10 hane");
                });
        verify(provider, never()).openDraft(any());
    }
}
