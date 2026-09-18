package com.warehouse.service.cargo;

import com.warehouse.constants.SettingKeys;
import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Proves the whole shipment chain works without spending anything.
 *
 * <p>Kargonomi charges on the second step: creating a draft and asking for prices is free, and
 * only {@code confirm-shipping-price} takes money. So the sender address, the recipient's
 * province and district lookup, the parcel size and the carrier's own pricing can all be
 * exercised end to end and then withdrawn, leaving nothing behind.
 *
 * <p>This matters more than it sounds. The readiness check can only report what each setting
 * looks like in isolation; it cannot tell whether they work <em>together</em>. A sender address
 * that Kargonomi rejects, a district it spells differently, or an account whose balance the API
 * under-reports all pass every individual check and fail on the first real parcel. Here they
 * fail on a draft that costs nothing and is deleted either way.
 */
@Service
public class CargoDryRunService {

    private static final Logger logger = LoggerFactory.getLogger(CargoDryRunService.class);

    /** Placeholder recipient. Never shipped to — the draft is deleted before it can be. */
    private static final String PROBE_NAME = "Deneme Alici";
    private static final String PROBE_PHONE = "5000000000";
    private static final String PROBE_ADDRESS = "Deneme Mahallesi Deneme Sokak No 1 Daire 1";

    private final SiteSettingService settingService;
    private final CargoApiService cargoApiService;

    public CargoDryRunService(SiteSettingService settingService, CargoApiService cargoApiService) {
        this.settingService = settingService;
        this.cargoApiService = cargoApiService;
    }

    /**
     * One step of the trial and how it went.
     *
     * @param ok     false marks where the chain stopped
     * @param detail what happened, phrased for an administrator
     */
    public record Step(String label, boolean ok, String detail) {}

    public record Result(boolean success, List<Step> steps, List<KargonomiCargoProvider.CarrierQuote> quotes) {}

    /**
     * Opens a draft shipment to the given address, reads the carrier prices and deletes it again.
     *
     * @param city     recipient province, as it is written on an order
     * @param district recipient district
     * @param desi     parcel size; 1 is enough to get a quote
     */
    public Result run(String city, String district, BigDecimal desi) {
        List<Step> steps = new ArrayList<>();

        if (!(cargoApiService.getActiveProvider() instanceof KargonomiCargoProvider provider)) {
            steps.add(new Step("Sağlayıcı", false, "Aktif kargo sağlayıcısı Kargonomi değil."));
            return new Result(false, steps, List.of());
        }
        if (!provider.isEnabled()) {
            steps.add(new Step("Sağlayıcı", false,
                    "Kargo entegrasyonu kapalı ya da API token'ı boş."));
            return new Result(false, steps, List.of());
        }
        steps.add(new Step("Sağlayıcı", true, "Kargonomi aktif."));

        String senderCity = settingService.getSetting(SettingKeys.SENDER_CITY);
        String senderDistrict = settingService.getSetting(SettingKeys.SENDER_DISTRICT);
        if (senderCity == null || senderCity.isBlank()) {
            steps.add(new Step("Gönderici adresi", false,
                    "Gönderici il/ilçe ayarı boş — gönderi oluşturulamaz."));
            return new Result(false, steps, List.of());
        }
        steps.add(new Step("Gönderici adresi", true, senderCity + " / " + senderDistrict));

        CargoShipmentRequest probe = CargoShipmentRequest.builder()
                .orderNumber(null)                 // no barcode: this draft is not an order
                .recipientName(PROBE_NAME)
                .recipientPhone(PROBE_PHONE)
                .recipientAddress(PROBE_ADDRESS)
                .recipientCity(city)
                .recipientDistrict(district)
                .recipientCountryCode("TR")
                .packageCount(1)
                .totalDesi(desi)
                .packages(List.of(new CargoShipmentRequest.PackagePlan(desi, null)))
                .build();

        String draftId = provider.createDraftShipment(probe);
        if (draftId == null) {
            steps.add(new Step("Taslak gönderi", false,
                    "Kargonomi taslağı oluşturmadı. Alıcı il/ilçe tanınmamış ya da gönderici "
                    + "bilgileri kabul edilmemiş olabilir; sunucu günlüğünde sebebi yazıyor."));
            return new Result(false, steps, List.of());
        }
        steps.add(new Step("Taslak gönderi", true, "Oluşturuldu (id " + draftId + ") — ücretsiz."));

        List<KargonomiCargoProvider.CarrierQuote> quotes = List.of();
        try {
            quotes = provider.fetchPriceComparison(draftId);
            if (quotes.isEmpty()) {
                steps.add(new Step("Fiyat teklifi", false,
                        "Hiçbir kargo firması fiyat vermedi. Adres hizmet dışı bölge olabilir."));
            } else {
                steps.add(new Step("Fiyat teklifi", true,
                        quotes.size() + " firma fiyat verdi."));
            }
        } finally {
            // Deleted whichever way the comparison went: an abandoned draft clutters the
            // Kargonomi panel and may count against a quota we have not been told about.
            boolean deleted = provider.deleteShipment(draftId);
            steps.add(new Step("Taslağı sil", deleted,
                    deleted ? "Silindi — hesapta iz kalmadı."
                            : "Taslak silinemedi (id " + draftId + "). Kargonomi panelinden "
                              + "elle silin."));
            if (!deleted) logger.warn("[Kargo deneme] taslak silinemedi: {}", draftId);
        }

        boolean success = steps.stream().allMatch(Step::ok);
        logger.info("[Kargo deneme] {}/{} {} desi → {} teklif, sonuç={}",
                city, district, desi, quotes.size(), success);
        return new Result(success, steps, quotes);
    }
}
