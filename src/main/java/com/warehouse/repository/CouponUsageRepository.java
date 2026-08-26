package com.warehouse.repository;

import com.warehouse.entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    /** How many times this customer has already redeemed the coupon. */
    long countByCouponIdAndCustomerId(Long couponId, Long customerId);

    /** Guards against double-redeeming the same order (retried payment callbacks). */
    boolean existsByOrderId(Long orderId);

    java.util.Optional<CouponUsage> findByOrderId(Long orderId);
}
