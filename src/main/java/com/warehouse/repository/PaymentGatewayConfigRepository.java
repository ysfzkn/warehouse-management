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
     * Atomik: tüm default flag'lerini kapatır. Set-default flow'da race condition
     * (iki admin aynı anda iki farklı gateway'i default yaparsa multiple default
     * kayıt kalması) önlenir.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query(
            "UPDATE PaymentGatewayConfig p SET p.defaultGateway = false WHERE p.defaultGateway = true")
    int clearAllDefaults();
}
