package com.warehouse.service.cargo;

import com.warehouse.entity.CargoProvider;
import com.warehouse.repository.CargoProviderRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.WarehouseRepository;
import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Everything that has to be true before the first real parcel goes out, checked in one go.
 *
 * <p>Going live means a handful of settings, a carrier account with money on it, a registered
 * webhook and products that have a size. Each of those fails quietly and in a different place —
 * a missing webhook secret returns 503 to the carrier, a missing size under-declares the parcel,
 * an unregistered webhook simply never arrives. This asks all of them at once and says which are
 * not ready yet.
 *
 * <p>Read-only: it inspects and reports, it never switches anything on.
 */
@Service
public class CargoReadinessService {

    private static final Logger logger = LoggerFactory.getLogger(CargoReadinessService.class);

    /** Where the carrier should be posting status updates. */
    public static final String WEBHOOK_PATH = "/api/public/cargo/kargonomi/webhook";

    private final SiteSettingService settingService;
    private final CargoApiService cargoApiService;
    private final CargoProviderRepository cargoProviderRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final KargonomiGeoLookupService geoLookup;

    public CargoReadinessService(SiteSettingService settingService,
                                  CargoApiService cargoApiService,
                                  CargoProviderRepository cargoProviderRepository,
                                  WarehouseRepository warehouseRepository,
                                  ProductRepository productRepository,
                                  KargonomiGeoLookupService geoLookup) {
        this.settingService = settingService;
        this.cargoApiService = cargoApiService;
        this.cargoProviderRepository = cargoProviderRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.geoLookup = geoLookup;
    }

    /** OK = ready, WARN = will work but something is worse than it should be, FAIL = blocks go-live. */
    public enum Level { OK, WARN, FAIL }

    public record Check(String key, String label, Level level, String detail, String fix) {}

    public record Report(Level overall, int passed, int total, List<Check> checks) {}

    public Report run() {
        List<Check> checks = new ArrayList<>();

        checks.add(providerSelected());
        checks.add(tokenAndBalance());
        checks.add(webhookSecret());
        checks.add(webhookRegistered());
        checks.add(senderAddress());
        checks.add(geoReachable());
        checks.add(carrierSlugs());
        checks.add(productDimensions());
        checks.add(autoCreate());

        long passed = checks.stream().filter(c -> c.level() == Level.OK).count();
        Level overall = checks.stream().anyMatch(c -> c.level() == Level.FAIL) ? Level.FAIL
                : checks.stream().anyMatch(c -> c.level() == Level.WARN) ? Level.WARN
                : Level.OK;

        return new Report(overall, (int) passed, checks.size(), checks);
    }

    // ── individual checks ────────────────────────────────────────

    private Check providerSelected() {
        boolean enabled = cargoApiService.isEnabled();
        String provider = settingService.getSetting("cargo_api_provider");

        if (!enabled) {
            return new Check("enabled", "Kargo entegrasyonu açık mı", Level.FAIL,
                    "cargo_api_enabled kapalı — hiçbir kargo işlemi yapılmıyor.",
                    "Ayarlar → Kargo API → entegrasyonu aç.");
        }
        if (!"KARGONOMI".equalsIgnoreCase(provider)) {
            return new Check("enabled", "Kargo entegrasyonu açık mı", Level.WARN,
                    "Aktif sağlayıcı: " + provider + " (Kargonomi değil).",
                    "Canlıya çıkarken cargo_api_provider = KARGONOMI yapın.");
        }
        return new Check("enabled", "Kargo entegrasyonu açık mı", Level.OK,
                "Kargonomi aktif.", null);
    }

    private Check tokenAndBalance() {
        String token = settingService.getSetting("kargonomi_api_token");
        if (token == null || token.isBlank()) {
            return new Check("token", "API token ve bakiye", Level.FAIL,
                    "kargonomi_api_token boş.",
                    "Kargonomi panelinden aldığınız token'ı Ayarlar → Kargo API'ye girin.");
        }

        CargoBalance balance = cargoApiService.getProviderBalance();
        return switch (balance.state()) {
            case OK -> balance.isDepleted()
                    ? new Check("token", "API token ve bakiye", Level.FAIL,
                        "Token çalışıyor ama bakiye " + balance.amount() + " TL.",
                        "Kargonomi hesabına bakiye yükleyin; bakiyesiz gönderi oluşturulamaz.")
                    : new Check("token", "API token ve bakiye", Level.OK,
                        "Token çalışıyor, bakiye " + balance.amount() + " TL.", null);
            case NOT_REPORTED -> new Check("token", "API token ve bakiye", Level.FAIL,
                    "Token çalışıyor (401 dönmedi) ama Kargonomi bakiye bildirmiyor.",
                    "Kargonomi hesabına bakiye yükleyip tekrar kontrol edin.");
            // Reddedilme ile ulaşamama ayrı ayrı raporlanıyor: ikisinin çaresi zıt, tek
            // mesajda birleştirilince admin hangisini düzelteceğini bilemiyordu.
            case REJECTED -> new Check("token", "API token ve bakiye", Level.FAIL,
                    "Kargonomi token'ı reddetti — " + balance.detail() + ".",
                    "Token hatalı, süresi dolmuş ya da başka bir hesaba ait. "
                    + "Kargonomi panelinden yeni token alıp Ayarlar → Kargo API'ye girin.");
            case UNREACHABLE -> new Check("token", "API token ve bakiye", Level.FAIL,
                    "Kargonomi'ye hiç ulaşılamadı" + (balance.detail() == null
                            ? "." : " — " + balance.detail() + "."),
                    "Token değil bağlantı sorunu: sunucunun dış ağ çıkışını ve "
                    + "kargonomi_api_base_url ayarını kontrol edin.");
            case UNSUPPORTED -> new Check("token", "API token ve bakiye", Level.WARN,
                    "Aktif sağlayıcı bakiye sorgusunu desteklemiyor.", null);
        };
    }

    private Check webhookSecret() {
        String secret = settingService.getSetting("kargonomi_webhook_secret");
        if (secret == null || secret.isBlank()) {
            return new Check("webhookSecret", "Webhook imza anahtarı", Level.FAIL,
                    "kargonomi_webhook_secret boş — webhook alıcısı gelen her bildirime 503 döner, "
                            + "kargo durumları hiç güncellenmez.",
                    "openssl rand -hex 32 ile üretip hem buraya hem Kargonomi webhook kaydına girin.");
        }
        if (secret.length() < 24) {
            return new Check("webhookSecret", "Webhook imza anahtarı", Level.WARN,
                    "Secret kısa (" + secret.length() + " karakter).",
                    "En az 32 karakterlik rastgele bir değer kullanın.");
        }
        return new Check("webhookSecret", "Webhook imza anahtarı", Level.OK, "Tanımlı.", null);
    }

    private Check webhookRegistered() {
        if (!(cargoApiService.getActiveProvider() instanceof KargonomiCargoProvider k)) {
            return new Check("webhook", "Kargonomi webhook kaydı", Level.WARN,
                    "Aktif sağlayıcı Kargonomi değil, kontrol edilemedi.", null);
        }
        try {
            List<Map<String, Object>> webhooks = k.listWebhooks();
            if (webhooks == null || webhooks.isEmpty()) {
                return new Check("webhook", "Kargonomi webhook kaydı", Level.FAIL,
                        "Kargonomi tarafında kayıtlı webhook yok — durum güncellemeleri yalnızca "
                                + "30 dakikalık yoklamayla gelir.",
                        "Ayarlardan webhook kaydı oluşturun (URL sonu: " + WEBHOOK_PATH + ").");
            }
            boolean pointsAtUs = webhooks.stream().anyMatch(w -> {
                Object url = w.get("url");
                return url != null && url.toString().contains(WEBHOOK_PATH);
            });
            if (!pointsAtUs) {
                return new Check("webhook", "Kargonomi webhook kaydı", Level.WARN,
                        webhooks.size() + " webhook kayıtlı ama hiçbiri bizim adresimize bakmıyor.",
                        "Webhook URL'i " + WEBHOOK_PATH + " ile bitmeli.");
            }
            return new Check("webhook", "Kargonomi webhook kaydı", Level.OK,
                    "Bize bakan webhook kaydı var.", null);
        } catch (Exception e) {
            logger.warn("Webhook kaydı kontrol edilemedi: {}", e.toString());
            return new Check("webhook", "Kargonomi webhook kaydı", Level.WARN,
                    "Kontrol edilemedi: " + e.getMessage(), null);
        }
    }

    private Check senderAddress() {
        String warehouseId = settingService.getSetting("kargonomi_warehouse_id");
        long mappedWarehouses = warehouseRepository.findAll().stream()
                .filter(w -> w.getKargonomiWarehouseId() != null && !w.getKargonomiWarehouseId().isBlank())
                .count();

        if ((warehouseId != null && !warehouseId.isBlank()) || mappedWarehouses > 0) {
            String detail = warehouseId != null && !warehouseId.isBlank()
                    ? "Global depo id: " + warehouseId
                    : mappedWarehouses + " depo Kargonomi ile eşleştirilmiş.";
            return new Check("sender", "Gönderici deposu", Level.OK, detail, null);
        }

        // No warehouse id anywhere — the sender has to be sent inline instead.
        List<String> missing = new ArrayList<>();
        for (String key : List.of("sender_name", "sender_phone", "sender_address",
                                   "sender_city", "sender_district")) {
            String value = settingService.getSetting(key);
            if (value == null || value.isBlank()) missing.add(key);
        }
        if (missing.isEmpty()) {
            return new Check("sender", "Gönderici deposu", Level.WARN,
                    "Kargonomi depo id'si yok; gönderici bilgileri her gönderide tek tek yollanacak.",
                    "Kargonomi'de depo oluşturup id'sini kaydetmek daha sağlam.");
        }
        return new Check("sender", "Gönderici deposu", Level.FAIL,
                "Ne depo id'si var ne de gönderici bilgileri tam. Eksik: " + String.join(", ", missing),
                "Ayarlar → Gönderici Bilgileri'ni doldurun ya da Kargonomi'de depo oluşturun.");
    }

    private Check geoReachable() {
        int states = geoLookup.states().size();
        if (states == 0) {
            return new Check("geo", "İl/ilçe listesi", Level.FAIL,
                    "Kargonomi il listesi alınamadı — checkout adres doğrulaması çalışmaz.",
                    "Token ve internet erişimini kontrol edin.");
        }
        if (states < 70) {
            return new Check("geo", "İl/ilçe listesi", Level.WARN,
                    states + " il geldi, 81 bekleniyordu.", null);
        }
        return new Check("geo", "İl/ilçe listesi", Level.OK, states + " il yüklü.", null);
    }

    private Check carrierSlugs() {
        List<CargoProvider> active = cargoProviderRepository.findByActiveTrueOrderBySortOrderAsc();
        if (active.isEmpty()) {
            return new Check("slugs", "Kargo firması eşlemeleri", Level.FAIL,
                    "Aktif kargo firması yok — müşteri checkout'ta firma seçemez.",
                    "Kargo Firmaları ekranından en az bir firma ekleyin.");
        }
        List<String> unmapped = active.stream()
                .filter(p -> p.getKargonomiSlug() == null || p.getKargonomiSlug().isBlank())
                .map(CargoProvider::getName)
                .toList();
        if (!unmapped.isEmpty()) {
            return new Check("slugs", "Kargo firması eşlemeleri", Level.WARN,
                    "Kargonomi slug'ı girilmemiş firmalar: " + String.join(", ", unmapped)
                            + ". Bunlarda Kargonomi otomatik en ucuzu seçer.",
                    "Kargo Firmaları ekranından slug girin (yurtici, aras, mng, …).");
        }
        return new Check("slugs", "Kargo firması eşlemeleri", Level.OK,
                active.size() + " firmanın tamamı eşleşmiş.", null);
    }

    private Check productDimensions() {
        long missing = productRepository.countWithoutShippingDimensions();
        if (missing == 0) {
            return new Check("dimensions", "Ürün ölçüleri", Level.OK,
                    "Satıştaki ürünlerin hepsinde ağırlık ya da ölçü var.", null);
        }
        return new Check("dimensions", "Ürün ölçüleri", Level.WARN,
                missing + " satıştaki üründe ne ağırlık ne de en/boy/yükseklik var; bu ürünler "
                        + "kargoya olduğundan hafif bildirilir ve fark faturaya yansır.",
                "Ürün ekranından ağırlık veya ölçü girin.");
    }

    private Check autoCreate() {
        boolean auto = cargoApiService.isAutoCreateEnabled();
        return new Check("autoCreate", "Otomatik gönderi oluşturma", auto ? Level.OK : Level.WARN,
                auto ? "Sipariş 'Kargoda' olunca gönderi otomatik oluşuyor."
                     : "Kapalı — gönderiler elle oluşturulacak.",
                auto ? null : "Ayarlar → Kargo API → cargo_api_auto_create.");
    }
}
