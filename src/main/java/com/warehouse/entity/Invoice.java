package com.warehouse.entity;

import com.warehouse.enums.InvoiceStatus;
import com.warehouse.enums.InvoiceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoices", indexes = {
        @Index(name = "idx_invoices_order_id", columnList = "order_id"),
        @Index(name = "idx_invoices_invoice_number", columnList = "invoice_number"),
        @Index(name = "idx_invoices_status", columnList = "status"),
        @Index(name = "idx_invoices_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false, length = 20)
    @Builder.Default
    private InvoiceType invoiceType = InvoiceType.E_ARSIV;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    // --- Alıcı bilgileri (sipariş anından snapshot) ---

    @Column(name = "recipient_name", length = 255)
    private String recipientName;

    @Column(name = "recipient_tax_id", length = 20)
    private String recipientTaxId;

    @Column(name = "recipient_tax_office", length = 100)
    private String recipientTaxOffice;

    @Column(name = "recipient_address", columnDefinition = "TEXT")
    private String recipientAddress;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "recipient_city", length = 100)
    private String recipientCity;

    @Column(name = "recipient_district", length = 100)
    private String recipientDistrict;

    @Column(name = "recipient_postal_code", length = 10)
    private String recipientPostalCode;

    /** ETTN: e-Fatura evrensel takip numarası (GUID) - Logo/GİB'de faturayı bulmak için */
    @Column(name = "ettn", length = 50)
    private String ettn;

    /**
     * Bireysel müşteri mi? true: TC kimlik no ile e-Arşiv, false: VKN ile e-Fatura
     * Bu flag bu faturanın bireysel mi kurumsal mı olduğunu belirler.
     */
    @Column(name = "is_individual", nullable = false)
    @Builder.Default
    private boolean individual = true;

    // --- Tutar bilgileri ---

    @Column(name = "subtotal", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "vat_amount", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    // --- Sağlayıcı bilgileri ---

    @Column(name = "provider_name", length = 50)
    private String providerName;

    @Column(name = "provider_invoice_id", length = 100)
    private String providerInvoiceId;

    @Column(name = "gib_response", columnDefinition = "TEXT")
    private String gibResponse;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // --- Dosya bilgileri ---

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @Column(name = "xml_content", columnDefinition = "TEXT")
    private String xmlContent;

    // --- Notlar ---

    @Column(name = "note", length = 500)
    private String note;

    // --- Zaman damgaları ---

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
