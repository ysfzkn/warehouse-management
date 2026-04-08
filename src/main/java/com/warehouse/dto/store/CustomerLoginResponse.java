package com.warehouse.dto.store;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CustomerLoginResponse {
    private Long customerId;
    private String email;
    private String firstName;
    private String token;
    private String refreshToken;
}
