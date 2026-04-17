package com.warehouse.service;

/**
 * Stokta yoksa bildir servisi.
 *
 * Kullanım:
 * 1. Müşteri stoku biten ürün için `subscribe()` çağırır
 * 2. Stok güncellendiğinde `checkAndNotifyProduct()` çağrılır (scheduled job veya hook)
 * 3. Ürün tekrar stoktaysa tüm abonelere e-posta gönderilir ve notified=true yapılır
 */
public interface StockNotificationService {

    /**
     * Bir ürün için stok bildirimine abone olur.
     * Aynı e-posta + ürün için zaten aktif abonelik varsa idempotent (hata fırlatmaz).
     *
     * @return true: yeni abonelik oluşturuldu; false: zaten vardı
     */
    boolean subscribe(Long productId, String email, Long customerId);

    /**
     * Belirli bir ürünün stok durumunu kontrol eder;
     * stoktaysa bekleyen tüm aboneliklere bildirim gönderir ve notified=true işaretler.
     *
     * @return bildirim gönderilen abonelik sayısı
     */
    int checkAndNotifyProduct(Long productId);

    /**
     * Bekleyen abonelikleri olan tüm ürünleri kontrol eder.
     * Scheduled job tarafından çağrılır.
     *
     * @return toplam gönderilen bildirim sayısı
     */
    int processPendingSubscriptions();
}
