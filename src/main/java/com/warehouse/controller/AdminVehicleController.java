package com.warehouse.controller;

import com.warehouse.entity.Vehicle;
import com.warehouse.service.AdminSecurityService;
import com.warehouse.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Vehicle directory. Warehouse roles read and pick (they fill in transfers); editing and
 * deleting stay with admins, matching how drivers are handled.
 */
@RestController
@RequestMapping("/api/admin/vehicles")
@PreAuthorize("hasAnyRole('ADMIN', 'STOCK_IN', 'STOCK_OUT')")
public class AdminVehicleController {

    private final VehicleService vehicleService;
    private final AdminSecurityService adminSecurityService;

    public AdminVehicleController(VehicleService vehicleService, AdminSecurityService adminSecurityService) {
        this.vehicleService = vehicleService;
        this.adminSecurityService = adminSecurityService;
    }

    @GetMapping
    public ResponseEntity<List<Vehicle>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(vehicleService.list(q, activeOnly));
    }

    /** Type-ahead for the transfer form's plate field. */
    @GetMapping("/suggest")
    public ResponseEntity<List<Vehicle>> suggest(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(vehicleService.suggest(q));
    }

    /** Vehicles already assigned to a driver — offered first when that driver is picked. */
    @GetMapping("/by-driver/{driverId}")
    public ResponseEntity<List<Vehicle>> byDriver(@PathVariable Long driverId) {
        return ResponseEntity.ok(vehicleService.forDriver(driverId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_IN', 'STOCK_OUT')")
    public ResponseEntity<Vehicle> create(@Valid @RequestBody Vehicle vehicle) {
        // Mass-assignment guard: the request body is bound straight onto the JPA
        // entity, so a caller-supplied id would turn this insert into an update of
        // whatever row that id points at. The path is the only source of identity.
        vehicle.setId(null);
        // Creation stays open to warehouse roles: a new vehicle usually shows up mid-transfer,
        // and forcing an admin round-trip would just push people back to free text.
        return ResponseEntity.ok(vehicleService.create(vehicle));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Vehicle> update(@PathVariable Long id, @Valid @RequestBody Vehicle vehicle) {
        return ResponseEntity.ok(vehicleService.update(id, vehicle));
    }

    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Vehicle> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleService.toggleActive(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        vehicleService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Araç silindi."));
    }

    // ─── Driver assignments ─────────────────────────────────────────────────

    @PostMapping("/{vehicleId}/drivers/{driverId}")
    public ResponseEntity<Map<String, String>> assign(@PathVariable Long vehicleId, @PathVariable Long driverId) {
        vehicleService.assignToDriver(driverId, vehicleId);
        return ResponseEntity.ok(Map.of("message", "Araç şoföre atandı."));
    }

    @DeleteMapping("/{vehicleId}/drivers/{driverId}")
    public ResponseEntity<Map<String, String>> unassign(@PathVariable Long vehicleId, @PathVariable Long driverId) {
        vehicleService.unassignFromDriver(driverId, vehicleId);
        return ResponseEntity.ok(Map.of("message", "Atama kaldırıldı."));
    }
}
