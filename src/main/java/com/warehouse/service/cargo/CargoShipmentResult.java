package com.warehouse.service.cargo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kargo gönderimi oluşturma sonucu.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CargoShipmentResult {

    private boolean success;

    /** Kargo firması takip numarası (müşteriye gösterilecek) */
    private String trackingNumber;

    /** Kargo firmasının web sitesindeki takip URL'i */
    private String trackingUrl;

    /** Kargo firmasının adı (Yurtiçi, Aras, MNG vb.) */
    private String carrierName;

    /** Kargo firmasının kodu (yurtici, aras, mng vb.) */
    private String carrierCode;

    /** Etiket PDF URL'i (yazdırmak için) */
    private String labelUrl;

    /** Sağlayıcının sistem ID'si (iptal / sorgulama için) */
    private String providerShipmentId;

    /** Hata durumunda */
    private String errorCode;
    private String errorMessage;

    public static CargoShipmentResult success(String trackingNumber, String trackingUrl,
                                               String carrierName, String providerId) {
        return CargoShipmentResult.builder()
                .success(true)
                .trackingNumber(trackingNumber)
                .trackingUrl(trackingUrl)
                .carrierName(carrierName)
                .providerShipmentId(providerId)
                .build();
    }

    public static CargoShipmentResult failure(String errorCode, String errorMessage) {
        return CargoShipmentResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
