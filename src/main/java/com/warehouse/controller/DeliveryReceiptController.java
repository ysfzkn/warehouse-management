package com.warehouse.controller;

import com.warehouse.dto.DeliveryReceiptDto;
import com.warehouse.enums.DeliveryReceiptStatus;
import com.warehouse.security.SignedUrlService;
import com.warehouse.service.DeliveryReceiptService;
import com.warehouse.util.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Delivery receipts.
 *
 * <p>Split across two path prefixes on purpose. The per-transfer operations live under
 * {@code /api/admin/stock-transfers/**}, which the security chain already opens to the
 * warehouse roles — they are the people printing receipts and photographing the signed
 * page. The archive endpoints under {@code /api/admin/delivery-receipts/**} fall through
 * to the admin-only rule, because browsing every delivery ever made is a management view,
 * not a shop-floor one.</p>
 */
@RestController
public class DeliveryReceiptController {

    private static final String SIGNED_RESOURCE = "receipt-attachment";
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final DeliveryReceiptService receiptService;

    public DeliveryReceiptController(DeliveryReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    // ─────────────────── Per transfer (warehouse roles) ───────────────────

    /** Issues the receipt, or reprints it with the revision bumped. */
    @PostMapping("/api/admin/stock-transfers/{transferId}/receipt")
    public ResponseEntity<DeliveryReceiptDto> issue(@PathVariable Long transferId) {
        return ResponseEntity.ok(receiptService.issue(transferId, CurrentUser.usernameOrSystem()));
    }

    /** Null body with 204 when the shipment has no receipt yet — the panel shows "Düzenle". */
    @GetMapping("/api/admin/stock-transfers/{transferId}/receipt")
    public ResponseEntity<DeliveryReceiptDto> get(@PathVariable Long transferId) {
        DeliveryReceiptDto dto = receiptService.findByTransfer(transferId);
        return dto == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(dto);
    }

    /**
     * The printable page.
     *
     * <p>Returned as HTML to an authenticated XHR rather than opened directly in a tab:
     * a {@code window.open} cannot carry the Bearer token, and handing this page a signed
     * URL instead would put a working link to customer names and addresses into browser
     * history and the clipboard. The panel fetches it and writes it into a new window.</p>
     */
    @GetMapping(value = "/api/admin/stock-transfers/{transferId}/receipt/print",
                produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> print(@PathVariable Long transferId) {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(MediaType.TEXT_HTML_VALUE + ";charset=UTF-8"))
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(receiptService.renderHtml(transferId, true));
    }

    @GetMapping(value = "/api/admin/stock-transfers/{transferId}/receipt/pdf",
                produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long transferId) {
        DeliveryReceiptDto dto = receiptService.findByTransfer(transferId);
        byte[] pdf = receiptService.renderPdf(transferId, CurrentUser.usernameOrSystem());
        String name = "makbuz-" + (dto != null ? dto.getReceiptNo() : transferId) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(name))
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(pdf);
    }

    /**
     * Receipt state for a page of transfers, keyed by transfer id.
     *
     * <p>The list screen needs to show which shipments already have a receipt and which
     * are still waiting for the signed copy. Asking per row would be one request per
     * line; this answers the whole page in one.</p>
     */
    @PostMapping("/api/admin/stock-transfers/receipts/by-transfers")
    public ResponseEntity<Map<Long, DeliveryReceiptDto>> byTransfers(
            @RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body == null ? null : body.get("transferIds");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.ok(Map.of());
        }
        // Bound the batch so a crafted request cannot ask for the whole table at once.
        List<Long> capped = ids.size() > 200 ? ids.subList(0, 200) : ids;
        return ResponseEntity.ok(receiptService.findByTransferIds(capped));
    }

    /** One file for a batch of shipments, in the order the list screen shows them. */
    @PostMapping(value = "/api/admin/stock-transfers/receipts/bulk-pdf",
                 produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> bulkPdf(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body == null ? null : body.get("transferIds");
        byte[] pdf = receiptService.renderBulkPdf(ids, CurrentUser.usernameOrSystem());
        String name = "makbuzlar-" + LocalDateTime.now().format(FILE_STAMP) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(name))
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(pdf);
    }

    @PostMapping("/api/admin/stock-transfers/{transferId}/receipt/confirm")
    public ResponseEntity<DeliveryReceiptDto> confirm(@PathVariable Long transferId,
                                                      @RequestBody ConfirmRequest request) {
        return ResponseEntity.ok(receiptService.confirmDelivery(
                transferId,
                request.deliveredByName(),
                request.receivedByName(),
                request.deliveredAt(),
                request.note(),
                CurrentUser.usernameOrSystem()));
    }

    @PostMapping("/api/admin/stock-transfers/{transferId}/receipt/attachments")
    public ResponseEntity<DeliveryReceiptDto> upload(@PathVariable Long transferId,
                                                     @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                receiptService.addAttachment(transferId, file, CurrentUser.usernameOrSystem()));
    }

    // ───────────────────── Archive (admin only) ───────────────────────────

    @GetMapping("/api/admin/delivery-receipts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<DeliveryReceiptDto>> list(
            @RequestParam(required = false) DeliveryReceiptStatus status,
            @RequestParam(required = false) Boolean hasSignedCopy,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(100, Math.max(1, size));
        return ResponseEntity.ok(receiptService.search(status, hasSignedCopy, from, to, search,
                PageRequest.of(Math.max(0, page), safeSize, Sort.by(Sort.Direction.DESC, "issuedAt"))));
    }

    @GetMapping("/api/admin/delivery-receipts/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(receiptService.stats());
    }

    @DeleteMapping("/api/admin/delivery-receipts/attachments/{attachmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long attachmentId) {
        receiptService.deleteAttachment(attachmentId, CurrentUser.usernameOrSystem());
        return ResponseEntity.noContent().build();
    }

    /**
     * Serves an uploaded signed copy.
     *
     * <p>Reachable without a session because the panel renders these in {@code <img>} and
     * {@code <iframe>} elements, which cannot send an Authorization header — so the
     * authorisation lives in the URL instead: a signature this server issued for this
     * attachment, which expires. Without it, sequential ids would let anyone page through
     * every customer's signed delivery note.</p>
     */
    @GetMapping("/api/admin/delivery-receipts/attachments/{attachmentId}/view")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> viewAttachment(@PathVariable Long attachmentId,
                                                 @RequestParam(value = "exp", required = false) String exp,
                                                 @RequestParam(value = "sig", required = false) String sig) {
        if (!SignedUrlService.valid(SIGNED_RESOURCE, attachmentId, exp, sig)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        DeliveryReceiptService.StoredAttachment stored = receiptService.loadAttachment(attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + safeAscii(stored.fileName()) + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(org.springframework.http.CacheControl.noCache().cachePrivate())
                .body(stored.bytes());
    }

    // ─────────────────────────────── Helpers ──────────────────────────────

    /**
     * RFC 5987 form so Turkish characters survive the download name; the plain
     * {@code filename} stays as an ASCII fallback for older clients.
     */
    private static String contentDisposition(String fileName) {
        String ascii = safeAscii(fileName);
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    /**
     * ASCII fallback for the {@code filename} parameter. Folds Turkish letters by hand
     * rather than reusing {@code TurkishText.normalize}, which also flattens dots and
     * would turn {@code makbuz-TM-2026-000042.pdf} into a name with no extension.
     */
    private static String safeAscii(String value) {
        if (value == null || value.isBlank()) return "makbuz";
        String folded = value
                .replace('ı', 'i').replace('İ', 'I')
                .replace('ş', 's').replace('Ş', 'S')
                .replace('ğ', 'g').replace('Ğ', 'G')
                .replace('ü', 'u').replace('Ü', 'U')
                .replace('ö', 'o').replace('Ö', 'O')
                .replace('ç', 'c').replace('Ç', 'C');
        String cleaned = folded.replaceAll("[^A-Za-z0-9._-]", "-").replaceAll("-{2,}", "-");
        return cleaned.isBlank() ? "makbuz" : cleaned;
    }

    /** Delivery confirmation payload. */
    public record ConfirmRequest(String deliveredByName,
                                 String receivedByName,
                                 @com.fasterxml.jackson.annotation.JsonFormat(
                                         shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING,
                                         pattern = "yyyy-MM-dd'T'HH:mm:ss")
                                 LocalDateTime deliveredAt,
                                 String note) {}
}
