package com.warehouse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One earlier movement that appears to belong to the same customer, shown in the
 * "bu müşteriye zaten teslimat yapılmış" warning before a removal or a shipment is saved.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerActivityDto {

    public enum ActivityType {
        /** A CUSTOMER_DELIVERY stock transfer. */
        TRANSFER,
        /** A manual stock reduction whose note or customer field named this customer. */
        STOCK_REMOVAL,
        /** An order that has already shipped or been delivered. */
        ORDER
    }

    public enum Confidence {
        /** Phone numbers matched, or the name matched a dedicated customer field. */
        HIGH,
        /** The name was found inside free-text note. */
        MEDIUM
    }

    private ActivityType type;
    private Confidence confidence;
    private LocalDateTime occurredAt;

    /** Name as it was recorded on the earlier movement. */
    private String customerName;
    private String customerPhone;

    private String productName;
    private Integer quantity;
    private String warehouseName;

    /** Transfer id, or the audit record id for a manual removal. */
    private Long referenceId;
    /** Human label for the reference, e.g. "Transfer #128". */
    private String referenceLabel;
    /** Transfer status, when the activity is a transfer. */
    private String status;
    /** The original note, so the operator can judge the match themselves. */
    private String note;
    /** Which name tokens matched — makes a fuzzy hit auditable. */
    private List<String> matchedTokens;
    /** Where the match was found: "telefon", "müşteri adı" or "not". */
    private String matchedOn;
}
