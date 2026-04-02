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
}
