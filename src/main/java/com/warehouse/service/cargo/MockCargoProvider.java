package com.warehouse.service.cargo;

import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Mock kargo sağlayıcısı. Geliştirme ve test için.
 * Gerçek API çağrısı yapmaz, rastgele takip numarası üretir.
 */
@Component
public class MockCargoProvider implements CargoApiProvider {

    private static final Logger logger = LoggerFactory.getLogger(MockCargoProvider.class);

    private final SiteSettingService settingService;

    public MockCargoProvider(SiteSettingService settingService) {
        this.settingService = settingService;
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
}
