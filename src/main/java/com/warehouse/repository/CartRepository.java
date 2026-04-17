package com.warehouse.repository;

import com.warehouse.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByCustomerId(Long customerId);
    Optional<Cart> findBySessionId(String sessionId);

    /**
     * Terk edilmiş sepet kurtarma için hedef sepetleri bulur:
     * - Hesap sahibi (customer != null) olmalı (e-posta göndermek için)
     * - En az bir ürünü olmalı
     * - Son güncellenmeden :cutoffTime geçmiş olmalı (yeterince beklendi)
     * - Son güncelleme :minRecentTime'den yeni olmalı (çok eski sepetleri spamleme)
     * - Daha önce hatırlatma gönderilmemiş olmalı
     */
    @Query("""
        SELECT c FROM Cart c
        WHERE c.customer IS NOT NULL
          AND c.abandonedCartReminderSentAt IS NULL
          AND c.updatedAt <= :cutoffTime
          AND c.updatedAt >= :minRecentTime
          AND EXISTS (SELECT 1 FROM CartItem ci WHERE ci.cart = c)
    """)
    List<Cart> findAbandonedCarts(
            @Param("cutoffTime") LocalDateTime cutoffTime,
            @Param("minRecentTime") LocalDateTime minRecentTime);
}
