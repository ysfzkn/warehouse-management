package com.warehouse.service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * KVKK Article 11 (e) / GDPR Article 17 & 20 compliance — customer data export and
 * account anonymization service. Implementation: {@code CustomerAccountServiceImpl}.
 */
public interface CustomerAccountService {

    /**
     * Returns all of the customer's PII, order history, addresses, reviews,
     * cart, and notification subscriptions as a JSON-compatible Map.
     * The customer can download and archive this JSON (data portability).
     */
    Map<String, Object> exportCustomerData(Long customerId);

    /**
     * Anonymizes the customer account:
     * <ul>
     *   <li>PII fields (first name, last name, email, phone, TCKN, date of birth) are hashed or cleared.</li>
     *   <li>Addresses are deleted.</li>
     *   <li>The cart is emptied.</li>
     *   <li>The wishlist is deleted.</li>
     *   <li>Reviews are renamed to "Anonymous User".</li>
     *   <li>Order and invoice records are **preserved** (10-year legal retention obligation in Turkey).</li>
     *   <li>customer.anonymizedAt is set, customer.active=false.</li>
     * </ul>
     *
     * @throws AccountDeletionBlockedException if there is an active order (PENDING/PAID/SHIPPING/PROCESSING)
     */
    DeletionResult anonymizeAccount(Long customerId, String reason);

    record DeletionResult(LocalDateTime anonymizedAt, int preservedOrders) {}

    /** An account with an active order cannot be deleted — the order must first be completed/cancelled. */
    class AccountDeletionBlockedException extends RuntimeException {
        public AccountDeletionBlockedException(String message) { super(message); }
    }
}
