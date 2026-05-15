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
     * İade faturası (Credit Note) oluşturur.
     *
     * <p>Türkiye e-fatura mevzuatı:
     * <ul>
     *   <li>e-Arşiv 8 gün içinde iptal edilebilir → bu durumda credit note değil,
     *       doğrudan {@link #cancelInvoice(Long)} kullanın.</li>
     *   <li>8 gün sonrasındaki e-Arşiv veya herhangi bir zamandaki e-Fatura için
     *       credit note kesilmelidir. Bu metot UBL-TR ProfileID="IADE" + orijinal
     *       faturanın BillingReference'ını içeren yeni bir Invoice satırı yaratır.</li>
     * </ul></p>
     *
     * @param originalInvoiceId iadeye konu orijinal fatura
     * @param refundAmount     iade tutarı (null ise orijinalin tamamı)
     * @param reason           İade nedeni (UBL Note alanına yazılır)
     * @return yeni oluşturulan credit note Invoice DTO'su
     */
    InvoiceDto createCreditNote(Long originalInvoiceId, java.math.BigDecimal refundAmount, String reason);

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

    /**
     * PENDING durumundaki faturaları Logo'dan sorgular ve güncel statülerini yazar.
     * Scheduled job tarafından çağrılır.
     * @return güncellenen fatura sayısı
     */
    int refreshPendingStatuses();
}
