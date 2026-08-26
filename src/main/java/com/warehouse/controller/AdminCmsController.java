package com.warehouse.controller;

import com.warehouse.dto.PagedResponse;
import com.warehouse.entity.CmsPage;
import com.warehouse.service.CmsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import com.warehouse.util.PageLimits;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/cms")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCmsController {

    private final CmsService cmsService;

    public AdminCmsController(CmsService cmsService) {
        this.cmsService = cmsService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<CmsPage>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CmsPage> result = cmsService.getAllPages(PageRequest.of(PageLimits.page(page), PageLimits.size(size), Sort.by("sortOrder").ascending()));
        return ResponseEntity.ok(new PagedResponse<>(result.getContent(), result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast()));
    }

    @PostMapping
    public ResponseEntity<CmsPage> create(@RequestBody CmsPage page) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cmsService.createPage(page));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CmsPage> update(@PathVariable Long id, @RequestBody CmsPage page) {
        return ResponseEntity.ok(cmsService.updatePage(id, page));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        cmsService.deletePage(id);
        return ResponseEntity.ok(Map.of("message", "Sayfa silindi."));
    }
}
