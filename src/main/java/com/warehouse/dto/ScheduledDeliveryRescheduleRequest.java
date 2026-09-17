package com.warehouse.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Planlı bir teslimatın tarihinin değiştirilmesi.
 *
 * <p>İptal edip yeniden açmanın alternatifi. O yol aynı mal için ikinci bir makbuz numarası
 * üretir ve müşterinin elindeki kâğıt sistemde karşılıksız kalırdı; burada sevkiyat,
 * rezervasyon ve makbuz kimliği yerinde duruyor, yalnızca tarih ve hatırlatma damgaları
 * yenileniyor.</p>
 */
@Data
public class ScheduledDeliveryRescheduleRequest {

    @NotNull(message = "Yeni teslim tarihi zorunludur")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledDeliveryAt;

    /** Denetim kaydına ve bildirime yazılır; ertelemenin neden yapıldığı sonradan sorulur. */
    @Size(max = 300, message = "Sebep 300 karakteri aşamaz")
    private String reason;
}
