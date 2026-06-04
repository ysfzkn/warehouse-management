package com.warehouse.dto;

import com.warehouse.enums.InvoiceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceCreateRequest {

    /** Order ID */
    private Long orderId;

    /** Invoice type (default: E_ARSIV) */
    private InvoiceType invoiceType;

    /** Recipient tax number (for legal entities) */
    private String recipientTaxId;

    /** Recipient tax office (for legal entities) */
    private String recipientTaxOffice;

    /** Note */
    private String note;
}
