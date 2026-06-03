package com.warehouse.repository;

import com.warehouse.entity.PaymentGatewayConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentGatewayConfigRepository extends JpaRepository<PaymentGatewayConfig, Long> {

    List<PaymentGatewayConfig> findByActiveTrue();

    List<PaymentGatewayConfig> findByActiveTrueOrderByPriorityAsc();

    Optional<PaymentGatewayConfig> findFirstByActiveTrueAndDefaultGatewayTrueOrderByPriorityAsc();

    Optional<PaymentGatewayConfig> findByCode(String code);

    List<PaymentGatewayConfig> findByGatewayProtocol(String gatewayProtocol);

    /**
     * Atomic: clears all default flags. Prevents the race condition in the
     * set-default flow (two admins making two different gateways default at the
     * same time leaving multiple default records).
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query(
            "UPDATE PaymentGatewayConfig p SET p.defaultGateway = false WHERE p.defaultGateway = true")
    int clearAllDefaults();
}
