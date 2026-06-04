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
     * Finds target carts for abandoned-cart recovery:
     * - Must belong to an account holder (customer != null) so we can send an email
     * - Must contain at least one item
     * - At least :cutoffTime must have elapsed since the last update (waited long enough)
     * - The last update must be newer than :minRecentTime (don't spam very old carts)
     * - A reminder must not have been sent already
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
