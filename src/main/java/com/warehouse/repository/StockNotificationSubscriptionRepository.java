package com.warehouse.repository;

import com.warehouse.entity.StockNotificationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockNotificationSubscriptionRepository extends JpaRepository<StockNotificationSubscription, Long> {

    boolean existsByProductIdAndEmailAndNotifiedFalse(Long productId, String email);

    List<StockNotificationSubscription> findByProductIdAndNotifiedFalse(Long productId);

    /**
     * Henüz bildirilmemiş tüm abonelikleri ait olduğu ürünlerin ID'leri ile birlikte döner.
     * Scheduled job bu listeyi alır, her ürünün güncel stoğunu kontrol eder.
     */
    @Query("SELECT DISTINCT s.product.id FROM StockNotificationSubscription s WHERE s.notified = false")
    List<Long> findProductIdsWithPendingSubscriptions();

    long countByProductIdAndNotifiedFalse(Long productId);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM StockNotificationSubscription s WHERE s.customer.id = :customerId")
    void deleteByCustomerId(@Param("customerId") Long customerId);
}
