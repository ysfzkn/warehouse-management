package com.warehouse.service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * KVKK Madde 11 (e) / GDPR Article 17 & 20 uyumu — müşteri veri ihracı ve hesap
 * anonimleştirme servisi. Implementasyon: {@code CustomerAccountServiceImpl}.
 */
public interface CustomerAccountService {

    /**
     * Müşterinin tüm PII'sini, sipariş geçmişini, adreslerini, yorumlarını,
     * sepetini, abone olduğu bildirimleri JSON-uyumlu Map olarak döndürür.
     * Müşteri bu JSON'u indirip arşivleyebilir (data portability).
     */
    Map<String, Object> exportCustomerData(Long customerId);

    /**
     * Müşteri hesabını anonimleştirir:
     * <ul>
     *   <li>PII alanları (ad, soyad, e-posta, telefon, TC, doğum tarihi) hash'lenir veya boşaltılır.</li>
     *   <li>Adresleri silinir.</li>
     *   <li>Sepeti boşaltılır.</li>
     *   <li>Wishlist silinir.</li>
     *   <li>Yorumları "Anonim Kullanıcı" olarak yeniden adlandırılır.</li>
     *   <li>Sipariş ve fatura kayıtları **korunur** (Türkiye'de 10 yıl yasal saklama yükümlülüğü).</li>
     *   <li>customer.anonymizedAt set edilir, customer.active=false.</li>
     * </ul>
     *
     * @throws AccountDeletionBlockedException aktif sipariş varsa (PENDING/PAID/SHIPPING/PROCESSING)
     */
    DeletionResult anonymizeAccount(Long customerId, String reason);

    record DeletionResult(LocalDateTime anonymizedAt, int preservedOrders) {}

    /** Aktif siparişi olan hesap silinemez — önce sipariş tamamlanmalı/iptal edilmeli. */
    class AccountDeletionBlockedException extends RuntimeException {
        public AccountDeletionBlockedException(String message) { super(message); }
    }
}
