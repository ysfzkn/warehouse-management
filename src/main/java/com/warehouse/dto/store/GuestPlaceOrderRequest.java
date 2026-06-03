package com.warehouse.dto.store;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Order creation request for guest (non-member) customers.
 * Address details are sent inline instead of as a saved address ID.
 */
@Data
public class GuestPlaceOrderRequest {

    // --- Guest contact info ---

    @NotBlank(message = "E-posta zorunludur")
    @Email(message = "Geçerli bir e-posta adresi giriniz")
    private String email;

    @NotBlank(message = "Ad zorunludur")
    private String firstName;

    @NotBlank(message = "Soyad zorunludur")
    private String lastName;

    @NotBlank(message = "Telefon zorunludur")
    private String phone;

    // --- Shipping address (inline) ---

    @NotBlank(message = "Teslimat adresi zorunludur")
    private String shippingAddressLine;

    @NotBlank(message = "Şehir zorunludur")
    private String shippingCity;

    @NotBlank(message = "İlçe zorunludur")
    private String shippingDistrict;

    private String shippingPostalCode;

    // --- Billing address (optional; if empty, the shipping address is used) ---

    private String billingAddressLine;
    private String billingCity;
    private String billingDistrict;
    private String billingPostalCode;

    /** Is the billing address the same as the shipping address? */
    private boolean billingSameAsShipping = true;

    // --- Shipment and payment ---

    private String cargoCompany;
    private Long cargoProviderId;

    @NotBlank(message = "Ödeme yöntemi zorunludur")
    private String paymentMethod;

    private String customerNote;

    // --- Contracts (required by Turkish Law No. 6502 + KVKK 6698) ---

    private boolean distanceSalesContractAccepted;
    private java.time.LocalDateTime distanceSalesContractAcceptedAt;
    private boolean preliminaryInfoAccepted;
    private java.time.LocalDateTime preliminaryInfoAcceptedAt;
    private boolean kvkkConsent;
    private java.time.LocalDateTime kvkkConsentAt;

    /** For linking to the guest cart (the session ID in localStorage) */
    private String sessionId;
}
