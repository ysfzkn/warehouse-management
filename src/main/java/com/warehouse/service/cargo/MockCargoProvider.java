package com.warehouse.service.cargo;

import com.warehouse.entity.Order;
import com.warehouse.repository.OrderRepository;
import com.warehouse.service.SiteSettingService;
import com.warehouse.service.invoice.SimplePdfBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Mock kargo sağlayıcısı. Geliştirme ve test için.
 * Gerçek API çağrısı yapmaz, rastgele takip numarası üretir ve
 * etiket indirmek istenirse basit bir test PDF'i döner.
 */
@Component
public class MockCargoProvider implements CargoApiProvider {

    private static final Logger logger = LoggerFactory.getLogger(MockCargoProvider.class);

    private final SiteSettingService settingService;
    private final OrderRepository orderRepository;

    public MockCargoProvider(SiteSettingService settingService, OrderRepository orderRepository) {
        this.settingService = settingService;
        this.orderRepository = orderRepository;
    }

    @Override
    public String getProviderName() {
        return "MOCK";
    }

    @Override
    public boolean isEnabled() {
        String provider = settingService.getSetting("cargo_api_provider");
        return provider == null || provider.isBlank() || "MOCK".equalsIgnoreCase(provider);
    }

    @Override
    public CargoShipmentResult createShipment(CargoShipmentRequest request) {
        String trackingNumber = "MOCK" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        String providerId = "mock-" + System.currentTimeMillis();

        logger.info("[MOCK CARGO] Shipment created: orderNumber={}, trackingNumber={}, recipient={}",
                request.getOrderNumber(), trackingNumber, request.getRecipientName());

        return CargoShipmentResult.builder()
                .success(true)
                .trackingNumber(trackingNumber)
                .trackingUrl("https://example.com/tracking/" + trackingNumber)
                .carrierName("Mock Kargo")
                .carrierCode("mock")
                .providerShipmentId(providerId)
                .build();
    }

    @Override
    public CargoTrackingStatus getTrackingStatus(String trackingNumber) {
        logger.info("[MOCK CARGO] Tracking query: {}", trackingNumber);
        return CargoTrackingStatus.builder()
                .trackingNumber(trackingNumber)
                .status(CargoTrackingStatus.CargoStatus.IN_TRANSIT)
                .statusText("Kargo yolda (Mock)")
                .estimatedDelivery(LocalDateTime.now().plusDays(2))
                .events(List.of(
                    CargoTrackingStatus.TrackingEvent.builder()
                        .timestamp(LocalDateTime.now().minusHours(3))
                        .description("Gönderi oluşturuldu")
                        .status(CargoTrackingStatus.CargoStatus.CREATED)
                        .build(),
                    CargoTrackingStatus.TrackingEvent.builder()
                        .timestamp(LocalDateTime.now().minusHours(1))
                        .description("Kargo firmasına teslim edildi")
                        .status(CargoTrackingStatus.CargoStatus.IN_TRANSIT)
                        .build()
                ))
                .build();
    }

    @Override
    public CargoShipmentResult cancelShipment(String providerShipmentId) {
        logger.info("[MOCK CARGO] Shipment cancelled: {}", providerShipmentId);
        return CargoShipmentResult.builder()
                .success(true)
                .providerShipmentId(providerShipmentId)
                .build();
    }

    @Override
    public CargoShipmentResult createReturnShipment(CargoShipmentRequest request) {
        String trackingNumber = "MOCK-RET-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        logger.info("[MOCK CARGO] Return shipment created: {}", trackingNumber);
        return CargoShipmentResult.builder()
                .success(true)
                .trackingNumber(trackingNumber)
                .trackingUrl("https://example.com/tracking/" + trackingNumber)
                .carrierName("Mock Kargo")
                .carrierCode("mock")
                .providerShipmentId("mock-ret-" + System.currentTimeMillis())
                .build();
    }

    /**
     * MOCK için geçerli bir test etiketi PDF'i üretir (gerçek kargo firması
     * etiketi değil, sadece "akışın UI'da çalıştığını" doğrulamaya yarar).
     */
    public byte[] downloadLabelPdf(String providerShipmentId) {
        logger.info("[MOCK CARGO] Etiket PDF isteği: {}", providerShipmentId);
        Order order = orderRepository.findByCargoProviderShipmentId(providerShipmentId).orElse(null);

        SimplePdfBuilder pdf = new SimplePdfBuilder();
        int y = 800;

        pdf.line(50, y, "TEST KARGO ETIKETI (MOCK)", 20);
        y -= 22;
        pdf.line(50, y, "Bu etiket gecerli degildir — sadece test amacli olusturulmustur.", 9);
        y -= 30;

        pdf.line(50, y, "Takip No: " + (order != null && order.getCargoTrackingNo() != null
                ? order.getCargoTrackingNo() : "MOCK-UNKNOWN"), 14);
        y -= 22;

        if (order != null) {
            pdf.line(50, y, "Siparis: " + safe(order.getOrderNumber()));
            y -= 18;

            Object shippingAddr = order.getShippingAddressSnapshot();
            if (shippingAddr instanceof java.util.Map<?, ?> m) {
                String name = strFrom(m, "firstName") + " " + strFrom(m, "lastName");
                pdf.line(50, y, "ALICI", 13);
                y -= 18;
                pdf.line(50, y, name.trim());
                y -= 16;
                pdf.line(50, y, strFrom(m, "phone"));
                y -= 16;
                String addr = strFrom(m, "addressLine");
                if (!addr.isBlank()) {
                    pdf.line(50, y, truncate(addr, 70));
                    y -= 16;
                }
                pdf.line(50, y, strFrom(m, "district") + " / " + strFrom(m, "city")
                        + "  " + strFrom(m, "postalCode"));
                y -= 24;
            }

            pdf.line(50, y, "GONDERICI", 13);
            y -= 18;
            pdf.line(50, y, safe(settingService.getSetting("sender_name")));
            y -= 16;
            pdf.line(50, y, safe(settingService.getSetting("sender_phone")));
            y -= 16;
            pdf.line(50, y, safe(settingService.getSetting("sender_district")) + " / "
                    + safe(settingService.getSetting("sender_city")));
            y -= 20;

            pdf.line(50, y, "Desi: 1  |  Agirlik: 0.5 kg  |  Paket: 1");
            y -= 16;
            pdf.line(50, y, "Olusturulma: " + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        }

        // Alt uyari
        pdf.line(50, 40, "— MOCK KARGO — Gercek gonderi icin Kargonomi saglayicisina gecin —", 8);

        return pdf.build();
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String strFrom(java.util.Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : v.toString();
    }
    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
