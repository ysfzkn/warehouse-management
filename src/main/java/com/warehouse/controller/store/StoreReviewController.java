package com.warehouse.controller.store;

import com.warehouse.entity.Customer;
import com.warehouse.entity.Order;
import com.warehouse.entity.Product;
import com.warehouse.entity.Review;
import com.warehouse.entity.ReviewImage;
import com.warehouse.repository.CustomerRepository;
import com.warehouse.repository.OrderRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.ReviewImageRepository;
import com.warehouse.repository.ReviewRepository;
import com.warehouse.security.JwtService;
import com.warehouse.service.PhotoStorageService;
import com.warehouse.util.CustomerTokenExtractor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Storefront product reviews — submission (purchase-verified), edit, delete,
 * photo upload and public listing. Reviews are published only after admin approval.
 */
@RestController
@RequestMapping("/api/store")
public class StoreReviewController {

    private static final int MAX_IMAGES = 5;

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final PhotoStorageService photoStorageService;
    private final JwtService jwtService;

    public StoreReviewController(ReviewRepository reviewRepository,
                                 ReviewImageRepository reviewImageRepository,
                                 OrderRepository orderRepository,
                                 ProductRepository productRepository,
                                 CustomerRepository customerRepository,
                                 PhotoStorageService photoStorageService,
                                 JwtService jwtService) {
        this.reviewRepository = reviewRepository;
        this.reviewImageRepository = reviewImageRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.photoStorageService = photoStorageService;
        this.jwtService = jwtService;
    }

    // ───────────────────────── Public listing ─────────────────────────

    @GetMapping("/products/{productId}/reviews")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> list(@PathVariable Long productId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 50), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> result = reviewRepository.findByProductIdAndApprovedTrueAndVisibleTrue(productId, pageable);

        List<Long> reviewIds = result.getContent().stream().map(Review::getId).collect(Collectors.toList());
        Map<Long, List<ReviewImage>> imagesByReview = new HashMap<>();
        if (!reviewIds.isEmpty()) {
            for (ReviewImage img : reviewImageRepository.findByReviewIdInOrderBySortOrderAscIdAsc(reviewIds)) {
                imagesByReview.computeIfAbsent(img.getReview().getId(), k -> new ArrayList<>()).add(img);
            }
        }

        List<Map<String, Object>> items = result.getContent().stream()
                .map(r -> publicDto(r, imagesByReview.getOrDefault(r.getId(), List.of())))
                .collect(Collectors.toList());

        // Rating distribution (1..5)
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) distribution.put(String.valueOf(i), 0L);
        for (Object[] row : reviewRepository.ratingDistribution(productId)) {
            distribution.put(String.valueOf(((Number) row[0]).intValue()), ((Number) row[1]).longValue());
        }

        Double avg = reviewRepository.getAverageRatingByProductId(productId);
        long count = reviewRepository.countApprovedByProductId(productId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", result.getNumber());
        body.put("totalPages", result.getTotalPages());
        body.put("totalElements", result.getTotalElements());
        body.put("average", avg);
        body.put("count", count);
        body.put("distribution", distribution);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/products/{productId}/reviews/eligibility")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> eligibility(@PathVariable Long productId, HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        Map<String, Object> body = new LinkedHashMap<>();
        if (customerId == null) {
            body.put("loggedIn", false);
            body.put("canReview", false);
            return ResponseEntity.ok(body);
        }
        boolean hasPurchased = orderRepository.hasPurchasedProduct(customerId, productId);
        Optional<Review> mine = reviewRepository.findFirstByCustomerIdAndProductIdOrderByCreatedAtDesc(customerId, productId);
        body.put("loggedIn", true);
        body.put("hasPurchased", hasPurchased);
        body.put("hasReviewed", mine.isPresent());
        body.put("canReview", hasPurchased && mine.isEmpty());
        mine.ifPresent(r -> {
            body.put("myReview", ownDto(r, reviewImageRepository.findByReviewIdOrderBySortOrderAscIdAsc(r.getId())));
        });
        return ResponseEntity.ok(body);
    }

    // ───────────────────────── Submit / edit / delete ─────────────────────────

    @PostMapping("/products/{productId}/reviews")
    @Transactional
    public ResponseEntity<?> submit(@PathVariable Long productId,
                                    @RequestBody Map<String, Object> payload,
                                    HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        if (customerId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Giriş yapmanız gerekiyor."));

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Ürün bulunamadı."));

        if (!orderRepository.hasPurchasedProduct(customerId, productId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Yorum yapabilmek için bu ürünü satın almış olmanız gerekir."));
        }
        if (reviewRepository.existsByCustomerIdAndProductId(customerId, productId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Bu ürün için zaten bir yorumunuz var. Mevcut yorumunuzu düzenleyebilirsiniz."));
        }

        int rating = clampRating(payload.get("rating"));
        if (rating < 1) return ResponseEntity.badRequest().body(Map.of("error", "Lütfen 1-5 arası bir puan verin."));

        List<Long> orderIds = orderRepository.eligibleOrderIds(customerId, productId, PageRequest.of(0, 1));

        Review r = new Review();
        r.setProduct(product);
        r.setCustomer(customerRepository.getReferenceById(customerId));
        if (!orderIds.isEmpty()) r.setOrder(orderRepository.getReferenceById(orderIds.get(0)));
        r.setRating(rating);
        r.setTitle(str(payload.get("title"), 200));
        r.setComment(str(payload.get("comment"), 2000));
        r.setApproved(false);
        r.setVisible(true);
        Review saved = reviewRepository.save(r);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", saved.getId(),
                "message", "Yorumunuz alındı. Onaylandıktan sonra yayınlanacaktır."));
    }

    @PutMapping("/reviews/{id}")
    @Transactional
    public ResponseEntity<?> edit(@PathVariable Long id,
                                  @RequestBody Map<String, Object> payload,
                                  HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        Review r = reviewRepository.findById(id).orElse(null);
        if (r == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Yorum bulunamadı."));
        if (customerId == null || r.getCustomer() == null || !customerId.equals(r.getCustomer().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Bu yorumu düzenleme yetkiniz yok."));
        }
        int rating = clampRating(payload.get("rating"));
        if (rating >= 1) r.setRating(rating);
        if (payload.containsKey("title")) r.setTitle(str(payload.get("title"), 200));
        if (payload.containsKey("comment")) r.setComment(str(payload.get("comment"), 2000));
        r.setApproved(false); // re-moderate after an edit
        reviewRepository.save(r);
        return ResponseEntity.ok(Map.of("message", "Yorumunuz güncellendi. Tekrar onaya gönderildi."));
    }

    @DeleteMapping("/reviews/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        Review r = reviewRepository.findById(id).orElse(null);
        if (r == null) return ResponseEntity.noContent().build();
        if (customerId == null || r.getCustomer() == null || !customerId.equals(r.getCustomer().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Bu yorumu silme yetkiniz yok."));
        }
        for (ReviewImage img : reviewImageRepository.findByReviewIdOrderBySortOrderAscIdAsc(id)) {
            safeDeleteStorage(img.getStorageKey());
        }
        reviewRepository.delete(r); // cascades review_images rows
        return ResponseEntity.noContent().build();
    }

    // ───────────────────────── Photos ─────────────────────────

    @PostMapping("/reviews/{id}/images")
    @Transactional
    public ResponseEntity<?> uploadImage(@PathVariable Long id,
                                         @RequestParam("file") MultipartFile file,
                                         HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        Review r = reviewRepository.findById(id).orElse(null);
        if (r == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Yorum bulunamadı."));
        if (customerId == null || r.getCustomer() == null || !customerId.equals(r.getCustomer().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Yetkiniz yok."));
        }
        if (file == null || file.isEmpty() || file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of("error", "Sadece görsel yüklenebilir."));
        }
        if (reviewImageRepository.countByReviewId(id) >= MAX_IMAGES) {
            return ResponseEntity.badRequest().body(Map.of("error", "En fazla " + MAX_IMAGES + " görsel ekleyebilirsiniz."));
        }
        try {
            String key = photoStorageService.storeDocument("reviews/" + id, file.getOriginalFilename(), file.getContentType(), file.getInputStream());
            ReviewImage img = new ReviewImage();
            img.setReview(r);
            img.setFileName(file.getOriginalFilename());
            img.setStorageKey(key);
            img.setContentType(file.getContentType());
            img.setSizeBytes(file.getSize());
            img.setSortOrder((int) reviewImageRepository.countByReviewId(id));
            ReviewImage savedImg = reviewImageRepository.save(img);
            r.setApproved(false); // re-moderate after adding a photo
            reviewRepository.save(r);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id", savedImg.getId(),
                    "url", "/api/store/reviews/images/" + savedImg.getId() + "/view"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Görsel yüklenemedi."));
        }
    }

    @DeleteMapping("/reviews/images/{imageId}")
    @Transactional
    public ResponseEntity<?> deleteImage(@PathVariable Long imageId, HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        ReviewImage img = reviewImageRepository.findById(imageId).orElse(null);
        if (img == null) return ResponseEntity.noContent().build();
        Review r = img.getReview();
        if (customerId == null || r == null || r.getCustomer() == null || !customerId.equals(r.getCustomer().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Yetkiniz yok."));
        }
        safeDeleteStorage(img.getStorageKey());
        reviewImageRepository.delete(img);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reviews/images/{id}/view")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> viewImage(@PathVariable Long id) {
        ReviewImage img = reviewImageRepository.findById(id).orElse(null);
        if (img == null) return ResponseEntity.notFound().build();
        try (java.io.InputStream is = photoStorageService.openDocumentStream(img.getStorageKey())) {
            byte[] bytes = is.readAllBytes();
            HttpHeaders headers = new HttpHeaders();
            try {
                headers.setContentType(MediaType.parseMediaType(img.getContentType() != null ? img.getContentType() : "image/jpeg"));
            } catch (Exception ignored) {
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            }
            headers.setContentLength(bytes.length);
            headers.setCacheControl("public, max-age=86400");
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ───────────────────────── helpers ─────────────────────────

    private Map<String, Object> publicDto(Review r, List<ReviewImage> images) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("rating", r.getRating());
        m.put("title", r.getTitle());
        m.put("comment", r.getComment());
        m.put("authorName", maskName(r.getCustomer()));
        m.put("verifiedPurchase", r.getOrder() != null);
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        m.put("adminReply", r.getAdminReply());
        m.put("images", imageUrls(images));
        return m;
    }

    private Map<String, Object> ownDto(Review r, List<ReviewImage> images) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("rating", r.getRating());
        m.put("title", r.getTitle());
        m.put("comment", r.getComment());
        m.put("approved", r.isApproved());
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        m.put("images", images.stream().map(img -> Map.of(
                "id", img.getId(),
                "url", "/api/store/reviews/images/" + img.getId() + "/view")).collect(Collectors.toList()));
        return m;
    }

    private List<String> imageUrls(List<ReviewImage> images) {
        return images.stream().map(img -> "/api/store/reviews/images/" + img.getId() + "/view").collect(Collectors.toList());
    }

    private String maskName(Customer c) {
        if (c == null) return "Müşteri";
        String fn = c.getFirstName() != null ? c.getFirstName().trim() : "";
        String ln = c.getLastName() != null ? c.getLastName().trim() : "";
        String last = ln.isEmpty() ? "" : " " + ln.charAt(0) + ".";
        String res = (fn + last).trim();
        return res.isEmpty() ? "Müşteri" : res;
    }

    private void safeDeleteStorage(String key) {
        try { if (key != null) photoStorageService.deleteDocument(key); } catch (Exception ignored) { }
    }

    private static int clampRating(Object v) {
        if (v == null) return 0;
        try {
            int n = (v instanceof Number) ? ((Number) v).intValue() : Integer.parseInt(v.toString().trim());
            return (n >= 1 && n <= 5) ? n : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String str(Object v, int max) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
