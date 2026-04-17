package com.warehouse.service.cargo;

/**
 * Kargo API sağlayıcı arayüzü.
 *
 * Türkiye'deki farklı kargo entegrasyonları bu arayüzü uygular:
 * - Kargonomi (multi-carrier aggregator)
 * - Yurtiçi Kargo (doğrudan)
 * - Aras Kargo (doğrudan)
 * - MNG (doğrudan)
 *
 * Yapılandırma site_settings tablosundan okunur.
 */
public interface CargoApiProvider {

    /**
     * Sağlayıcı adı. Örn: "KARGONOMI", "YURTICI", "ARAS", "MOCK".
     */
    String getProviderName();

    /**
     * Bu sağlayıcı aktif ve credential'ları yapılandırılmış mı?
     */
    boolean isEnabled();

    /**
     * Kargo gönderimi oluşturur.
     * Kargo firmasından takip numarası alır.
     */
    CargoShipmentResult createShipment(CargoShipmentRequest request);

    /**
     * Takip numarasına göre kargo durumunu sorgular.
     */
    CargoTrackingStatus getTrackingStatus(String trackingNumber);

    /**
     * Gönderimi iptal eder.
     */
    CargoShipmentResult cancelShipment(String providerShipmentId);

    /**
     * İade etiketi oluşturur (müşterinin ürünü iade etmesi için).
     */
    CargoShipmentResult createReturnShipment(CargoShipmentRequest request);
}
