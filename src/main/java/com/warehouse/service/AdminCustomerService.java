package com.warehouse.service;

import com.warehouse.dto.admin.AdminCustomerDetailDto;
import com.warehouse.dto.admin.AdminCustomerDto;
import com.warehouse.dto.admin.CustomerFilterRequest;
import com.warehouse.enums.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminCustomerService {
    Page<AdminCustomerDto> getCustomers(CustomerFilterRequest filter, Pageable pageable);
    AdminCustomerDetailDto getCustomerDetail(Long id);
    void updateCustomerStatus(Long id, CustomerStatus status, String note, String adminUsername);
}
