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

    /** Sipariş ID */
    private Long orderId;

    /** Fatura tipi (varsayılan: E_ARSIV) */
    private InvoiceType invoiceType;

    /** Alıcı vergi numarası (tüzel kişiler için) */
    private String recipientTaxId;

    /** Alıcı vergi dairesi (tüzel kişiler için) */
    private String recipientTaxOffice;

    /** Not */
    private String note;
}
