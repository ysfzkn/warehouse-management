package com.warehouse.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "payment_gateway_configs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentGatewayConfig {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "gateway_protocol", nullable = false, length = 30)
    private String gatewayProtocol;

    @Column(name = "bank_code", length = 30)
    private String bankCode;

    // --- Credentials ---
    @Column(name = "merchant_id", length = 200)
    private String merchantId;

    @Column(name = "terminal_id", length = 200)
    private String terminalId;

    @Column(name = "store_key", columnDefinition = "TEXT")
    private String storeKey;

    @Column(name = "provision_password", columnDefinition = "TEXT")
    private String provisionPassword;

    @Column(name = "api_key", columnDefinition = "TEXT")
    private String apiKey;

    @Column(name = "secret_key", columnDefinition = "TEXT")
    private String secretKey;

    // --- Endpoints ---
    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "three_d_url", length = 500)
    private String threeDUrl;

    @Column(name = "callback_url", length = 500)
    private String callbackUrl;

    // --- Behavior ---
    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_default", nullable = false)
    private boolean defaultGateway;

    @Column(name = "is_sandbox", nullable = false)
    private boolean sandbox = true;

    @Column(nullable = false)
    private int priority = 100;

    @Column(name = "supported_cards", length = 200)
    private String supportedCards = "VISA,MASTERCARD,TROY";

    @Column(name = "max_installments")
    private Integer maxInstallments = 12;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_config", columnDefinition = "jsonb")
    private Map<String, Object> extraConfig;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); this.updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
