package com.warehouse.service.invoice;

import com.warehouse.enums.InvoiceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result returned by an E-Fatura provider.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResult {

    private boolean success;
    private String invoiceNumber;
    private String providerInvoiceId;
    private InvoiceStatus status;
    private String gibResponse;
    private String errorMessage;
    private String pdfUrl;
}
