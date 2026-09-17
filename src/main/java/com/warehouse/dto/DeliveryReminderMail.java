package com.warehouse.dto;

import com.warehouse.enums.DeliveryReminderStage;

import java.util.List;

/**
 * Planlı bir teslimatın hatırlatma maline giren her şey, hazır biçimlenmiş hâlde.
 *
 * <p>Tarihler ve adetler burada metne çevrilmiş olarak duruyor; mail katmanı biçimlendirme
 * yapmıyor. Sebebi tek bir doğruluk noktası: aynı hatırlatma hem panele düşen bildirime hem
 * mail gövdesine yazılıyor, ve iki yerde ayrı ayrı biçimlendirilirse ikisi er geç farklı
 * tarih gösterir — hatırlatmanın tek işi doğru tarihi söylemek olduğu hâlde.</p>
 *
 * @param stage        hangi aşama (1 gün önce / teslim günü / gecikmiş)
 * @param transferId   sevkiyat kaydı — panel bağlantısı bunun üzerinden kuruluyor
 * @param receiptNo    depo çıkış makbuzu numarası; makbuz hiç basılmamışsa {@code null}
 * @param scheduledAt  planlanan teslim tarihi, gün.ay.yıl saat:dakika
 * @param daysLabel    "Yarın", "Bugün", "3 gün gecikti" gibi tek bakışta okunan ifade
 * @param items        ürün dökümü; teslimatı yapacak kişinin aracına ne yükleyeceği
 */
public record DeliveryReminderMail(
        DeliveryReminderStage stage,
        Long transferId,
        String receiptNo,
        String scheduledAt,
        String daysLabel,
        String customerFullName,
        String customerPhone,
        String customerAddress,
        String warehouseName,
        String handoverToName,
        String orderNumber,
        String notes,
        int totalQuantity,
        List<Line> items) {

    /** Tek bir ürün satırı. */
    public record Line(String sku, String name, Integer quantity) {}
}
