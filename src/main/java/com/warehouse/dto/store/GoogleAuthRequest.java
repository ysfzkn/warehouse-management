package com.warehouse.dto.store;

import lombok.Data;

@Data
public class GoogleAuthRequest {
    private String code;
    private String redirectUri;
}
