package com.warehouse.dto.admin;

import com.warehouse.enums.CustomerStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CustomerFilterRequest {
    private String search;
    private CustomerStatus status;
    private Boolean emailVerified;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
}
