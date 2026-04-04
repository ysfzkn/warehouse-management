package com.warehouse.controller.store;

import com.warehouse.entity.Brand;
import com.warehouse.entity.Color;
import com.warehouse.service.BrandService;
import com.warehouse.service.ColorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/store")
public class StoreBrandColorController {

    private final BrandService brandService;
    private final ColorService colorService;

    public StoreBrandColorController(BrandService brandService, ColorService colorService) {
        this.brandService = brandService;
        this.colorService = colorService;
    }

    @GetMapping("/brands")
    public ResponseEntity<List<BrandDto>> getActiveBrands() {
        List<BrandDto> brands = brandService.getAllActiveBrands().stream()
            .map(b -> new BrandDto(b.getId(), b.getName(), b.getSlug(), b.getLogoUrl()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(brands);
    }

    @GetMapping("/colors")
    public ResponseEntity<List<ColorDto>> getActiveColors() {
        List<ColorDto> colors = colorService.getAllActiveColors().stream()
            .map(c -> new ColorDto(c.getId(), c.getName(), c.getHexCode()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(colors);
    }

    public record BrandDto(Long id, String name, String slug, String logoUrl) {}
    public record ColorDto(Long id, String name, String hexCode) {}
}
