package com.warehouse.controller;

import com.warehouse.dto.PagedResponse;
import com.warehouse.dto.store.ReturnDtoMapper;
import com.warehouse.entity.ReturnRequest;
import com.warehouse.enums.ReturnStatus;
import com.warehouse.service.ReturnRequestService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin management of customer return requests (iade): list/filter, inspect, and
 * drive the lifecycle (approve → receive → refund, or reject). All business rules,
 * stock restoration, refund and notifications live in {@link ReturnRequestService}.
 */
@RestController
@RequestMapping("/api/admin/returns")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReturnController {

    private final ReturnRequestService returnService;

    public AdminReturnController(ReturnRequestService returnService) {
        this.returnService = returnService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<PagedResponse<Map<String, Object>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        ReturnStatus statusFilter = parseStatus(status);
        Page<ReturnRequest> result = returnService.listForAdmin(
                statusFilter, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<Map<String, Object>> content = result.getContent().stream()
                .map(ReturnDtoMapper::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(new PagedResponse<>(
                content, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast()));
    }

    /** Per-status counts for dashboard badges (e.g. how many PENDING). */
    @GetMapping("/counts")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Long>> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ReturnStatus s : ReturnStatus.values()) {
            counts.put(s.name(), returnService.countByStatus(s));
        }
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ReturnDtoMapper.toMap(returnService.getByIdForAdmin(id)));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id,
                                                       @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(ReturnDtoMapper.toMap(returnService.approve(id, note(body))));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long id,
                                                      @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(ReturnDtoMapper.toMap(returnService.reject(id, note(body))));
    }

    @PutMapping("/{id}/receive")
    public ResponseEntity<Map<String, Object>> receive(@PathVariable Long id,
                                                       @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(ReturnDtoMapper.toMap(returnService.markReceived(id, note(body))));
    }

    @PutMapping("/{id}/refund")
    public ResponseEntity<Map<String, Object>> refund(@PathVariable Long id,
                                                      @RequestBody(required = false) Map<String, Object> body,
                                                      jakarta.servlet.http.HttpServletRequest request) {
        BigDecimal amount = null;
        String adminNote = null;
        if (body != null) {
            Object a = body.get("amount");
            if (a != null && !a.toString().isBlank()) {
                try { amount = new BigDecimal(a.toString()); } catch (NumberFormatException ignored) {}
            }
            Object n = body.get("adminNote");
            if (n != null) adminNote = n.toString();
        }
        ReturnRequest updated = returnService.refund(id, amount, adminNote, request.getRemoteAddr());
        return ResponseEntity.ok(ReturnDtoMapper.toMap(updated));
    }

    /** Admin sends a written reply to the customer (emails them, status unchanged). */
    @PutMapping("/{id}/reply")
    public ResponseEntity<Map<String, Object>> reply(@PathVariable Long id,
                                                     @RequestBody Map<String, String> body) {
        String message = body != null ? body.get("message") : null;
        return ResponseEntity.ok(ReturnDtoMapper.toMap(returnService.reply(id, message)));
    }

    /**
     * Streams a customer-uploaded return photo. {@code permitAll} (like product
     * image views) so {@code <img>} tags render it without an auth header — the id
     * is non-enumerable-sensitive and carries no PII.
     */
    @GetMapping("/photos/{photoId}/view")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> viewPhoto(@PathVariable Long photoId) {
        ReturnRequestService.PhotoData data = returnService.getPhoto(photoId);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        try {
            headers.setContentType(org.springframework.http.MediaType.parseMediaType(data.contentType()));
        } catch (Exception e) {
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
        }
        headers.setContentLength(data.bytes().length);
        headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + (data.fileName() != null ? data.fileName() : "return-photo") + "\"");
        headers.setCacheControl("private, max-age=3600");
        return new ResponseEntity<>(data.bytes(), headers, org.springframework.http.HttpStatus.OK);
    }

    private String note(Map<String, String> body) {
        return body != null ? body.getOrDefault("adminNote", body.get("note")) : null;
    }

    private ReturnStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ReturnStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
