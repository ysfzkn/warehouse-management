package com.warehouse.controller.store;

import com.warehouse.dto.store.BannerDto;
import com.warehouse.dto.store.CmsPageDto;
import com.warehouse.service.CmsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/store/pages")
public class StoreCmsController {

    private final CmsService cmsService;

    public StoreCmsController(CmsService cmsService) {
        this.cmsService = cmsService;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<CmsPageDto> getPage(@PathVariable String slug) {
        return ResponseEntity.ok(cmsService.getPageBySlug(slug));
    }

    @GetMapping("/banners")
    public ResponseEntity<List<BannerDto>> getBanners(@RequestParam(required = false) String position) {
        return ResponseEntity.ok(cmsService.getActiveBanners(position));
    }
}
