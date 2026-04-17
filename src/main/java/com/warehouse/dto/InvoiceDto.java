package com.warehouse.dto;

import com.warehouse.enums.InvoiceStatus;
import com.warehouse.enums.InvoiceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDto {

    private Long id;
    private Long orderId;
    private String orderNumber;
    private String invoiceNumber;
    private InvoiceType invoiceType;
    private InvoiceStatus status;

    // Alıcı bilgileri
    private String recipientName;
    private String recipientTaxId;
    private String recipientTaxOffice;
    private String recipientAddress;

    // Tutar bilgileri
    private BigDecimal subtotal;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount;

    // Sağlayıcı bilgileri
    private String providerName;
    private String providerInvoiceId;
    private String errorMessage;

    // Dosya
    private String pdfUrl;
    private boolean hasPdf;

    // Notlar
    private String note;

    // Tarihler
    private LocalDateTime issuedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
