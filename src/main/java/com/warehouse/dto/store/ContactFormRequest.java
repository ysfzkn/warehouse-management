package com.warehouse.dto.store;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactFormRequest {

    @NotBlank
    @Size(max = 150)
    private String name;

    @NotBlank
    @Email
    @Size(max = 200)
    private String email;

    @Size(max = 40)
    private String phone;

    @NotBlank
    @Size(max = 200)
    private String subject;

    @NotBlank
    @Size(min = 10, max = 5000)
    private String message;

    /**
     * Honeypot field: legitimate users never fill this in (it's hidden by CSS).
     * Bots tend to fill every input they encounter. If this arrives non-empty
     * we silently drop the request.
     *
     * Intentionally has no validation annotations.
     */
    private String website;
}
