package com.warehouse.controller;

import com.warehouse.entity.Review;
import com.warehouse.entity.ReviewImage;
import com.warehouse.repository.ReviewImageRepository;
import com.warehouse.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin: product review moderation endpoints.
 *
 * <ul>
 *   <li>{@code GET /api/admin/reviews?approved=false} — reviews awaiting approval</li>
 *   <li>{@code POST /api/admin/reviews/{id}/approve} — approve a review</li>
 *   <li>{@code POST /api/admin/reviews/{id}/reject} — delete a review</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewsController {

    private static final Logger log = LoggerFactory.getLogger(AdminReviewsController.class);

    private final ReviewRepository reviewRepo;
    private final ReviewImageRepository reviewImageRepo;

    public AdminReviewsController(ReviewRepository reviewRepo, ReviewImageRepository reviewImageRepo) {
        this.reviewRepo = reviewRepo;
        this.reviewImageRepo = reviewImageRepo;
    }

    /** Dashboard insight cards: totals, pending count, overall average + star distribution. */
    @GetMapping("/insights")
    public ResponseEntity<Map<String, Object>> insights() {
        long total = reviewRepo.count();
        long pending = reviewRepo.countByApproved(false);
        Double avg = reviewRepo.overallAverage();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("totalReviews", total);
        body.put("pendingReviews", pending);
        body.put("approvedReviews", total - pending);
        body.put("averageRating", avg);
        return ResponseEntity.ok(body);
    }

    /** Admin reply to a review (shown publicly under the review). */
    @PutMapping("/{id}/reply")
    public ResponseEntity<?> reply(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Review r = reviewRepo.findById(id).orElseThrow();
        Object reply = body.get("reply");
        String text = reply != null ? reply.toString().trim() : null;
        r.setAdminReply(text == null || text.isEmpty() ? null : (text.length() > 1000 ? text.substring(0, 1000) : text));
        r.setAdminReplyAt(text == null || text.isEmpty() ? null : java.time.LocalDateTime.now());
        reviewRepo.save(r);
        return ResponseEntity.ok(Map.of("message", "Yanıt kaydedildi"));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) Boolean approved,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        var pageable = PageRequest.of(page, Math.min(size, 100), sort);

        Page<Review> result;
        if (approved == null) {
            result = reviewRepo.findAll(pageable);
        } else {
            result = reviewRepo.findByApproved(approved, pageable);
        }
        return ResponseEntity.ok(Map.of(
                "items", result.getContent().stream().map(this::toDto).collect(Collectors.toList()),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber(),
                "size", result.getSize()
        ));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        Review r = reviewRepo.findById(id).orElseThrow();
        r.setApproved(true);
        reviewRepo.save(r);
        log.info("[Reviews] Approved id={}, product={}, rating={}",
                r.getId(), r.getProduct() != null ? r.getProduct().getId() : null, r.getRating());
        return ResponseEntity.ok(Map.of("message", "Yorum onaylandı"));
    }

    /**
     * Review rejection/deletion. Both routes map to the same handler:
     *   - POST /{id}/reject?reason=...   (for the admin UI "Reject" button)
     *   - DELETE /{id}                   (REST style, optional)
     * Combined with @RequestMapping since Spring allows only a single annotation.
     */
    @org.springframework.web.bind.annotation.RequestMapping(
            value = { "/{id}/reject", "/{id}" },
            method = { org.springframework.web.bind.annotation.RequestMethod.POST,
                       org.springframework.web.bind.annotation.RequestMethod.DELETE })
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        Review r = reviewRepo.findById(id).orElseThrow();
        log.info("[Reviews] Rejected/deleted id={}, product={}, reason={}",
                r.getId(), r.getProduct() != null ? r.getProduct().getId() : null, reason);
        reviewRepo.delete(r);
        return ResponseEntity.ok(Map.of("message", "Yorum silindi"));
    }

    private Map<String, Object> toDto(Review r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("rating", r.getRating());
        m.put("title", r.getTitle());
        m.put("comment", r.getComment());
        m.put("approved", r.isApproved());
        m.put("verifiedPurchase", r.getOrder() != null);
        m.put("adminReply", r.getAdminReply());
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        m.put("images", reviewImageRepo.findByReviewIdOrderBySortOrderAscIdAsc(r.getId()).stream()
                .map((ReviewImage img) -> "/api/store/reviews/images/" + img.getId() + "/view")
                .collect(Collectors.toList()));
        if (r.getProduct() != null) {
            m.put("productId", r.getProduct().getId());
            m.put("productName", r.getProduct().getName());
            m.put("productSlug", r.getProduct().getSlug());
        }
        if (r.getCustomer() != null) {
            m.put("customerId", r.getCustomer().getId());
            m.put("customerName", r.getCustomer().getFullName());
            m.put("customerEmail", r.getCustomer().getEmail());
        }
        return m;
    }
}
