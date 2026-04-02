package com.warehouse.controller.store;

import com.warehouse.entity.Customer;
import com.warehouse.entity.CustomerAddress;
import com.warehouse.repository.CustomerAddressRepository;
import com.warehouse.repository.CustomerRepository;
import com.warehouse.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/store/addresses")
public class StoreAddressController {

    private final CustomerAddressRepository addressRepo;
    private final CustomerRepository customerRepo;
    private final JwtService jwtService;

    public StoreAddressController(CustomerAddressRepository addressRepo, CustomerRepository customerRepo, JwtService jwtService) {
        this.addressRepo = addressRepo;
        this.customerRepo = customerRepo;
        this.jwtService = jwtService;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        Long cid = extractCustomerId(request);
        if (cid == null) return ResponseEntity.badRequest().body(Map.of("message", "Giriş yapmanız gerekiyor."));
        List<Map<String, Object>> dtos = addressRepo.findByCustomerId(cid).stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<?> create(HttpServletRequest request, @RequestBody CustomerAddress addr) {
        Long cid = extractCustomerId(request);
        if (cid == null) return ResponseEntity.badRequest().body(Map.of("message", "Giriş yapmanız gerekiyor."));
        Customer customer = customerRepo.findById(cid).orElse(null);
        if (customer == null) return ResponseEntity.badRequest().body(Map.of("message", "Giriş yapmanız gerekiyor."));
        addr.setId(null);
        addr.setCustomer(customer);
        // If first address, set as default
        if (addressRepo.findByCustomerId(cid).isEmpty()) addr.setDefault(true);
        return ResponseEntity.ok(toDto(addressRepo.save(addr)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(HttpServletRequest request, @PathVariable Long id, @RequestBody CustomerAddress update) {
        Long cid = extractCustomerId(request);
        if (cid == null) return ResponseEntity.badRequest().body(Map.of("message", "Giriş yapmanız gerekiyor."));
        return addressRepo.findById(id)
                .filter(a -> a.getCustomer().getId().equals(cid))
                .map(existing -> {
                    if (update.getTitle() != null) existing.setTitle(update.getTitle());
                    if (update.getFirstName() != null) existing.setFirstName(update.getFirstName());
                    if (update.getLastName() != null) existing.setLastName(update.getLastName());
                    if (update.getPhone() != null) existing.setPhone(update.getPhone());
                    if (update.getCity() != null) existing.setCity(update.getCity());
                    if (update.getDistrict() != null) existing.setDistrict(update.getDistrict());
                    if (update.getNeighborhood() != null) existing.setNeighborhood(update.getNeighborhood());
                    if (update.getAddressLine() != null) existing.setAddressLine(update.getAddressLine());
                    if (update.getPostalCode() != null) existing.setPostalCode(update.getPostalCode());
                    if (update.getTcKimlikNo() != null) existing.setTcKimlikNo(update.getTcKimlikNo());
                    if (update.getCompanyName() != null) existing.setCompanyName(update.getCompanyName());
                    if (update.getTaxOffice() != null) existing.setTaxOffice(update.getTaxOffice());
                    if (update.getTaxNumber() != null) existing.setTaxNumber(update.getTaxNumber());
                    return ResponseEntity.ok(toDto(addressRepo.save(existing)));
                }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(HttpServletRequest request, @PathVariable Long id) {
        Long cid = extractCustomerId(request);
        if (cid == null) return ResponseEntity.badRequest().body(Map.of("message", "Giriş yapmanız gerekiyor."));
        return addressRepo.findById(id)
                .filter(a -> a.getCustomer().getId().equals(cid))
                .map(a -> { addressRepo.delete(a); return ResponseEntity.ok(Map.of("message", "Adres silindi.")); })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/set-default")
    public ResponseEntity<?> setDefault(HttpServletRequest request, @PathVariable Long id) {
        Long cid = extractCustomerId(request);
        if (cid == null) return ResponseEntity.badRequest().body(Map.of("message", "Giriş yapmanız gerekiyor."));
        List<CustomerAddress> all = addressRepo.findByCustomerId(cid);
        for (CustomerAddress a : all) {
            a.setDefault(a.getId().equals(id));
            addressRepo.save(a);
        }
        return ResponseEntity.ok(Map.of("message", "Varsayılan adres güncellendi."));
    }

    private Map<String, Object> toDto(CustomerAddress a) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", a.getId());
        dto.put("title", a.getTitle());
        dto.put("firstName", a.getFirstName());
        dto.put("lastName", a.getLastName());
        dto.put("phone", a.getPhone());
        dto.put("city", a.getCity());
        dto.put("district", a.getDistrict());
        dto.put("neighborhood", a.getNeighborhood());
        dto.put("addressLine", a.getAddressLine());
        dto.put("postalCode", a.getPostalCode());
        dto.put("isDefault", a.isDefault());
        dto.put("tcKimlikNo", a.getTcKimlikNo());
        dto.put("companyName", a.getCompanyName());
        dto.put("taxOffice", a.getTaxOffice());
        dto.put("taxNumber", a.getTaxNumber());
        return dto;
    }

    private Long extractCustomerId(HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
            else if (request.getCookies() != null) {
                for (var c : request.getCookies()) { if ("access_token".equals(c.getName())) { token = c.getValue(); break; } }
            }
            return token != null ? jwtService.extractCustomerId(token) : null;
        } catch (Exception e) { return null; }
    }
}
