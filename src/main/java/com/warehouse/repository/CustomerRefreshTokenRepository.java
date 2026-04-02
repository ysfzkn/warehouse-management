package com.warehouse.repository;

import com.warehouse.entity.CustomerRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CustomerRefreshTokenRepository extends JpaRepository<CustomerRefreshToken, Long> {
    Optional<CustomerRefreshToken> findByTokenAndRevokedAtIsNull(String token);

    @Modifying
    @Query("UPDATE CustomerRefreshToken t SET t.revokedAt = CURRENT_TIMESTAMP WHERE t.customer.id = :customerId AND t.revokedAt IS NULL")
    void revokeAllByCustomerId(Long customerId);
}
