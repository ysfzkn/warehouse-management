package com.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Fills in the carrier of a shipment that left the warehouse before the driver was known.
 *
 * <p>All four fields are mandatory here, exactly as on the ordinary transfer form: this is
 * the moment the missing information arrives, so there is no reason to accept half of it.</p>
 */
@Data
public class CarrierAssignmentRequest {

    @NotBlank(message = "Şoför adı zorunludur")
    @Size(min = 3, max = 100, message = "Şoför adı 3-100 karakter olmalıdır")
    private String driverName;

    @NotBlank(message = "Şoför TC kimlik no zorunludur")
    @Pattern(regexp = "^[0-9]{11}$", message = "TC kimlik no 11 haneli olmalıdır")
    private String driverTcId;

    @NotBlank(message = "Şoför telefonu zorunludur")
    @Size(min = 10, max = 20, message = "Şoför telefonu 10-20 karakter olmalıdır")
    private String driverPhone;

    @NotBlank(message = "Araç plakası zorunludur")
    @Size(min = 2, max = 20, message = "Araç plakası 2-20 karakter olmalıdır")
    private String vehiclePlate;
}
