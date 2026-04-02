package com.warehouse.dto.admin;

import com.warehouse.enums.CustomerStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CustomerStatusUpdateRequest {
    @NotNull(message = "Status zorunludur")
    private CustomerStatus status;
    private String note;
}
