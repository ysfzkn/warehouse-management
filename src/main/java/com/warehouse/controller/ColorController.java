package com.warehouse.controller;

import com.warehouse.entity.Color;
import com.warehouse.service.ColorService;
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

    @Autowired
    public ColorController(ColorService colorService) {
        this.colorService = colorService;
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
    public ResponseEntity<?> createColor(@Valid @RequestBody Color color) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(colorService.createColor(color));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateColor(@PathVariable Long id, @Valid @RequestBody Color color) {
        try {
            return ResponseEntity.ok(colorService.updateColor(id, color));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteColor(@PathVariable Long id) {
        try {
            colorService.deleteColor(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}


