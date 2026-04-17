package com.warehouse.enums;

/**
 * Fatura yaşam döngüsü durumları.
 */
public enum InvoiceStatus {
    /** Fatura oluşturuldu, henüz sağlayıcıya gönderilmedi */
    DRAFT,
    /** Sağlayıcıya gönderildi, GİB onayı bekleniyor */
    PENDING,
    /** GİB tarafından onaylandı */
    APPROVED,
    /** GİB tarafından reddedildi */
    REJECTED,
    /** İptal edildi */
    CANCELLED,
    /** Oluşturma sırasında hata oluştu */
    ERROR
}
