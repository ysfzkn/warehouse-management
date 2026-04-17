package com.warehouse.service.notification;

/**
 * Gönderilebilir bildirim tipleri.
 * Her tip için kullanıcı tercih (enabled/disabled) tutabilir.
 */
public enum NotificationType {
    /** Sipariş oluşturuldu / onaylandı */
    ORDER_CONFIRMED,
    /** Sipariş durumu değişti (hazırlanıyor, kargolandı, teslim edildi) */
    ORDER_STATUS_CHANGE,
    /** Kargo tracking numarası eklendi */
    CARGO_SHIPPED,
    /** Teslim edildi */
    ORDER_DELIVERED,
    /** Ödeme alındı */
    PAYMENT_RECEIVED,
    /** Pazarlama / kampanya */
    MARKETING
}
