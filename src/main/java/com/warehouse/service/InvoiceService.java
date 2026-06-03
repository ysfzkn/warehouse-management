package com.warehouse.service;

import com.warehouse.dto.InvoiceCreateRequest;
import com.warehouse.dto.InvoiceDto;
import com.warehouse.enums.InvoiceStatus;
import com.warehouse.enums.InvoiceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * E-Fatura / e-Arşiv invoice management service.
 */
public interface InvoiceService {

    /**
     * Creates an invoice automatically for an order.
     * Called when the order transitions to PAID status.
     */
    InvoiceDto createInvoiceForOrder(Long orderId);

    /**
     * Manual invoice creation (by an admin).
     */
    InvoiceDto createInvoice(InvoiceCreateRequest request);

    /**
     * Regenerates the invoice (in case of an error).
     */
    InvoiceDto regenerateInvoice(Long invoiceId);

    /**
     * Cancels the invoice.
     */
    InvoiceDto cancelInvoice(Long invoiceId);

    /**
     * Creates a return invoice (Credit Note).
     *
     * <p>Turkey e-invoice regulations:
     * <ul>
     *   <li>An e-Arşiv invoice can be cancelled within 8 days → in that case use
     *       {@link #cancelInvoice(Long)} directly instead of a credit note.</li>
     *   <li>For an e-Arşiv invoice past 8 days, or an E-Fatura at any time, a credit
     *       note must be issued. This method creates a new Invoice row with UBL-TR
     *       ProfileID="IADE" + the original invoice's BillingReference.</li>
     * </ul></p>
     *
     * @param originalInvoiceId the original invoice subject to the return
     * @param refundAmount     the refund amount (null means the full original amount)
     * @param reason           the return reason (written to the UBL Note field)
     * @return the newly created credit note Invoice DTO
     */
    InvoiceDto createCreditNote(Long originalInvoiceId, java.math.BigDecimal refundAmount, String reason);

    /**
     * Retrieves an invoice by ID.
     */
    Optional<InvoiceDto> getInvoiceById(Long invoiceId);

    /**
     * Retrieves an invoice by order ID.
     */
    Optional<InvoiceDto> getInvoiceByOrderId(Long orderId);

    /**
     * Downloads the invoice PDF.
     */
    byte[] downloadInvoicePdf(Long invoiceId);

    /**
     * Filtered invoice list.
     */
    Page<InvoiceDto> getInvoices(InvoiceStatus status, InvoiceType invoiceType,
                                  String search, LocalDateTime from, LocalDateTime to,
                                  Pageable pageable);

    /**
     * Queries PENDING invoices from Logo and writes their current statuses.
     * Called by a scheduled job.
     * @return the number of updated invoices
     */
    int refreshPendingStatuses();
}
