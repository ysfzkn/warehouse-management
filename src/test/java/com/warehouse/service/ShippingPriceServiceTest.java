package com.warehouse.service;

import com.warehouse.constants.SettingKeys;
import com.warehouse.entity.CargoProvider;
import com.warehouse.entity.CartItem;
import com.warehouse.entity.Product;
import com.warehouse.service.cargo.CargoPriceQuoteService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The shipping price the customer is shown has to be the one the order charges.
 *
 * <p>It was not. The checkout screen displayed {@code baseCost} and the order added
 * {@code costPerDesi × desi} on top, so a white-goods basket — the kind this shop sells — was
 * billed far above the figure the customer agreed to. These tests pin the rule down in the one
 * place that now owns it, including the cases where the old copies disagreed with each other.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShippingPriceServiceTest {

    @Mock private SiteSettingService settingService;
    @Mock private CargoPriceQuoteService priceQuoteService;

    private ShippingPriceService shipping;

    @BeforeEach
    void setUp() {
        shipping = new ShippingPriceService(settingService, priceQuoteService);
        when(priceQuoteService.isEnabled()).thenReturn(false);
        // Mağaza geneli söz: bu tutarın üstünde kargo bizden.
        when(settingService.getSetting(SettingKeys.FREE_SHIPPING_THRESHOLD)).thenReturn("5000");
    }

    private CargoProvider carrier() {
        CargoProvider provider = new CargoProvider();
        provider.setName("Aras Kargo");
        provider.setBaseCost(new BigDecimal("29.99"));
        provider.setCostPerDesi(new BigDecimal("2.00"));
        provider.setFreeShippingThreshold(new BigDecimal("500.00"));
        provider.setVatRate(new BigDecimal("20.00"));
        return provider;
    }

    private CartItem item(double weight, int quantity) {
        Product product = new Product();
        product.setWeight(weight);
        CartItem cartItem = new CartItem();
        cartItem.setProduct(product);
        cartItem.setQuantity(quantity);
        return cartItem;
    }

    /** The difference that was being charged without being shown. */
    @Test
    @DisplayName("Desi ücreti fiyata giriyor")
    void theDesiSurchargeIsPartOfThePrice() {
        var quote = shipping.quote(carrier(), new BigDecimal("300"), new BigDecimal("80"), null, null);

        // 29,99 + 80 × 2,00
        assertThat(quote.cost()).isEqualByComparingTo("189.99");
        assertThat(quote.vat()).isEqualByComparingTo("38.00");
        assertThat(quote.source()).isEqualTo(ShippingPriceService.Source.TARIFF);
    }

    @Test
    @DisplayName("Eşiğin üstünde kargo ücretsiz, KDV de yok")
    void aboveTheThresholdShippingIsFree() {
        var quote = shipping.quote(carrier(), new BigDecimal("5200"), new BigDecimal("80"), null, null);

        assertThat(quote.free()).isTrue();
        assertThat(quote.cost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(quote.vat()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * {@code CargoProvider.calculateShippingCost} read a zero threshold as "free above zero",
     * which is every order there is. Saving 0 in that field to mean "we do not offer free
     * shipping" would have given the whole shop free delivery.
     */
    @Test
    @DisplayName("Sıfır eşik 'ücretsiz kargo yok' demek")
    void aZeroThresholdMeansNoFreeShippingRatherThanAlwaysFree() {
        when(settingService.getSetting(SettingKeys.FREE_SHIPPING_THRESHOLD)).thenReturn("0");

        var quote = shipping.quote(carrier(), new BigDecimal("10000"), BigDecimal.ONE, null, null);

        assertThat(quote.free()).isFalse();
        assertThat(quote.cost()).isEqualByComparingTo("31.99");
    }

    @Test
    @DisplayName("Canlı fiyat açıksa tarifenin yerine geçer")
    void aLivePriceReplacesTheTariff() {
        when(priceQuoteService.isEnabled()).thenReturn(true);
        when(priceQuoteService.cheapest(anyString(), anyString(), any()))
                .thenReturn(Optional.of(new com.warehouse.service.cargo.KargonomiCargoProvider.CarrierQuote(
                        4, "surat", "Sürat Kargo", new BigDecimal("95.58"), 3)));

        var quote = shipping.quote(carrier(), new BigDecimal("300"), BigDecimal.ONE, "İstanbul", "Kadıköy");

        assertThat(quote.cost()).isEqualByComparingTo("95.58");
        assertThat(quote.source()).isEqualTo(ShippingPriceService.Source.LIVE);
    }

    /** Free shipping is our promise, not the carrier's, so it outranks a live price too. */
    @Test
    @DisplayName("Ücretsiz kargo canlı fiyatın da önünde")
    void freeShippingOutranksALivePrice() {
        when(priceQuoteService.isEnabled()).thenReturn(true);

        var quote = shipping.quote(carrier(), new BigDecimal("5200"), BigDecimal.ONE, "İstanbul", "Kadıköy");

        assertThat(quote.free()).isTrue();
        assertThat(quote.source()).isEqualTo(ShippingPriceService.Source.FREE);
    }

    @Test
    @DisplayName("Firma seçilmeden önce ayardaki varsayılan ücret geçerli")
    void withNoCarrierTheConfiguredDefaultApplies() {
        when(settingService.getSetting(SettingKeys.DEFAULT_SHIPPING_COST)).thenReturn("34.50");

        var quote = shipping.quote(new BigDecimal("100"));

        assertThat(quote.cost()).isEqualByComparingTo("34.50");
        assertThat(quote.source()).isEqualTo(ShippingPriceService.Source.DEFAULT);
    }

    @Test
    @DisplayName("Bozuk ayar değeri ücreti sıfırlamaz")
    void anUnreadableSettingDoesNotCollapseThePrice() {
        when(settingService.getSetting(SettingKeys.DEFAULT_SHIPPING_COST)).thenReturn("otuz lira");
        when(settingService.getSetting(SettingKeys.FREE_SHIPPING_THRESHOLD)).thenReturn("beş yüz");

        var quote = shipping.quote(new BigDecimal("100"));

        assertThat(quote.cost()).isEqualByComparingTo("29.99");
    }

    /** Desi is per item and multiplied by quantity — the figure the carrier charges on. */
    @Test
    @DisplayName("Sepetin desisi adetle çarpılarak toplanır")
    void theBasketDesiCountsEveryUnit() {
        assertThat(shipping.desiOf(List.of(item(12.0, 2), item(5.5, 1))))
                .isEqualByComparingTo("29.5");
    }

    @Test
    @DisplayName("Hacimsel ağırlık daha büyükse o sayılır")
    void volumeWinsWhenItIsLargerThanWeight() {
        Product bulky = new Product();
        bulky.setWeight(3.0);
        bulky.setLengthCm(60.0);
        bulky.setWidthCm(60.0);
        bulky.setHeightCm(60.0);
        CartItem cartItem = new CartItem();
        cartItem.setProduct(bulky);
        cartItem.setQuantity(1);

        // 60 × 60 × 60 / 3000 = 72
        assertThat(shipping.desiOf(List.of(cartItem))).isEqualByComparingTo("72.00");
    }

    @Test
    @DisplayName("Boş sepetin desisi sıfır")
    void anEmptyBasketHasNoParcel() {
        assertThat(shipping.desiOf(List.of())).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(shipping.desiOf(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * Each carrier used to carry its own threshold and it beat the setting, so the promise a
     * customer got depended on which carrier they picked — and the number typed in Site Ayarları
     * was not the one that applied.
     */
    @Test
    @DisplayName("Firma bazlı limit artık kararı etkilemiyor, mağaza geneli limit geçerli")
    void aCarrierCannotOverrideTheStoreWidePromise() {
        CargoProvider provider = carrier();
        provider.setFreeShippingThreshold(new BigDecimal("500"));

        // Firma 500 diyor, mağaza 5000: 600 liralık sepet ücretsiz değil.
        assertThat(shipping.quote(provider, new BigDecimal("600"), BigDecimal.ONE, null, null).free())
                .isFalse();
        assertThat(shipping.quote(provider, new BigDecimal("5000"), BigDecimal.ONE, null, null).free())
                .isTrue();
    }

    /**
     * Production had this setting empty, and the old code fell back to a 500 ₺ constant — a
     * promise nobody had configured, given away on every order above 500 ₺ without appearing on
     * any screen. Emptying the field now simply means the shop does not offer free shipping.
     */
    @Test
    @DisplayName("Ayar boşsa ücretsiz kargo yok, gizli bir varsayılana düşülmüyor")
    void anEmptySettingMeansNoFreeShippingRatherThanAHiddenDefault() {
        when(settingService.getSetting(SettingKeys.FREE_SHIPPING_THRESHOLD)).thenReturn("");

        assertThat(shipping.freeShippingThreshold()).isNull();
        assertThat(shipping.quote(carrier(), new BigDecimal("100000"), BigDecimal.ONE, null, null).free())
                .isFalse();
    }
}
