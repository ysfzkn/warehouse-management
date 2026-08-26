package com.warehouse.controller;

import com.warehouse.entity.Driver;
import com.warehouse.service.AdminSecurityService;
import com.warehouse.service.DriverService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Driver directory. Warehouse roles may read and pick a driver (they are the ones filling in
 * transfers); editing and deleting stay with admins.
 */
@RestController
@RequestMapping("/api/admin/drivers")
@PreAuthorize("hasAnyRole('ADMIN', 'STOCK_IN', 'STOCK_OUT')")
public class AdminDriverController {

    private final DriverService driverService;
    private final AdminSecurityService adminSecurityService;

    public AdminDriverController(DriverService driverService, AdminSecurityService adminSecurityService) {
        this.driverService = driverService;
        this.adminSecurityService = adminSecurityService;
    }

    /** Full list for the management screen. */
    @GetMapping
    public ResponseEntity<List<Driver>> list(@RequestParam(required = false) String q,
                                             @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(driverService.list(q, activeOnly));
    }

    /** Type-ahead used by the transfer form; blank query returns the most-used drivers. */
    @GetMapping("/suggest")
    public ResponseEntity<List<Driver>> suggest(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(driverService.suggest(q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Driver> get(@PathVariable Long id) {
        return ResponseEntity.ok(driverService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Driver> create(@Valid @RequestBody Driver driver) {
        return ResponseEntity.ok(driverService.create(driver));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Driver> update(@PathVariable Long id, @Valid @RequestBody Driver driver) {
        return ResponseEntity.ok(driverService.update(id, driver));
    }

    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Driver> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(driverService.toggleActive(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        driverService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Şoför silindi."));
    }
}
