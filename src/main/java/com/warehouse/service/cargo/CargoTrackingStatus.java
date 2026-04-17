package com.warehouse.service.cargo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kargo takip durumu.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CargoTrackingStatus {

    /** Takip numarası */
    private String trackingNumber;

    /** Güncel durum kodu */
    private CargoStatus status;

    /** Ham durum metni (sağlayıcıdan geldiği gibi) */
    private String statusText;

    /** Teslimat tahmini tarihi */
    private LocalDateTime estimatedDelivery;

    /** Gerçek teslimat tarihi (DELIVERED ise) */
    private LocalDateTime deliveredAt;

    /** Teslim alan kişi (varsa) */
    private String deliveredTo;

    /** Tarihsel olaylar */
    private List<TrackingEvent> events;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingEvent {
        private LocalDateTime timestamp;
        private String description;
        private String location;
        private CargoStatus status;
    }

    public enum CargoStatus {
        /** Gönderi oluşturuldu, henüz teslim alınmadı */
        CREATED,
        /** Kargo firmasına teslim edildi */
        PICKED_UP,
        /** Transit / yolda */
        IN_TRANSIT,
        /** Dağıtım şubesinde */
        OUT_FOR_DELIVERY,
        /** Teslim edildi */
        DELIVERED,
        /** İade yolda */
        RETURN_IN_TRANSIT,
        /** İade tamamlandı */
        RETURNED,
        /** İptal edildi */
        CANCELLED,
        /** Sorunlu (teslim edilemedi, yanlış adres vb.) */
        FAILED,
        /** Bilinmeyen / haritalanamayan durum */
        UNKNOWN
    }
}
