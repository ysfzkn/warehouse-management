package com.warehouse.dto.admin;

import lombok.Data;

@Data
public class OrderCargoUpdateRequest {
    private String cargoCompany;
    private String cargoTrackingNo;
}
