package com.warehouse.controller;

import com.warehouse.entity.User;
import com.warehouse.entity.UserRole;
import com.warehouse.service.UserService;
import com.warehouse.service.AdminSecurityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final AdminSecurityService adminSecurityService;

    public UserController(UserService userService, AdminSecurityService adminSecurityService) {
        this.userService = userService;
        this.adminSecurityService = adminSecurityService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> listUsers() {
        return userService.listUsers();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public User createUser(@RequestBody Map<String, String> body,
                           @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        String role = body.getOrDefault("role", UserRole.STOCK_IN.name());
        UserRole targetRole = UserRole.valueOf(role);
        if (targetRole == UserRole.ADMIN) {
            adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        }
        return userService.createUser(username, password, targetRole);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> changeRole(@PathVariable Long id,
                                           @RequestBody Map<String, String> body,
                                           @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        String role = body.get("role");
        UserRole targetRole = UserRole.valueOf(role);
        if (targetRole == UserRole.ADMIN) {
            adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        }
        userService.changeRole(id, targetRole);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String password = body.get("password");
        userService.resetPassword(id, password);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id,
                                           @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}


