package com.warehouse.dto.admin;

import com.warehouse.enums.CustomerStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AdminCustomerDetailDto {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String phone;
    private String tcKimlikNo;
    private String gender;
    private LocalDate birthDate;
    private CustomerStatus status;
    private String statusNote;
    private LocalDateTime statusChangedAt;
    private String statusChangedBy;
    private boolean emailVerified;
    private boolean kvkkConsent;
    private LocalDateTime kvkkConsentAt;
    private boolean marketingConsent;
    private LocalDateTime marketingConsentAt;
    private int failedLoginCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private long orderCount;
    private List<AddressDto> addresses;

    @Data
    @Builder
    public static class AddressDto {
        private Long id;
        private String title;
        private String addressType;
        private String fullName;
        private String phone;
        private String city;
        private String district;
        private String addressLine;
        private boolean isDefault;
    }
}
