package com.warehouse.service.notification;

/**
 * SMS sağlayıcı arayüzü.
 * Farklı Türk SMS sağlayıcıları (Netgsm, İleti Merkezi, Vatansms vb.) bu arayüzü uygular.
 */
public interface SmsProvider {

    /**
     * Sağlayıcı adını döner (örn: "NETGSM", "ILETIMERKEZI", "MOCK").
     */
    String getProviderName();

    /**
     * SMS gönderir.
     *
     * @param phoneNumber alıcı telefon numarası (E.164 formatı: +905551234567 veya 05551234567)
     * @param message     mesaj içeriği (Türkçe karakter destekli)
     * @return gönderim sonucu
     */
    SmsSendResult send(String phoneNumber, String message);

    /**
     * Sağlayıcının aktif olup olmadığını döner.
     * Credential yapılandırması eksikse false.
     */
    boolean isEnabled();
}
