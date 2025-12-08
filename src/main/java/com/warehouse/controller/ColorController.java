package com.warehouse.controller;

import com.warehouse.entity.Color;
import com.warehouse.service.ColorService;
import com.warehouse.service.AdminSecurityService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/colors")
@CrossOrigin(origins = "*")
public class ColorController {

    private final ColorService colorService;
    private final AdminSecurityService adminSecurityService;

    @Autowired
    public ColorController(ColorService colorService, AdminSecurityService adminSecurityService) {
        this.colorService = colorService;
        this.adminSecurityService = adminSecurityService;
    }

    @GetMapping
    public ResponseEntity<List<Color>> getAllColors() {
        return ResponseEntity.ok(colorService.getAllColors());
    }

    @GetMapping("/active")
    public ResponseEntity<List<Color>> getAllActiveColors() {
        return ResponseEntity.ok(colorService.getAllActiveColors());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Color>> searchColors(@RequestParam String name) {
        return ResponseEntity.ok(colorService.searchActiveColors(name));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Color> getColorById(@PathVariable Long id) {
        return colorService.getColorById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Color> createColor(@Valid @RequestBody Color color) {
        Color createdColor = colorService.createColor(color);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdColor);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Color> updateColor(@PathVariable Long id, @Valid @RequestBody Color color) {
        Color updatedColor = colorService.updateColor(id, color);
        return ResponseEntity.ok(updatedColor);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteColor(@PathVariable Long id,
                                            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        colorService.deleteColor(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<Void> deleteColorsBulk(@RequestBody List<Long> ids,
                                                 @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        colorService.deleteColorsBulk(ids);
        return ResponseEntity.noContent().build();
    }
}


