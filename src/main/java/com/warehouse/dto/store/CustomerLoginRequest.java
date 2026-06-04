package com.warehouse.dto.store;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CustomerLoginRequest {
    @NotBlank @Email
    private String email;

    @NotBlank
    private String password;
}
