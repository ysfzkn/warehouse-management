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
 * E-Fatura / E-Arşiv fatura yönetim servisi.
 */
public interface InvoiceService {

    /**
     * Sipariş için otomatik fatura oluşturur.
     * Sipariş PAID durumuna geçtiğinde çağrılır.
     */
    InvoiceDto createInvoiceForOrder(Long orderId);

    /**
     * Manuel fatura oluşturma (admin tarafından).
     */
    InvoiceDto createInvoice(InvoiceCreateRequest request);

    /**
     * Faturayı yeniden oluşturur (hata durumunda).
     */
    InvoiceDto regenerateInvoice(Long invoiceId);

    /**
     * Faturayı iptal eder.
     */
    InvoiceDto cancelInvoice(Long invoiceId);

    /**
     * ID ile fatura getirir.
     */
    Optional<InvoiceDto> getInvoiceById(Long invoiceId);

    /**
     * Sipariş ID ile fatura getirir.
     */
    Optional<InvoiceDto> getInvoiceByOrderId(Long orderId);

    /**
     * Fatura PDF'ini indirir.
     */
    byte[] downloadInvoicePdf(Long invoiceId);

    /**
     * Filtrelenmiş fatura listesi.
     */
    Page<InvoiceDto> getInvoices(InvoiceStatus status, InvoiceType invoiceType,
                                  String search, LocalDateTime from, LocalDateTime to,
                                  Pageable pageable);
}
