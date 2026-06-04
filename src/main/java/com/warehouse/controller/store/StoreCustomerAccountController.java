package com.warehouse.controller.store;

import com.warehouse.entity.Customer;
import com.warehouse.repository.CustomerRepository;
import com.warehouse.security.JwtService;
import com.warehouse.service.CustomerAccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Customer account management endpoints for compliance with KVKK Article 11 (e)
 * & GDPR Article 17 (right to erasure) + Article 20 (right to data portability).
 *
 * Endpoints:
 * <ul>
 *   <li>{@code GET  /api/store/account/data-export} — Downloads all of the
 *       customer's PII and order history as JSON.</li>
 *   <li>{@code DELETE /api/store/account} — Anonymizes the account. Order and
 *       invoice history is retained as required by the legal retention period,
 *       but all PII (name, email, phone, address) is deleted. This operation
 *       cannot be undone.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/store/account")
public class StoreCustomerAccountController {

    private static final Logger log = LoggerFactory.getLogger(StoreCustomerAccountController.class);

    private final CustomerAccountService accountService;
    private final CustomerRepository customerRepo;
    private final JwtService jwtService;

    public StoreCustomerAccountController(CustomerAccountService accountService,
                                           CustomerRepository customerRepo,
                                           JwtService jwtService) {
        this.accountService = accountService;
        this.customerRepo = customerRepo;
        this.jwtService = jwtService;
    }

    /**
     * KVKK Article 11 (e) — the technical counterpart of the right to know
     * whether personal data has been transferred to third parties domestically
     * or abroad, plus the right to obtain information about the reasons for which
     * the data is processed.
     */
    @GetMapping("/data-export")
    public ResponseEntity<?> exportData(HttpServletRequest request) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Giriş yapın"));
        }
        try {
            Map<String, Object> data = accountService.exportCustomerData(customerId);
            String filename = "kisisel-verilerim-" + customerId + "-" + java.time.LocalDate.now() + ".json";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(data);
        } catch (Exception e) {
            log.error("[KVKK] Veri ihracı başarısız (customerId={})", customerId, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Veri ihracı başarısız: " + e.getMessage()));
        }
    }

    /**
     * KVKK Article 11 (e) — the right to request deletion of personal data.
     * Typical structure: accounts with active orders (PENDING/PAID/SHIPPING) are
     * not deleted; the order must first be completed or cancelled.
     */
    @DeleteMapping
    public ResponseEntity<?> deleteAccount(HttpServletRequest request, @RequestBody(required = false) DeleteAccountRequest body) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Giriş yapın"));
        }
        // For double confirmation: the client sends the constant "DELETE_MY_DATA"
        if (body == null || !"DELETE_MY_DATA".equals(body.confirmation)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Hesap silme onayı eksik (confirmation alanı 'DELETE_MY_DATA' olmalı)."));
        }
        try {
            Customer c = customerRepo.findById(customerId).orElse(null);
            if (c == null) return ResponseEntity.notFound().build();
            CustomerAccountService.DeletionResult res = accountService.anonymizeAccount(customerId, body.reason);
            log.info("[KVKK] Hesap anonimleştirildi: customerId={}, korunan sipariş={}", customerId, res.preservedOrders());
            return ResponseEntity.ok(Map.of(
                    "message", "Hesabınız anonimleştirildi. Yasal saklama gereği sipariş ve fatura geçmişi korundu, kişisel verileriniz silindi.",
                    "preservedOrders", res.preservedOrders(),
                    "anonymizedAt", res.anonymizedAt().toString()
            ));
        } catch (CustomerAccountService.AccountDeletionBlockedException e) {
            return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("[KVKK] Hesap silme başarısız (customerId={})", customerId, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Hesap silme başarısız: " + e.getMessage()));
        }
    }

    // ── DTO ──
    public static class DeleteAccountRequest {
        /** Constant for double confirmation: "DELETE_MY_DATA". */
        public String confirmation;
        /** Optional description. */
        public String reason;
    }

    // ── Helpers ──
    private Long extractCustomerId(HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
            else if (request.getCookies() != null) {
                for (var c : request.getCookies()) {
                    if ("access_token".equals(c.getName())) { token = c.getValue(); break; }
                }
            }
            if (token == null) return null;
            return jwtService.extractCustomerId(token);
        } catch (Exception e) { return null; }
    }
}
