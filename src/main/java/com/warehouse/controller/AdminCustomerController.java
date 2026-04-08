package com.warehouse.controller;

import com.warehouse.dto.PagedResponse;
import com.warehouse.dto.admin.AdminCustomerDetailDto;
import com.warehouse.dto.admin.AdminCustomerDto;
import com.warehouse.dto.admin.CustomerFilterRequest;
import com.warehouse.dto.admin.CustomerStatusUpdateRequest;
import com.warehouse.entity.CustomerAddress;
import com.warehouse.enums.CustomerStatus;
import com.warehouse.repository.CustomerAddressRepository;
import com.warehouse.repository.CustomerRepository;
import com.warehouse.service.AdminCustomerService;
import com.warehouse.service.AdminSecurityService;
import com.warehouse.util.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/customers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCustomerController {

    private final AdminCustomerService adminCustomerService;
    private final CustomerAddressRepository addressRepository;
    private final CustomerRepository customerRepository;
    private final AdminSecurityService adminSecurityService;

    public AdminCustomerController(AdminCustomerService adminCustomerService,
                                    CustomerAddressRepository addressRepository,
                                    CustomerRepository customerRepository,
                                    AdminSecurityService adminSecurityService) {
        this.adminCustomerService = adminCustomerService;
        this.addressRepository = addressRepository;
        this.customerRepository = customerRepository;
        this.adminSecurityService = adminSecurityService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<AdminCustomerDto>> listCustomers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) Boolean emailVerified,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        CustomerFilterRequest filter = new CustomerFilterRequest();
        filter.setSearch(search);
        filter.setStatus(status);
        filter.setEmailVerified(emailVerified);
        filter.setCreatedFrom(createdFrom);
        filter.setCreatedTo(createdTo);

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100), sort);

        Page<AdminCustomerDto> result = adminCustomerService.getCustomers(filter, pageable);

        return ResponseEntity.ok(new PagedResponse<>(
            result.getContent(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.isFirst(),
            result.isLast()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminCustomerDetailDto> getCustomer(@PathVariable Long id) {
        return ResponseEntity.ok(adminCustomerService.getCustomerDetail(id));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, String>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody CustomerStatusUpdateRequest request) {
        String adminUsername = CurrentUser.usernameOrSystem();
        adminCustomerService.updateCustomerStatus(id, request.getStatus(), request.getNote(), adminUsername);
        return ResponseEntity.ok(Map.of("message", "Müşteri durumu güncellendi."));
    }

    @GetMapping("/{id}/addresses")
    public ResponseEntity<List<CustomerAddress>> getCustomerAddresses(@PathVariable Long id) {
        return ResponseEntity.ok(addressRepository.findByCustomerId(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteCustomer(
            @PathVariable Long id,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        return customerRepository.findById(id).map(customer -> {
            // Delete addresses first
            addressRepository.findByCustomerId(id).forEach(addressRepository::delete);
            // Delete customer
            customerRepository.delete(customer);
            return ResponseEntity.ok(Map.of("message", customer.getFirstName() + " " + customer.getLastName() + " müşterisi silindi."));
        }).orElse(ResponseEntity.notFound().build());
    }
}
