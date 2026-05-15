package com.warehouse.dto.store;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CustomerRegisterRequest {
    @NotBlank @Email
    private String email;

    /**
     * Parola politikası: en az 8 karakter, en az 1 büyük harf, 1 küçük harf, 1 rakam.
     * Pattern karmaşıklığı OWASP rehberine uygun "minimum yeterli" seviyede tutuldu;
     * sözlük saldırılarına karşı asıl koruma BCrypt strength 12 + login throttling.
     */
    @NotBlank
    @Size(min = 8, max = 100, message = "Parola en az 8 karakter olmalıdır")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "Parola en az bir büyük harf, bir küçük harf ve bir rakam içermelidir"
    )
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
