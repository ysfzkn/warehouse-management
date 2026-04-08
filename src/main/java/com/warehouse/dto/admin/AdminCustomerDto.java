package com.warehouse.dto.admin;

import com.warehouse.enums.CustomerStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminCustomerDto {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String phone;
    private CustomerStatus status;
    private String statusNote;
    private boolean emailVerified;
    private boolean kvkkConsent;
    private boolean marketingConsent;
    private long orderCount;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
