package com.warehouse.dto.store;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Misafir (üye olmayan) müşteriler için sipariş oluşturma isteği.
 * Adres bilgileri saved address ID yerine inline olarak gönderilir.
 */
@Data
public class GuestPlaceOrderRequest {

    // --- Misafir iletişim bilgileri ---

    @NotBlank(message = "E-posta zorunludur")
    @Email(message = "Geçerli bir e-posta adresi giriniz")
    private String email;

    @NotBlank(message = "Ad zorunludur")
    private String firstName;

    @NotBlank(message = "Soyad zorunludur")
    private String lastName;

    @NotBlank(message = "Telefon zorunludur")
    private String phone;

    // --- Teslimat adresi (inline) ---

    @NotBlank(message = "Teslimat adresi zorunludur")
    private String shippingAddressLine;

    @NotBlank(message = "Şehir zorunludur")
    private String shippingCity;

    @NotBlank(message = "İlçe zorunludur")
    private String shippingDistrict;

    private String shippingPostalCode;

    // --- Fatura adresi (opsiyonel; boşsa teslimat adresi kullanılır) ---

    private String billingAddressLine;
    private String billingCity;
    private String billingDistrict;
    private String billingPostalCode;

    /** Fatura adresi teslimat adresiyle aynı mı? */
    private boolean billingSameAsShipping = true;

    // --- Kargo ve ödeme ---

    private String cargoCompany;
    private Long cargoProviderId;

    @NotBlank(message = "Ödeme yöntemi zorunludur")
    private String paymentMethod;

    private String customerNote;

    // --- Sözleşmeler (Türkiye 6502 sayılı kanun + KVKK 6698 gereği) ---

    private boolean distanceSalesContractAccepted;
    private java.time.LocalDateTime distanceSalesContractAcceptedAt;
    private boolean preliminaryInfoAccepted;
    private java.time.LocalDateTime preliminaryInfoAcceptedAt;
    private boolean kvkkConsent;
    private java.time.LocalDateTime kvkkConsentAt;

    /** Misafir sepeti ile bağlantı için (localStorage'daki session ID) */
    private String sessionId;
}
