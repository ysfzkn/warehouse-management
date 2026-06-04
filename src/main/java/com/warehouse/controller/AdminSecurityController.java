package com.warehouse.controller;

import com.warehouse.dto.ChangeAdminSecurityCodeRequest;
import com.warehouse.service.AdminSecurityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/security-code")
public class AdminSecurityController {

    private final AdminSecurityService adminSecurityService;

    public AdminSecurityController(AdminSecurityService adminSecurityService) {
        this.adminSecurityService = adminSecurityService;
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Boolean> getStatus() {
        return Map.of("configured", adminSecurityService.isSecurityCodeConfigured());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> changeSecurityCode(@RequestBody ChangeAdminSecurityCodeRequest request) {
        adminSecurityService.changeSecurityCode(request.getCurrentCode(), request.getNewCode(), request.getConfirmNewCode());
        return ResponseEntity.ok(Map.of("message", "Güvenlik şifresi güncellendi."));
    }
}

