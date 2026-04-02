package com.warehouse.dto.store;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CustomerRegisterRequest {
    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 8, max = 100)
    private String password;

    @NotBlank @Size(min = 2, max = 100)
    private String firstName;

    @NotBlank @Size(min = 2, max = 100)
    private String lastName;

    @Size(max = 20)
    private String phone;

    private boolean kvkkConsent;
    private boolean marketingConsent;
}
