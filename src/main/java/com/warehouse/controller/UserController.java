package com.warehouse.controller;

import com.warehouse.dto.admin.AdminUserDto;
import com.warehouse.entity.User;
import com.warehouse.entity.UserRole;
import com.warehouse.security.TokenRevocationService;
import com.warehouse.service.UserService;
import com.warehouse.service.AdminSecurityService;
import com.warehouse.util.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private final UserService userService;
    private final AdminSecurityService adminSecurityService;
    private final TokenRevocationService tokenRevocationService;

    public UserController(UserService userService,
                          AdminSecurityService adminSecurityService,
                          TokenRevocationService tokenRevocationService) {
        this.userService = userService;
        this.adminSecurityService = adminSecurityService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminUserDto> listUsers() {
        return userService.listUsers().stream().map(AdminUserDto::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserDto createUser(@RequestBody Map<String, String> body,
                                   @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        String role = body.getOrDefault("role", UserRole.STOCK_IN.name());
        UserRole targetRole = UserRole.valueOf(role);
        if (targetRole == UserRole.ADMIN) {
            adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        }
        return AdminUserDto.from(userService.createUser(username, password, targetRole));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> changeRole(@PathVariable Long id,
                                           @RequestBody Map<String, String> body,
                                           @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        String role = body.get("role");
        UserRole targetRole = UserRole.valueOf(role);
        // Any role change is a privilege change — dropping someone out of ADMIN is as
        // sensitive as promoting them, so the second factor is required either way.
        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        User target = userService.listUsers().stream()
                .filter(u -> u.getId().equals(id)).findFirst().orElse(null);
        userService.changeRole(id, targetRole);
        // The old token still carries the old role claim; force a re-login so the
        // change takes effect immediately instead of at token expiry.
        if (target != null) {
            tokenRevocationService.revokeAllForSubject(target.getUsername());
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Resets another account's password.
     *
     * <p>This used to be the one privileged user operation that did <em>not</em> ask
     * for the admin security code, even though creating and deleting admins did.
     * That made it the cheapest possible takeover path: anyone holding a stolen admin
     * token could set the real administrator's password and lock them out without ever
     * knowing the second factor.</p>
     */
    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id,
                                              @RequestBody Map<String, String> body,
                                              @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        String password = body.get("password");
        User target = userService.listUsers().stream()
                .filter(u -> u.getId().equals(id)).findFirst().orElse(null);
        userService.resetPassword(id, password);
        if (target != null) {
            tokenRevocationService.revokeAllForSubject(target.getUsername());
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id,
                                           @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        User target = userService.listUsers().stream()
                .filter(u -> u.getId().equals(id)).findFirst().orElse(null);
        if (target != null && target.getUsername().equalsIgnoreCase(CurrentUser.usernameOrSystem())) {
            return ResponseEntity.badRequest().build();
        }
        userService.deleteUser(id);
        if (target != null) {
            tokenRevocationService.revokeAllForSubject(target.getUsername());
        }
        return ResponseEntity.noContent().build();
    }
}
