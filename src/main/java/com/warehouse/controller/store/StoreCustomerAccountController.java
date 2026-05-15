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
 * KVKK Madde 11 (e) & GDPR Article 17 (right to erasure) + Article 20
 * (right to data portability) uyumu için müşteri hesap yönetimi endpoint'leri.
 *
 * Endpoint'ler:
 * <ul>
 *   <li>{@code GET  /api/store/account/data-export} — Müşterinin tüm PII'sini ve
 *       sipariş geçmişini JSON olarak indirir.</li>
 *   <li>{@code DELETE /api/store/account} — Hesabı anonimleştirir. Sipariş ve
 *       fatura geçmişi yasal saklama süresi gereği korunur, fakat tüm PII
 *       (ad, e-posta, telefon, adres) silinir. İşlem geri alınamaz.</li>
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
     * KVKK Madde 11 (e) — kişisel verilerin yurt içinde veya yurt dışında üçüncü
     * kişilere aktarılıp aktarılmadığını bilme + verilerin işlenmesinin nedenleri
     * hakkında bilgi alma hakkının teknik karşılığı.
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
     * KVKK Madde 11 (e) — kişisel verilerin silinmesini isteme hakkı.
     * Tipik yapı: aktif siparişi (PENDING/PAID/SHIPPING) olan hesaplar silinmez,
     * önce siparişin tamamlanması veya iptal edilmesi gerekir.
     */
    @DeleteMapping
    public ResponseEntity<?> deleteAccount(HttpServletRequest request, @RequestBody(required = false) DeleteAccountRequest body) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Giriş yapın"));
        }
        // Çift onay için: client "DELETE_MY_DATA" sabitini gönderir
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
        /** Çift onay için sabit: "DELETE_MY_DATA". */
        public String confirmation;
        /** Opsiyonel açıklama. */
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
