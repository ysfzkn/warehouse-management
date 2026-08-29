package com.warehouse.entity;

import com.warehouse.enums.AddressType;
import jakarta.persistence.*;
import com.warehouse.security.EncryptedStringConverter;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_addresses", indexes = {
    @Index(name = "idx_customer_addresses_customer", columnList = "customer_id")
})
@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = false)
public class CustomerAddress {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @NotBlank @Column(nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 20)
    private AddressType addressType = AddressType.SHIPPING;

    @NotBlank @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotBlank @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @NotBlank @Column(nullable = false, length = 20)
    private String phone;

    @NotBlank @Column(nullable = false, length = 100)
    private String city;

    @NotBlank @Column(nullable = false, length = 100)
    private String district;

    @Column(length = 200)
    private String neighborhood;

    @NotBlank @Column(name = "address_line", nullable = false, length = 500)
    private String addressLine;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "tc_kimlik_no", length = 255)
    private String tcKimlikNo;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "tax_office", length = 200)
    private String taxOffice;

    @Column(name = "tax_number", length = 20)
    private String taxNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); this.updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
