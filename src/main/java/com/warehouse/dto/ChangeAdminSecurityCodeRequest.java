package com.warehouse.dto;

import lombok.Data;

@Data
public class ChangeAdminSecurityCodeRequest {
    private String currentCode;
    private String newCode;
    private String confirmNewCode;
}

