package com.warehouse.controller;

import com.warehouse.dto.InvoiceCreateRequest;
import com.warehouse.dto.InvoiceDto;
import com.warehouse.dto.PagedResponse;
import com.warehouse.enums.InvoiceStatus;
import com.warehouse.enums.InvoiceType;
import com.warehouse.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/invoices")
@RequiredArgsConstructor
public class AdminInvoiceController {

    private static final Logger logger = LoggerFactory.getLogger(AdminInvoiceController.class);
    private final InvoiceService invoiceService;

    /**
     * Invoice list (filterable, paginated).
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<PagedResponse<InvoiceDto>> getInvoices(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) InvoiceType invoiceType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LocalDateTime fromDate = parseDate(from);
        LocalDateTime toDate = parseDate(to);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));

        Page<InvoiceDto> invoices = invoiceService.getInvoices(status, invoiceType, search, fromDate, toDate, pageable);

        PagedResponse<InvoiceDto> response = new PagedResponse<>(
                invoices.getContent(),
                invoices.getNumber(),
                invoices.getSize(),
                invoices.getTotalElements(),
                invoices.getTotalPages(),
                invoices.isFirst(),
                invoices.isLast()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Single invoice detail.
     */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<InvoiceDto> getInvoice(@PathVariable Long id) {
        return invoiceService.getInvoiceById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Invoice detail by order ID.
     */
    @GetMapping("/by-order/{orderId}")
    @Transactional(readOnly = true)
    public ResponseEntity<InvoiceDto> getInvoiceByOrder(@PathVariable Long orderId) {
        return invoiceService.getInvoiceByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create an invoice for an order (automatic or manual).
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvoiceDto> createInvoice(@RequestBody InvoiceCreateRequest request) {
        logger.info("Manuel fatura oluşturma: siparisId={}", request.getOrderId());
        InvoiceDto invoice = invoiceService.createInvoice(request);
        return ResponseEntity.ok(invoice);
    }

    /**
     * Create an invoice automatically by order ID.
     */
    @PostMapping("/auto/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvoiceDto> createInvoiceForOrder(@PathVariable Long orderId) {
        logger.info("Otomatik fatura oluşturma: siparisId={}", orderId);
        InvoiceDto invoice = invoiceService.createInvoiceForOrder(orderId);
        return ResponseEntity.ok(invoice);
    }

    /**
     * Regenerate the invoice (in case of an error).
     */
    @PostMapping("/{id}/regenerate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvoiceDto> regenerateInvoice(@PathVariable Long id) {
        logger.info("Fatura yeniden oluşturma: id={}", id);
        InvoiceDto invoice = invoiceService.regenerateInvoice(id);
        return ResponseEntity.ok(invoice);
    }

    /**
     * Cancel the invoice.
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvoiceDto> cancelInvoice(@PathVariable Long id) {
        logger.info("Fatura iptal: id={}", id);
        InvoiceDto invoice = invoiceService.cancelInvoice(id);
        return ResponseEntity.ok(invoice);
    }

    /**
     * Create a return invoice (Credit Note).
     *
     * <p>Turkey e-invoice return rules:
     * <ul>
     *   <li>e-Arşiv within 8 days → use the {@code /cancel} endpoint (CANCELEARCHIVEINVOICE)</li>
     *   <li>E-Fatura or e-Arşiv past 8 days → this endpoint (a new IADE invoice is issued)</li>
     * </ul>
     * It is also triggered automatically via {@code InvoiceCancellationListener} after a
     * RETURNED/REFUNDED order status change; this endpoint is for manual/retry use.</p>
     */
    @PostMapping("/{id}/credit-note")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvoiceDto> createCreditNote(@PathVariable Long id,
                                                        @RequestBody(required = false) CreditNoteRequest body) {
        java.math.BigDecimal refundAmount = body != null ? body.refundAmount : null;
        String reason = body != null ? body.reason : null;
        logger.info("Credit note manuel kesim: originalInvoiceId={}, refund={}, reason={}",
                id, refundAmount, reason);
        InvoiceDto creditNote = invoiceService.createCreditNote(id, refundAmount, reason);
        return ResponseEntity.ok(creditNote);
    }

    /** Request body for a manual credit note. */
    public static class CreditNoteRequest {
        /** If null, the full original invoice amount is refunded. */
        public java.math.BigDecimal refundAmount;
        /** Return reason to be written to the UBL Note field. */
        public String reason;
    }

    /**
     * Download the invoice PDF.
     */
    @GetMapping("/{id}/pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        byte[] pdf = invoiceService.downloadInvoicePdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fatura-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDateTime.parse(dateStr);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateStr.replace("Z", ""));
            } catch (Exception ex) {
                logger.warn("Geçersiz tarih formatı: {}", dateStr);
                return null;
            }
        }
    }
}
