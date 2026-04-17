package com.warehouse.dto.store;

import com.warehouse.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Halka açık sipariş takip için sadeleştirilmiş sipariş bilgisi.
 * Hassas bilgiler (adres, fatura no, iletişim) içermez - sadece durum bilgileri döner.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicOrderTrackingDto {

    private String orderNumber;
    private OrderStatus status;
    private String statusLabel;
    private LocalDateTime createdAt;

    // Teslimat tahmini
    private LocalDate estimatedDeliveryDate;
    private LocalDate actualDeliveryDate;

    // Kargo bilgileri
    private String cargoCompany;
    private String cargoProviderName;
    private String cargoTrackingNo;
    private String cargoTrackingUrl;

    // Toplam tutar (müşterinin siparişini doğrulaması için)
    private BigDecimal grandTotal;

    // Ürün sayısı (özet)
    private Integer itemCount;

    // Maskelenmiş müşteri adı (örn: "Ahmet Y***")
    private String maskedCustomerName;

    // Durum geçmişi timeline'ı
    private List<StatusHistoryItem> statusHistory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistoryItem {
        private String status;
        private String statusLabel;
        private LocalDateTime changedAt;
        private String note;
    }
}
