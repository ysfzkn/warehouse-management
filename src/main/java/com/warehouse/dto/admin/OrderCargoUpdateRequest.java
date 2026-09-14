package com.warehouse.dto.admin;

import lombok.Data;

@Data
public class OrderCargoUpdateRequest {
    private String cargoCompany;
    private String cargoTrackingNo;

    /**
     * Parcel count for this order. Null leaves the packing plan in charge; a number overrides it,
     * for when the person who taped the boxes shut knows better than the calculation.
     */
    private Integer cargoPackageCount;
}
