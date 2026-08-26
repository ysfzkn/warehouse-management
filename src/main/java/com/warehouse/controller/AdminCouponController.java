package com.warehouse.controller;

import com.warehouse.dto.PagedResponse;
import com.warehouse.entity.Coupon;
import com.warehouse.repository.CouponRepository;
import com.warehouse.service.AdminSecurityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.warehouse.util.PageLimits;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/coupons")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCouponController {

    private final CouponRepository couponRepo;
    private final AdminSecurityService adminSecurityService;

    public AdminCouponController(CouponRepository couponRepo, AdminSecurityService adminSecurityService) {
        this.couponRepo = couponRepo;
        this.adminSecurityService = adminSecurityService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<Coupon>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Coupon> result = couponRepo.findAll(PageRequest.of(PageLimits.page(page), PageLimits.size(size), Sort.by("createdAt").descending()));
        return ResponseEntity.ok(new PagedResponse<>(
            result.getContent(), result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Coupon> detail(@PathVariable Long id) {
        return couponRepo.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Coupon> create(
            @RequestBody Coupon coupon,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        coupon.setId(null);
        coupon.setUsedCount(0);
        // Default dates: starts now, expires in 1 month
        if (coupon.getStartsAt() == null) coupon.setStartsAt(java.time.LocalDateTime.now());
        if (coupon.getExpiresAt() == null) coupon.setExpiresAt(java.time.LocalDateTime.now().plusMonths(1));
        return ResponseEntity.ok(couponRepo.save(coupon));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Coupon> update(
            @PathVariable Long id,
            @RequestBody Coupon update,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        return couponRepo.findById(id).map(existing -> {
            if (update.getCode() != null) existing.setCode(update.getCode());
            if (update.getDescription() != null) existing.setDescription(update.getDescription());
            if (update.getDiscountType() != null) existing.setDiscountType(update.getDiscountType());
            if (update.getDiscountValue() != null) existing.setDiscountValue(update.getDiscountValue());
            existing.setMinOrderAmount(update.getMinOrderAmount());
            existing.setMaxDiscountAmount(update.getMaxDiscountAmount());
            existing.setUsageLimit(update.getUsageLimit());
            existing.setUsageLimitPerCustomer(update.getUsageLimitPerCustomer());
            existing.setStartsAt(update.getStartsAt());
            existing.setExpiresAt(update.getExpiresAt());
            existing.setActive(update.isActive());
            return ResponseEntity.ok(couponRepo.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String securityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(securityCode);
        return couponRepo.findById(id).map(c -> {
            couponRepo.delete(c);
            return ResponseEntity.ok(Map.of("message", c.getCode() + " kuponu silindi."));
        }).orElse(ResponseEntity.notFound().build());
    }
}
