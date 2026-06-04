package com.warehouse.service;

import com.warehouse.dto.admin.AdminCustomerDetailDto;
import com.warehouse.dto.admin.CustomerFilterRequest;
import com.warehouse.entity.Customer;
import com.warehouse.entity.CustomerAddress;
import com.warehouse.enums.CustomerStatus;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.CustomerAddressRepository;
import com.warehouse.repository.CustomerRefreshTokenRepository;
import com.warehouse.repository.CustomerRepository;
import com.warehouse.repository.OrderRepository;
import com.warehouse.service.impl.AdminCustomerServiceImpl;
import com.warehouse.testutil.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCustomerServiceImplTest {

    @Mock private CustomerRepository customerRepo;
    @Mock private CustomerAddressRepository addressRepo;
    @Mock private CustomerRefreshTokenRepository refreshTokenRepo;
    @Mock private OrderRepository orderRepo;

    private AdminCustomerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminCustomerServiceImpl(customerRepo, addressRepo, refreshTokenRepo, orderRepo);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_return_paginated_customers() {
        Customer customer = TestDataFactory.createCustomer();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Customer> page = new PageImpl<>(List.of(customer), pageable, 1);

        when(customerRepo.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(orderRepo.countByCustomerId(customer.getId())).thenReturn(2L);

        var result = service.getCustomers(new CustomerFilterRequest(), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo(customer.getEmail());
    }

    @Test
    void should_return_customer_detail() {
        Customer customer = TestDataFactory.createCustomer();
        CustomerAddress address = TestDataFactory.createAddress(customer);

        when(customerRepo.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(addressRepo.findByCustomerId(customer.getId())).thenReturn(List.of(address));
        when(orderRepo.countByCustomerId(customer.getId())).thenReturn(5L);

        AdminCustomerDetailDto detail = service.getCustomerDetail(customer.getId());

        assertThat(detail.getEmail()).isEqualTo(customer.getEmail());
        assertThat(detail.getOrderCount()).isEqualTo(5L);
        assertThat(detail.getAddresses()).hasSize(1);
    }

    @Test
    void should_throw_when_customer_not_found() {
        when(customerRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCustomerDetail(999L))
            .isInstanceOf(WarehouseManagementException.class);
    }

    @Test
    void should_update_customer_status() {
        Customer customer = TestDataFactory.createCustomer();
        when(customerRepo.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(customerRepo.save(any())).thenReturn(customer);

        service.updateCustomerStatus(customer.getId(), CustomerStatus.INACTIVE, "Test note", "admin");

        verify(customerRepo).save(argThat(c ->
            c.getStatus() == CustomerStatus.INACTIVE &&
            c.getStatusNote().equals("Test note") &&
            c.getStatusChangedBy().equals("admin")
        ));
    }

    @Test
    void should_revoke_tokens_when_blacklisted() {
        Customer customer = TestDataFactory.createCustomer();
        when(customerRepo.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(customerRepo.save(any())).thenReturn(customer);

        service.updateCustomerStatus(customer.getId(), CustomerStatus.BLACKLISTED, "Fraud", "admin");

        verify(refreshTokenRepo).revokeAllByCustomerId(customer.getId());
    }

    @Test
    void should_not_revoke_tokens_for_non_blacklist_status() {
        Customer customer = TestDataFactory.createCustomer();
        when(customerRepo.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(customerRepo.save(any())).thenReturn(customer);

        service.updateCustomerStatus(customer.getId(), CustomerStatus.INACTIVE, "Temp", "admin");

        verify(refreshTokenRepo, never()).revokeAllByCustomerId(any());
    }

    @Test
    void should_sync_active_flag_on_status_change() {
        Customer customer = TestDataFactory.createCustomer();
        when(customerRepo.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(customerRepo.save(any())).thenReturn(customer);

        service.updateCustomerStatus(customer.getId(), CustomerStatus.INACTIVE, "note", "admin");
        verify(customerRepo).save(argThat(c -> !c.isActive()));
    }
}
