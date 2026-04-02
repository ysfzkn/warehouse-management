package com.warehouse.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import com.warehouse.enums.CustomerStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers", indexes = {
    @Index(name = "idx_customers_email", columnList = "email"),
    @Index(name = "idx_customers_phone", columnList = "phone")
})
@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = false)
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Email @Column(nullable = false, unique = true)
    private String email;

    @NotBlank @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @NotBlank @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotBlank @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 20)
    private String phone;

    @Column(name = "tc_kimlik_no", length = 11)
    private String tcKimlikNo;

    @Column(length = 10)
    private String gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Column(name = "status_note", length = 500)
    private String statusNote;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    @Column(name = "status_changed_by", length = 100)
    private String statusChangedBy;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verify_token", length = 200)
    private String emailVerifyToken;

    @Column(name = "email_verify_sent_at")
    private LocalDateTime emailVerifySentAt;

    @Column(name = "kvkk_consent", nullable = false)
    private boolean kvkkConsent = false;

    @Column(name = "kvkk_consent_at")
    private LocalDateTime kvkkConsentAt;

    @Column(name = "marketing_consent", nullable = false)
    private boolean marketingConsent = false;

    @Column(name = "marketing_consent_at")
    private LocalDateTime marketingConsentAt;

    @Column(name = "password_reset_token", length = 200)
    private String passwordResetToken;

    @Column(name = "password_reset_sent_at")
    private LocalDateTime passwordResetSentAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); this.updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public String getFullName() { return firstName + " " + lastName; }
}
