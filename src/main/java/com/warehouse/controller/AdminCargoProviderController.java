package com.warehouse.controller;

import com.warehouse.entity.CargoProvider;
import com.warehouse.repository.CargoProviderRepository;
import com.warehouse.service.AdminSecurityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/cargo-providers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCargoProviderController {

    private final CargoProviderRepository cargoRepo;
    private final AdminSecurityService adminSecurityService;

    public AdminCargoProviderController(CargoProviderRepository cargoRepo, AdminSecurityService adminSecurityService) {
        this.cargoRepo = cargoRepo;
        this.adminSecurityService = adminSecurityService;
    }

    @GetMapping
    public ResponseEntity<List<CargoProvider>> list() {
        return ResponseEntity.ok(cargoRepo.findAll(org.springframework.data.domain.Sort.by("sortOrder")));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CargoProvider> detail(@PathVariable Long id) {
        return cargoRepo.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CargoProvider provider) {
        if (cargoRepo.existsByCode(provider.getCode())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bu kargo kodu zaten kullanılıyor: " + provider.getCode()));
        }
        provider.setId(null);
        return ResponseEntity.ok(cargoRepo.save(provider));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody CargoProvider update) {
        return cargoRepo.findById(id).map(existing -> {
            if (update.getName() != null) existing.setName(update.getName());
            if (update.getLogoUrl() != null) existing.setLogoUrl(update.getLogoUrl());
            if (update.getBaseCost() != null) existing.setBaseCost(update.getBaseCost());
            if (update.getCostPerDesi() != null) existing.setCostPerDesi(update.getCostPerDesi());
            if (update.getFreeShippingThreshold() != null) existing.setFreeShippingThreshold(update.getFreeShippingThreshold());
            if (update.getEstimatedDeliveryDays() != null) existing.setEstimatedDeliveryDays(update.getEstimatedDeliveryDays());
            if (update.getVatRate() != null) existing.setVatRate(update.getVatRate());
            if (update.getTrackingUrlTemplate() != null) existing.setTrackingUrlTemplate(update.getTrackingUrlTemplate());
            existing.setSortOrder(update.getSortOrder());
            return ResponseEntity.ok(cargoRepo.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/toggle")
    public ResponseEntity<Map<String, String>> toggle(@PathVariable Long id) {
        return cargoRepo.findById(id).map(p -> {
            p.setActive(!p.isActive());
            cargoRepo.save(p);
            return ResponseEntity.ok(Map.of("message", p.getName() + (p.isActive() ? " aktifleştirildi." : " deaktif edildi.")));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        return cargoRepo.findById(id).map(p -> {
            cargoRepo.delete(p);
            return ResponseEntity.ok(Map.of("message", p.getName() + " silindi."));
        }).orElse(ResponseEntity.notFound().build());
    }
}
