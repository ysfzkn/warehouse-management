package com.warehouse.enums;

/**
 * Payment method selected by customer at checkout.
 * Used instead of hardcoded strings throughout the payment flow.
 */
public enum PaymentMethod {
    CREDIT_CARD,     // Kredi/banka kartı (iyzico veya sanal POS üzerinden)
    VIRTUAL_POS,     // Doğrudan banka sanal POS
    BANK_TRANSFER,   // Havale/EFT
    DOOR_CASH,       // Kapıda nakit ödeme
    DOOR_CARD        // Kapıda kart ile ödeme
}
