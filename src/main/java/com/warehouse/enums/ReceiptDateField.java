package com.warehouse.enums;

/**
 * Makbuz arşivindeki tarih aralığının hangi tarihe bakacağı.
 *
 * <p>Bir makbuzun üç ayrı tarihi var ve planlı teslimat geldiğinden beri üçü de farklı
 * günlere düşebiliyor: kâğıdın kesildiği gün, malın gideceği gün, malın gittiği gün.
 * "Ekim ayındaki makbuzlar" sorusunun tek bir doğru cevabı yok — hangisini sorduğunu
 * kullanıcının söylemesi gerekiyor.</p>
 */
public enum ReceiptDateField {

    /** Kâğıdın kesildiği an. Varsayılan: belge arşivinin doğal ekseni. */
    ISSUED("issuedAt"),

    /** Planlanan teslim tarihi. Planı olmayan makbuzlar bu kesitte hiç görünmüyor. */
    SCHEDULED("scheduledDeliveryAt"),

    /** Malın fiilen teslim edildiği an. Teslim edilmemişler bu kesitte görünmüyor. */
    DELIVERED("deliveredAt");

    private final String attribute;

    ReceiptDateField(String attribute) {
        this.attribute = attribute;
    }

    /** {@code DeliveryReceipt} üzerindeki alan adı — hem filtrede hem sıralamada kullanılıyor. */
    public String getAttribute() {
        return attribute;
    }
}
