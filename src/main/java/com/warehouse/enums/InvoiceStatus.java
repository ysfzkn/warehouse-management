package com.warehouse.enums;

/**
 * Invoice lifecycle statuses.
 */
public enum InvoiceStatus {
    /** Invoice created, not yet sent to the provider */
    DRAFT,
    /** Sent to the provider, awaiting GİB approval */
    PENDING,
    /** Approved by GİB */
    APPROVED,
    /** Rejected by GİB */
    REJECTED,
    /** Cancelled */
    CANCELLED,
    /** An error occurred during creation */
    ERROR
}
