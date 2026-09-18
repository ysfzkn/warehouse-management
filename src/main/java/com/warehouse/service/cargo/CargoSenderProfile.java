package com.warehouse.service.cargo;

import com.warehouse.constants.SettingKeys;
import com.warehouse.service.SiteSettingService;
import org.springframework.stereotype.Service;

/**
 * Who the parcel is sent by, read from settings in one place.
 *
 * <p>Kargonomi requires six sender fields on every shipment that does not name a warehouse, and
 * refuses the whole request when any is missing. Three call sites were building this block: the
 * order flow filled it from settings, while the price quote and the trial shipment left it empty
 * — so both of those were rejected with all six fields reported missing, and the price quote had
 * been that way unnoticed because live checkout pricing ships switched off.
 *
 * <p>One place to read them from means the next caller cannot forget.
 */
@Service
public class CargoSenderProfile {

    private final SiteSettingService settingService;

    public CargoSenderProfile(SiteSettingService settingService) {
        this.settingService = settingService;
    }

    /** Applies the configured sender to a request under construction. */
    public CargoShipmentRequest.CargoShipmentRequestBuilder applyTo(
            CargoShipmentRequest.CargoShipmentRequestBuilder builder) {
        return builder
                .senderName(name())
                .senderPhone(settingService.getSetting(SettingKeys.SENDER_PHONE))
                .senderAddress(settingService.getSetting(SettingKeys.SENDER_ADDRESS))
                .senderCity(settingService.getSetting(SettingKeys.SENDER_CITY))
                .senderDistrict(settingService.getSetting(SettingKeys.SENDER_DISTRICT))
                .senderPostalCode(settingService.getSetting(SettingKeys.SENDER_POSTAL_CODE))
                .senderTaxNumber(taxNumber());
    }

    /** Falls back to the store name, which is what a customer would recognise on a label. */
    public String name() {
        String senderName = settingService.getSetting(SettingKeys.SENDER_NAME);
        return senderName == null || senderName.isBlank()
                ? settingService.getSetting(SettingKeys.SITE_NAME)
                : senderName;
    }

    /**
     * Kargonomi wants a tax or national id for the sender. Rather than asking for the same number
     * twice, this reuses the one already entered for invoices; a dedicated cargo setting overrides
     * it when the shipping entity differs from the invoicing one.
     */
    public String taxNumber() {
        String cargoTaxNumber = settingService.getSetting(SettingKeys.SENDER_TAX_NUMBER);
        if (cargoTaxNumber != null && !cargoTaxNumber.isBlank()) return cargoTaxNumber;
        return settingService.getSetting(SettingKeys.INVOICE_COMPANY_TAX_ID);
    }

    /** The fields Kargonomi rejects the request for, when any of them is missing. */
    public boolean isComplete() {
        return notBlank(name())
                && notBlank(settingService.getSetting(SettingKeys.SENDER_PHONE))
                && notBlank(settingService.getSetting(SettingKeys.SENDER_ADDRESS))
                && notBlank(settingService.getSetting(SettingKeys.SENDER_CITY))
                && notBlank(settingService.getSetting(SettingKeys.SENDER_DISTRICT))
                && notBlank(taxNumber());
    }

    /** Names what is missing, so the admin screen can say which field to fill. */
    public String missingFields() {
        StringBuilder missing = new StringBuilder();
        appendIfBlank(missing, "gönderici adı", name());
        appendIfBlank(missing, "telefon", settingService.getSetting(SettingKeys.SENDER_PHONE));
        appendIfBlank(missing, "adres", settingService.getSetting(SettingKeys.SENDER_ADDRESS));
        appendIfBlank(missing, "il", settingService.getSetting(SettingKeys.SENDER_CITY));
        appendIfBlank(missing, "ilçe", settingService.getSetting(SettingKeys.SENDER_DISTRICT));
        appendIfBlank(missing, "vergi/kimlik no", taxNumber());
        return missing.toString();
    }

    private static void appendIfBlank(StringBuilder out, String label, String value) {
        if (notBlank(value)) return;
        if (out.length() > 0) out.append(", ");
        out.append(label);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
