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
    /** Kargonomi's own example always names the package contents; an unnamed parcel is refused. */
    private static final String PROBE_CONTENT = "Deneme gonderi";

    private final SiteSettingService settingService;
    private final CargoApiService cargoApiService;
    private final CargoSenderProfile senderProfile;
    private final KargonomiGeoLookupService geoLookup;

    public CargoDryRunService(SiteSettingService settingService, CargoApiService cargoApiService,
                               CargoSenderProfile senderProfile, KargonomiGeoLookupService geoLookup) {
        this.settingService = settingService;
        this.cargoApiService = cargoApiService;
        this.senderProfile = senderProfile;
        this.geoLookup = geoLookup;
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

        // A configured warehouse replaces the whole sender block: Kargonomi takes warehouse_id
        // and none of the six fields are sent, so checking them here would fail a trial that
        // would in fact succeed.
        String warehouseId = settingService.getSetting(SettingKeys.KARGONOMI_WAREHOUSE_ID);
        boolean usesWarehouse = warehouseId != null && !warehouseId.isBlank();

        if (usesWarehouse) {
            steps.add(new Step("Gönderici bilgileri", true,
                    "Kargonomi deposu kullanılıyor (id " + warehouseId.trim() + ")."));
        } else {
            // Kargonomi altı gönderici alanının hepsini istiyor ve biri eksikse isteğin tamamını
            // reddediyor — yalnızca ile bakmak, eksiği taslak adımına kadar gizliyordu.
            if (!senderProfile.isComplete()) {
                steps.add(new Step("Gönderici bilgileri", false,
                        "Eksik alan(lar): " + senderProfile.missingFields()
                        + ". Kargonomi bunlar olmadan gönderi oluşturmuyor."));
                return new Result(false, steps, List.of());
            }
            String senderCity = settingService.getSetting(SettingKeys.SENDER_CITY);
            String senderDistrict = settingService.getSetting(SettingKeys.SENDER_DISTRICT);
            steps.add(new Step("Gönderici bilgileri", true,
                    senderProfile.name() + " — " + senderCity + " / " + senderDistrict));

            // Filled in is not the same as recognised. Kargonomi keeps its own province and
            // district list, and a district it spells differently is dropped from the request —
            // which the carrier then reports as every sender field missing, pointing at the
            // wrong settings entirely.
            if (geoLookup.lookupStateAndCity(senderCity, senderDistrict) == null) {
                steps.add(new Step("Gönderici il/ilçe", false,
                        "Kargonomi \"" + senderCity + " / " + senderDistrict + "\" adresini "
                        + "tanımıyor. Ayarlardaki yazımı Kargonomi'nin listesine göre düzeltin."));
                return new Result(false, steps, List.of());
            }
            steps.add(new Step("Gönderici il/ilçe", true, "Kargonomi tanıdı."));

            // Filled in is not the same as dialable either. A number with an extension or a
            // second number beside it reaches the carrier as the wrong length and is refused.
            String phoneProblem = senderProfile.phoneProblem();
            if (phoneProblem != null) {
                steps.add(new Step("Gönderici telefon", false, phoneProblem));
                return new Result(false, steps, List.of());
            }
            steps.add(new Step("Gönderici telefon", true, senderProfile.dialledPhone()
                    + (senderProfile.hasMobilePhone() ? ""
                       : " — Kargonomi bu alanı \"Mobil\" olarak adlandırıyor, sabit hattı "
                         + "kabul etmeyebilir.")));
        }

        if (geoLookup.lookupStateAndCity(city, district) == null) {
            steps.add(new Step("Alıcı il/ilçe", false,
                    "Kargonomi \"" + city + " / " + district + "\" adresini tanımıyor. "
                    + "Deneme için il ve ilçeyi Kargonomi'nin yazdığı gibi girin."));
            return new Result(false, steps, List.of());
        }
        steps.add(new Step("Alıcı il/ilçe", true, "Kargonomi tanıdı."));

        CargoShipmentRequest probe = senderProfile.applyTo(CargoShipmentRequest.builder())
                .orderNumber(null)                 // no barcode: this draft is not an order
                .recipientName(PROBE_NAME)
                .recipientPhone(PROBE_PHONE)
                .recipientAddress(PROBE_ADDRESS)
                .recipientCity(city)
                .recipientDistrict(district)
                .recipientCountryCode("TR")
                .packageCount(1)
                .totalDesi(desi)
                .contentDescription(PROBE_CONTENT)
                .packages(List.of(new CargoShipmentRequest.PackagePlan(desi, PROBE_CONTENT)))
                .build();

        KargonomiCargoProvider.DraftAttempt attempt = provider.openDraft(probe);
        if (!attempt.ok()) {
            // The carrier's own words, not a pointer to a log the administrator cannot open.
            steps.add(new Step("Taslak gönderi", false, attempt.failure()));
            return new Result(false, steps, List.of());
        }
        String draftId = attempt.id();
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
