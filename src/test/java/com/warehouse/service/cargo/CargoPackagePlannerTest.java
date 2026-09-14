package com.warehouse.service.cargo;

import com.warehouse.entity.OrderItem;
import com.warehouse.entity.Product;
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
import static org.mockito.Mockito.when;

/**
 * How many boxes actually leave the warehouse.
 *
 * <p>The behaviour being pinned: units are not parcels. Six cushions ship in one box; six tables
 * do not. The old code declared one parcel per unit, which multiplied the carrier's bill and the
 * number of labels printed by the quantity ordered.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CargoPackagePlannerTest {

    @Mock private SiteSettingService settingService;

    private CargoPackagePlanner planner;

    @BeforeEach
    void setUp() {
        when(settingService.getSetting("cargo_max_desi_per_package")).thenReturn("30");
        planner = new CargoPackagePlanner(settingService);
    }

    /** A small item: 2 kg, 20×20×20 cm → volumetric 2.67 desi, so 2.67 wins over the weight. */
    private Product smallItem() {
        Product p = new Product();
        p.setName("Kırlent");
        p.setWeight(2.0);
        p.setLengthCm(20.0);
        p.setWidthCm(20.0);
        p.setHeightCm(20.0);
        return p;
    }

    /** A table that travels on its own: 120×80×75 cm → 240 desi. */
    private Product tableThatShipsAlone() {
        Product p = new Product();
        p.setName("Yemek Masası");
        p.setWeight(35.0);
        p.setLengthCm(120.0);
        p.setWidthCm(80.0);
        p.setHeightCm(75.0);
        p.setPackagesPerUnit(1);
        return p;
    }

    private OrderItem line(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    private BigDecimal totalDesi(List<CargoShipmentRequest.PackagePlan> plan) {
        return plan.stream()
                .map(CargoShipmentRequest.PackagePlan::getDesi)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("Altı adet küçük ürün tek koli eder — adet başına koli açılmaz")
    void consolidatesSmallItemsIntoOneParcel() {
        List<CargoShipmentRequest.PackagePlan> plan = planner.plan(List.of(line(smallItem(), 6)), null);

        assertThat(plan).hasSize(1);
        // 6 × 2.67 = 16 desi — tek kolinin 30 desi sınırının altında
        assertThat(plan.get(0).getDesi()).isEqualByComparingTo("16.02");
    }

    @Test
    @DisplayName("Kendi başına taşınan ürün ayrı koli, gerisi tek kolide birleşir")
    void bulkyProductsGetTheirOwnParcel() {
        List<CargoShipmentRequest.PackagePlan> plan = planner.plan(
                List.of(line(tableThatShipsAlone(), 2), line(smallItem(), 3)), null);

        assertThat(plan).hasSize(3);           // 2 masa + 1 birleşik koli
        assertThat(plan.get(0).getContent()).isEqualTo("Yemek Masası");
        assertThat(plan.get(0).getDesi()).isEqualByComparingTo("240.00");
        assertThat(plan.get(2).getDesi()).isEqualByComparingTo("8.01");
    }

    @Test
    @DisplayName("Birleşik yığın koli sınırını aşarsa bölünür")
    void splitsConsolidatedLoadAtTheConfiguredCeiling() {
        // 20 × 2.67 = 53.4 desi → 30 desi sınırıyla iki koli
        List<CargoShipmentRequest.PackagePlan> plan = planner.plan(List.of(line(smallItem(), 20)), null);

        assertThat(plan).hasSize(2);
        assertThat(totalDesi(plan)).isEqualByComparingTo("53.40");
    }

    @Test
    @DisplayName("Admin koli sayısını ezerse plan ona uyar")
    void adminOverrideWins() {
        List<CargoShipmentRequest.PackagePlan> plan = planner.plan(List.of(line(smallItem(), 6)), 3);

        assertThat(plan).hasSize(3);
        assertThat(totalDesi(plan)).isGreaterThanOrEqualTo(new BigDecimal("16.02"));
    }

    @Test
    @DisplayName("Ölçüsü girilmemiş ürün sıfır desiyle gönderilmez")
    void productWithoutDimensionsStillGetsAPositiveDesi() {
        Product noDimensions = new Product();
        noDimensions.setName("Ölçüsüz ürün");

        List<CargoShipmentRequest.PackagePlan> plan = planner.plan(List.of(line(noDimensions, 2)), null);

        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).getDesi()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Ayarda saçma bir değer varsa varsayılan sınıra düşer")
    void fallsBackToTheDefaultCeilingOnAnUnusableSetting() {
        when(settingService.getSetting("cargo_max_desi_per_package")).thenReturn("sıfır");

        List<CargoShipmentRequest.PackagePlan> plan = planner.plan(List.of(line(smallItem(), 20)), null);

        assertThat(plan).hasSize(2);   // 53.4 desi / 30 varsayılan
    }
}
