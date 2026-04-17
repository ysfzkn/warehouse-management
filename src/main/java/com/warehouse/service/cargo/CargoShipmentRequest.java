package com.warehouse.service.cargo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kargo gönderimi oluşturma isteği. Provider-agnostik format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CargoShipmentRequest {

    /** Sipariş numarası - referans için (bizim sistemde) */
    private String orderNumber;

    /** İç sipariş ID */
    private Long orderId;

    // --- Alıcı bilgileri ---
    private String recipientName;
    private String recipientPhone;
    private String recipientEmail;

    // --- Teslimat adresi ---
    private String recipientAddress;
    private String recipientCity;
    private String recipientDistrict;
    private String recipientPostalCode;
    /** "TR" */
    private String recipientCountryCode;

    // --- Gönderici (mağaza) bilgileri ---
    private String senderName;
    private String senderPhone;
    private String senderAddress;
    private String senderCity;
    private String senderDistrict;
    private String senderPostalCode;

    // --- Paket bilgileri ---
    /** Paket sayısı (birden fazla koli olabilir) */
    private Integer packageCount;
    /** Toplam ağırlık (kg) */
    private BigDecimal totalWeightKg;
    /** Toplam desi (hacim ağırlığı) */
    private BigDecimal totalDesi;
    /** Paket içeriği açıklaması */
    private String contentDescription;

    /** Sipariş tutarı (kapıda ödeme için önemli) */
    private BigDecimal orderAmount;

    /** Kapıda ödeme? */
    private boolean cashOnDelivery;
    private BigDecimal cashOnDeliveryAmount;

    // --- Kargo tercih ---
    /** Kargonomi için: belirli bir taşıyıcı tercihi ("yurtici", "aras" vb.) */
    private String preferredCarrier;
    /** Servis tipi: STANDARD, EXPRESS, NEXT_DAY vb. */
    private String serviceType;

    /** Ek notlar (teslimatçıya) */
    private String deliveryNote;

    /** Sipariş kalemleri (opsiyonel - bazı API'ler detay ister) */
    private List<ShipmentItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShipmentItem {
        private String productName;
        private String sku;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
