package com.warehouse.service.impl;

import com.warehouse.dto.admin.AdminCustomerDetailDto;
import com.warehouse.dto.admin.AdminCustomerDto;
import com.warehouse.dto.admin.CustomerFilterRequest;
import com.warehouse.entity.Customer;
import com.warehouse.entity.CustomerAddress;
import com.warehouse.enums.CustomerStatus;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.CustomerAddressRepository;
import com.warehouse.repository.CustomerRefreshTokenRepository;
import com.warehouse.repository.CustomerRepository;
import com.warehouse.repository.OrderRepository;
import com.warehouse.repository.specification.CustomerSpecifications;
import com.warehouse.service.AdminCustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminCustomerServiceImpl implements AdminCustomerService {

    private static final Logger logger = LoggerFactory.getLogger(AdminCustomerServiceImpl.class);

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final CustomerRefreshTokenRepository refreshTokenRepository;
    private final OrderRepository orderRepository;

    public AdminCustomerServiceImpl(CustomerRepository customerRepository,
                                     CustomerAddressRepository addressRepository,
                                     CustomerRefreshTokenRepository refreshTokenRepository,
                                     OrderRepository orderRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminCustomerDto> getCustomers(CustomerFilterRequest filter, Pageable pageable) {
        return customerRepository.findAll(CustomerSpecifications.withFilter(filter), pageable)
            .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminCustomerDetailDto getCustomerDetail(Long id) {
        Customer customer = customerRepository.findById(id)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Müşteri bulunamadı: " + id));

        List<CustomerAddress> addresses = addressRepository.findByCustomerId(id);
        long orderCount = orderRepository.countByCustomerId(id);

        return AdminCustomerDetailDto.builder()
            .id(customer.getId())
            .email(customer.getEmail())
            .firstName(customer.getFirstName())
            .lastName(customer.getLastName())
            .fullName(customer.getFullName())
            .phone(customer.getPhone())
            .tcKimlikNo(customer.getTcKimlikNo())
            .gender(customer.getGender())
            .birthDate(customer.getBirthDate())
            .status(customer.getStatus())
            .statusNote(customer.getStatusNote())
            .statusChangedAt(customer.getStatusChangedAt())
            .statusChangedBy(customer.getStatusChangedBy())
            .emailVerified(customer.isEmailVerified())
            .kvkkConsent(customer.isKvkkConsent())
            .kvkkConsentAt(customer.getKvkkConsentAt())
            .marketingConsent(customer.isMarketingConsent())
            .marketingConsentAt(customer.getMarketingConsentAt())
            .failedLoginCount(customer.getFailedLoginCount())
            .lockedUntil(customer.getLockedUntil())
            .lastLoginAt(customer.getLastLoginAt())
            .lastLoginIp(customer.getLastLoginIp())
            .createdAt(customer.getCreatedAt())
            .updatedAt(customer.getUpdatedAt())
            .orderCount(orderCount)
            .addresses(addresses.stream().map(this::toAddressDto).collect(Collectors.toList()))
            .build();
    }

    @Override
    public void updateCustomerStatus(Long id, CustomerStatus status, String note, String adminUsername) {
        Customer customer = customerRepository.findById(id)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Müşteri bulunamadı: " + id));

        CustomerStatus oldStatus = customer.getStatus();
        customer.setStatus(status);
        customer.setStatusNote(note);
        customer.setStatusChangedAt(LocalDateTime.now());
        customer.setStatusChangedBy(adminUsername);

        // Sync the legacy isActive field
        customer.setActive(status == CustomerStatus.ACTIVE);

        customerRepository.save(customer);

        // If blacklisted, revoke all refresh tokens
        if (status == CustomerStatus.BLACKLISTED) {
            refreshTokenRepository.revokeAllByCustomerId(id);
            logger.info("Customer {} blacklisted by {}. All refresh tokens revoked.", id, adminUsername);
        }

        logger.info("Customer {} status changed: {} -> {} by {}", id, oldStatus, status, adminUsername);
    }

    private AdminCustomerDto toDto(Customer customer) {
        long orderCount = 0;
        try {
            orderCount = orderRepository.countByCustomerId(customer.getId());
        } catch (Exception ignored) {}

        return AdminCustomerDto.builder()
            .id(customer.getId())
            .email(customer.getEmail())
            .firstName(customer.getFirstName())
            .lastName(customer.getLastName())
            .fullName(customer.getFullName())
            .phone(customer.getPhone())
            .status(customer.getStatus())
            .statusNote(customer.getStatusNote())
            .emailVerified(customer.isEmailVerified())
            .kvkkConsent(customer.isKvkkConsent())
            .marketingConsent(customer.isMarketingConsent())
            .orderCount(orderCount)
            .lastLoginAt(customer.getLastLoginAt())
            .lastLoginIp(customer.getLastLoginIp())
            .createdAt(customer.getCreatedAt())
            .updatedAt(customer.getUpdatedAt())
            .build();
    }

    private AdminCustomerDetailDto.AddressDto toAddressDto(CustomerAddress addr) {
        return AdminCustomerDetailDto.AddressDto.builder()
            .id(addr.getId())
            .title(addr.getTitle())
            .addressType(addr.getAddressType() != null ? addr.getAddressType().name() : null)
            .fullName(addr.getFirstName() + " " + addr.getLastName())
            .phone(addr.getPhone())
            .city(addr.getCity())
            .district(addr.getDistrict())
            .addressLine(addr.getAddressLine())
            .isDefault(addr.isDefault())
            .build();
    }
}
