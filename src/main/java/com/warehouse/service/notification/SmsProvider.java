package com.warehouse.service.notification;

/**
 * SMS provider interface.
 * Different Turkish SMS providers (Netgsm, İleti Merkezi, Vatansms, etc.) implement this interface.
 */
public interface SmsProvider {

    /**
     * Returns the provider name (e.g. "NETGSM", "ILETIMERKEZI", "MOCK").
     */
    String getProviderName();

    /**
     * Sends an SMS.
     *
     * @param phoneNumber recipient phone number (E.164 format: +905551234567 or 05551234567)
     * @param message     message content (supports Turkish characters)
     * @return send result
     */
    SmsSendResult send(String phoneNumber, String message);

    /**
     * Returns whether the provider is enabled.
     * False if the credential configuration is missing.
     */
    boolean isEnabled();
}
