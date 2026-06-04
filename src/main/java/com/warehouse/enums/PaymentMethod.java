package com.warehouse.enums;

/**
 * Payment method selected by customer at checkout.
 * Used instead of hardcoded strings throughout the payment flow.
 */
public enum PaymentMethod {
    CREDIT_CARD,     // Credit/debit card (via iyzico or virtual POS)
    VIRTUAL_POS,     // Direct bank virtual POS
    BANK_TRANSFER,   // Bank transfer/EFT
    DOOR_CASH,       // Cash on delivery
    DOOR_CARD        // Card payment on delivery
}
