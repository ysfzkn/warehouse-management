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
     * Returns the product IDs of all subscriptions that have not yet been notified.
     * The scheduled job takes this list and checks each product's current stock.
     */
    @Query("SELECT DISTINCT s.product.id FROM StockNotificationSubscription s WHERE s.notified = false")
    List<Long> findProductIdsWithPendingSubscriptions();

    long countByProductIdAndNotifiedFalse(Long productId);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM StockNotificationSubscription s WHERE s.customer.id = :customerId")
    void deleteByCustomerId(@Param("customerId") Long customerId);
}
