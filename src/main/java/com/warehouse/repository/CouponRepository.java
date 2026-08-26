package com.warehouse.repository;

import com.warehouse.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCode(String code);

    @Query("SELECT c FROM Coupon c WHERE c.code = :code AND c.active = true AND (c.startsAt IS NULL OR c.startsAt <= :now) AND (c.expiresAt IS NULL OR c.expiresAt >= :now)")
    Optional<Coupon> findActiveByCode(String code, LocalDateTime now);

    /**
     * SELECT ... FOR UPDATE on the coupon row. Redemption reads {@code usedCount}, compares it
     * to the limit and writes it back; without the lock two simultaneous checkouts both see the
     * last remaining use.
     */
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Coupon> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
