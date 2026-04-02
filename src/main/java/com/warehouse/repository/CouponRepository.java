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

    @Query("SELECT c FROM Coupon c WHERE c.code = :code AND c.active = true AND c.startsAt <= :now AND c.expiresAt >= :now")
    Optional<Coupon> findActiveByCode(String code, LocalDateTime now);
}
