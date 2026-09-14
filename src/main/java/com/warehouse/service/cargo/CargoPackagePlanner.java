package com.warehouse.service.cargo;

import com.warehouse.entity.OrderItem;
import com.warehouse.entity.Product;
import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns order lines into a parcel plan.
 *
 * <p>The rule the carrier bills on is parcels, not units. Six cushions go in one box; six tables
 * do not. Previously every unit became its own parcel — a six-item order was quoted and labelled
 * as six shipments — so this decides:
 *
 * <ol>
 *   <li>An order-level override wins outright: an admin who counted the boxes is right.</li>
 *   <li>Products carrying {@code packagesPerUnit} travel alone, that many parcels per unit.</li>
 *   <li>Everything else is consolidated into as few parcels as
 *       {@code cargo_max_desi_per_package} allows.</li>
 * </ol>
 *
 * <p>Desi per unit is the greater of actual weight and volumetric weight (L×W×H / 3000), which is
 * how carriers in Turkey price. Products with no dimensions and no weight contribute nothing, so
 * a floor of {@value #MIN_PACKAGE_DESI} desi per parcel keeps a shipment from being declared
 * weightless — Kargonomi rejects a parcel at zero desi outright.
 */
@Service
public class CargoPackagePlanner {

    private static final Logger logger = LoggerFactory.getLogger(CargoPackagePlanner.class);

    /** Volumetric divisor used by Turkish carriers. */
    private static final BigDecimal VOLUMETRIC_DIVISOR = new BigDecimal("3000");

    /** No parcel is ever declared below this. */
    static final BigDecimal MIN_PACKAGE_DESI = new BigDecimal("1");

    /** Fallback ceiling for a consolidated parcel when the setting is missing or unusable. */
    static final BigDecimal DEFAULT_MAX_DESI = new BigDecimal("30");

    private final SiteSettingService settingService;

    public CargoPackagePlanner(SiteSettingService settingService) {
        this.settingService = settingService;
    }

    /**
     * @param items    the order's lines
     * @param override admin's parcel count for this order, or null to let the plan decide
     * @return at least one parcel, each with a positive desi
     */
    public List<CargoShipmentRequest.PackagePlan> plan(List<OrderItem> items, Integer override) {
        BigDecimal totalDesi = BigDecimal.ZERO;
        List<CargoShipmentRequest.PackagePlan> separate = new ArrayList<>();
        BigDecimal consolidatedDesi = BigDecimal.ZERO;
        boolean sawMissingDimensions = false;

        for (OrderItem item : items) {
            Product product = item.getProduct();
            int quantity = item.getQuantity() != null ? Math.max(1, item.getQuantity()) : 1;
            BigDecimal unitDesi = desiPerUnit(product);

            if (unitDesi.signum() <= 0) sawMissingDimensions = true;
            totalDesi = totalDesi.add(unitDesi.multiply(BigDecimal.valueOf(quantity)));

            int parcelsPerUnit = product != null && product.getPackagesPerUnit() != null
                    ? product.getPackagesPerUnit() : 0;

            if (parcelsPerUnit >= 1) {
                BigDecimal perParcel = unitDesi.divide(
                        BigDecimal.valueOf(parcelsPerUnit), 2, RoundingMode.UP);
                String content = product != null ? product.getName() : null;
                for (int i = 0; i < quantity * parcelsPerUnit; i++) {
                    separate.add(new CargoShipmentRequest.PackagePlan(atLeastMinimum(perParcel), content));
                }
            } else {
                consolidatedDesi = consolidatedDesi.add(unitDesi.multiply(BigDecimal.valueOf(quantity)));
            }
        }

        if (sawMissingDimensions) {
            logger.warn("Kargo desi hesabı eksik ürün ölçüsüyle yapıldı — ağırlık ve en/boy/yükseklik "
                    + "alanları boş ürünler var, kargo fiyatı olduğundan düşük çıkabilir.");
        }

        if (override != null && override >= 1) {
            return evenSplit(totalDesi, override);
        }

        List<CargoShipmentRequest.PackagePlan> plan = new ArrayList<>(separate);
        if (consolidatedDesi.signum() > 0) {
            plan.addAll(evenSplit(consolidatedDesi, consolidatedParcelCount(consolidatedDesi)));
        } else if (plan.isEmpty()) {
            // Nothing measurable at all — one parcel at the floor, rather than a rejected request.
            plan.add(new CargoShipmentRequest.PackagePlan(MIN_PACKAGE_DESI, null));
        }
        return plan;
    }

    /** Desi of a single unit: the greater of dead weight and volumetric weight. */
    private BigDecimal desiPerUnit(Product product) {
        if (product == null) return BigDecimal.ZERO;

        BigDecimal weight = product.getWeight() != null
                ? BigDecimal.valueOf(product.getWeight()) : BigDecimal.ZERO;

        BigDecimal volumetric = BigDecimal.ZERO;
        if (product.getLengthCm() != null && product.getWidthCm() != null && product.getHeightCm() != null) {
            volumetric = BigDecimal.valueOf(product.getLengthCm())
                    .multiply(BigDecimal.valueOf(product.getWidthCm()))
                    .multiply(BigDecimal.valueOf(product.getHeightCm()))
                    .divide(VOLUMETRIC_DIVISOR, 2, RoundingMode.HALF_UP);
        }
        return weight.max(volumetric);
    }

    /** How many parcels the consolidated pile needs at the configured ceiling. */
    private int consolidatedParcelCount(BigDecimal desi) {
        BigDecimal max = maxDesiPerPackage();
        return Math.max(1, desi.divide(max, 0, RoundingMode.UP).intValue());
    }

    private BigDecimal maxDesiPerPackage() {
        String raw = settingService.getSetting("cargo_max_desi_per_package");
        if (raw != null && !raw.isBlank()) {
            try {
                BigDecimal value = new BigDecimal(raw.trim());
                if (value.signum() > 0) return value;
            } catch (NumberFormatException ignored) {
                // fall through to the default
            }
        }
        return DEFAULT_MAX_DESI;
    }

    private List<CargoShipmentRequest.PackagePlan> evenSplit(BigDecimal totalDesi, int count) {
        int parcels = Math.max(1, count);
        BigDecimal each = totalDesi.divide(BigDecimal.valueOf(parcels), 2, RoundingMode.UP);
        List<CargoShipmentRequest.PackagePlan> plan = new ArrayList<>(parcels);
        for (int i = 0; i < parcels; i++) {
            plan.add(new CargoShipmentRequest.PackagePlan(atLeastMinimum(each), null));
        }
        return plan;
    }

    private BigDecimal atLeastMinimum(BigDecimal desi) {
        return desi.max(MIN_PACKAGE_DESI);
    }
}
