package com.warehouse.service.cargo;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Asking the carrier what a delivery actually costs.
 *
 * <p>Two things must hold, because each quote costs a draft shipment on Kargonomi's side: the
 * throwaway draft is always cleaned up, and similar baskets to the same district share one quote
 * instead of opening a draft per visitor.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CargoPriceQuoteServiceTest {

    @Mock private SiteSettingService settingService;
    @Mock private CargoApiService cargoApiService;
    @Mock private KargonomiCargoProvider provider;

    private CargoPriceQuoteService service;

    @BeforeEach
    void setUp() {
        when(cargoApiService.isEnabled()).thenReturn(true);
        when(cargoApiService.getActiveProvider()).thenReturn(provider);
        when(settingService.getSetting("cargo_checkout_live_pricing")).thenReturn("true");
        when(settingService.getSetting("cargo_price_cache_minutes")).thenReturn("720");

        when(provider.createDraftShipment(any())).thenReturn("DRAFT-1");
        when(provider.deleteShipment(anyString())).thenReturn(true);
        when(provider.fetchPriceComparison("DRAFT-1")).thenReturn(List.of(
                new KargonomiCargoProvider.CarrierQuote(1, "yurtici", "Yurtiçi Kargo", new BigDecimal("89.90"), 2),
                new KargonomiCargoProvider.CarrierQuote(2, "surat", "Sürat Kargo", new BigDecimal("71.50"), 3)));

        service = new CargoPriceQuoteService(settingService, cargoApiService);
    }

    @Test
    @DisplayName("Fiyat sorgusu taslağı her durumda siliniyor")
    void alwaysCleansUpTheThrowawayDraft() {
        service.quotes("İstanbul", "Kadıköy", new BigDecimal("4"));

        verify(provider).createDraftShipment(any());
        verify(provider).deleteShipment("DRAFT-1");
    }

    @Test
    @DisplayName("Karşılaştırma patlasa bile taslak siliniyor")
    void cleansUpEvenWhenTheComparisonFails() {
        when(provider.fetchPriceComparison("DRAFT-1")).thenThrow(new RuntimeException("500"));

        try {
            service.quotes("İstanbul", "Kadıköy", new BigDecimal("4"));
        } catch (RuntimeException expected) {
            // the failure itself is not what this test is about
        }
        verify(provider).deleteShipment("DRAFT-1");
    }

    @Test
    @DisplayName("Aynı ilçeye benzer paket ikinci kez taslak açmıyor")
    void reusesTheCachedQuote() {
        service.quotes("İstanbul", "Kadıköy", new BigDecimal("4"));
        service.quotes("istanbul", "KADIKÖY", new BigDecimal("4"));   // aynı yer, farklı yazım

        verify(provider, times(1)).createDraftShipment(any());
    }

    @Test
    @DisplayName("Aynı desi dilimindeki farklı sepetler tek fiyatı paylaşıyor")
    void similarParcelsShareABucket() {
        service.quotes("İstanbul", "Kadıköy", new BigDecimal("12.4"));
        service.quotes("İstanbul", "Kadıköy", new BigDecimal("14.9"));   // ikisi de 15 dilimi

        verify(provider, times(1)).createDraftShipment(any());
    }

    @Test
    @DisplayName("Belirli bir firmanın fiyatı seçilebiliyor, en ucuz bulunabiliyor")
    void picksACarrierAndTheCheapest() {
        assertThat(service.priceFor("İstanbul", "Kadıköy", new BigDecimal("4"), "yurtici"))
                .contains(new BigDecimal("89.90"));
        assertThat(service.priceFor("İstanbul", "Kadıköy", new BigDecimal("4"), "aras"))
                .isEmpty();
        assertThat(service.cheapest("İstanbul", "Kadıköy", new BigDecimal("4")))
                .get()
                .extracting(KargonomiCargoProvider.CarrierQuote::slug)
                .isEqualTo("surat");
    }

    @Test
    @DisplayName("Ayar kapalıyken Kargonomi'ye hiç dokunulmuyor")
    void doesNothingWhileTheFeatureIsOff() {
        when(settingService.getSetting("cargo_checkout_live_pricing")).thenReturn("false");

        assertThat(service.quotes("İstanbul", "Kadıköy", new BigDecimal("4"))).isEmpty();
        verify(provider, never()).createDraftShipment(any());
    }

    @Test
    @DisplayName("Adres eksikse fiyat sorulmuyor")
    void needsAFullDestination() {
        assertThat(service.quotes("İstanbul", null, new BigDecimal("4"))).isEmpty();
        assertThat(service.quotes(null, "Kadıköy", new BigDecimal("4"))).isEmpty();
        verify(provider, never()).createDraftShipment(any());
    }

    @Test
    @DisplayName("Desi dilimleri: 10'a kadar tam sayı, sonrası beşerli")
    void bucketsRoundUp() {
        assertThat(CargoPriceQuoteService.bucketDesi(new BigDecimal("0.4"))).isEqualByComparingTo("1");
        assertThat(CargoPriceQuoteService.bucketDesi(new BigDecimal("4.1"))).isEqualByComparingTo("5");
        assertThat(CargoPriceQuoteService.bucketDesi(new BigDecimal("10"))).isEqualByComparingTo("10");
        assertThat(CargoPriceQuoteService.bucketDesi(new BigDecimal("10.1"))).isEqualByComparingTo("15");
        assertThat(CargoPriceQuoteService.bucketDesi(new BigDecimal("31"))).isEqualByComparingTo("35");
    }
}
