package com.warehouse.service.impl;

import com.warehouse.dto.InvoiceCreateRequest;
import com.warehouse.dto.InvoiceDto;
import com.warehouse.entity.Invoice;
import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;
import com.warehouse.enums.InvoiceStatus;
import com.warehouse.enums.InvoiceType;
import com.warehouse.enums.OrderStatus;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.InvoiceRepository;
import com.warehouse.repository.OrderItemRepository;
import com.warehouse.repository.OrderRepository;
import com.warehouse.service.InvoiceService;
import com.warehouse.service.SiteSettingService;
import com.warehouse.service.EmailService;
import com.warehouse.service.invoice.InvoiceNumberGenerator;
import com.warehouse.service.invoice.InvoiceProvider;
import com.warehouse.service.invoice.InvoiceResult;
import com.warehouse.service.invoice.logo.UblTrInvoiceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceServiceImpl.class);

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final List<InvoiceProvider> providers;
    private final SiteSettingService settingService;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final EmailService emailService;

    public InvoiceServiceImpl(InvoiceRepository invoiceRepository,
                               OrderRepository orderRepository,
                               OrderItemRepository orderItemRepository,
                               List<InvoiceProvider> providers,
                               SiteSettingService settingService,
                               InvoiceNumberGenerator invoiceNumberGenerator,
                               EmailService emailService) {
        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.providers = providers;
        this.settingService = settingService;
        this.invoiceNumberGenerator = invoiceNumberGenerator;
        this.emailService = emailService;
    }

    /**
     * Returns the first enabled provider (isEnabled()==true).
     * If none are enabled, the MOCK provider is used as a fallback.
     *
     * <p><b>Production guard:</b> when {@code invoice.mock-enabled=false} the MOCK
     * provider bean does not exist; in that case, if no fallback is found, null is
     * returned and the caller throws an "invoice provider not defined" error. This
     * makes it impossible to accidentally issue a fake invoice in production.</p>
     */
    private InvoiceProvider getActiveProvider() {
        return providers.stream()
                .filter(InvoiceProvider::isEnabled)
                .findFirst()
                .orElseGet(() -> providers.stream()
                        .filter(p -> "MOCK".equalsIgnoreCase(p.getProviderName()))
                        .findFirst()
                        .orElse(providers.isEmpty() ? null : providers.get(0)));
    }

    @Override
    @Transactional
    public InvoiceDto createInvoiceForOrder(Long orderId) {
        // Check whether an invoice already exists
        Optional<Invoice> existing = invoiceRepository.findByOrderId(orderId);
        if (existing.isPresent() && existing.get().getStatus() != InvoiceStatus.ERROR
                && existing.get().getStatus() != InvoiceStatus.CANCELLED) {
            logger.info("Sipariş {} için zaten fatura mevcut: {}", orderId, existing.get().getInvoiceNumber());
            return toDto(existing.get());
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.ORDER_NOT_FOUND, "ID: " + orderId));

        return createAndSendInvoice(order, InvoiceType.E_ARSIV, null);
    }

    @Override
    @Transactional
    public InvoiceDto createInvoice(InvoiceCreateRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.ORDER_NOT_FOUND, "ID: " + request.getOrderId()));

        InvoiceType type = request.getInvoiceType() != null ? request.getInvoiceType() : InvoiceType.E_ARSIV;
        return createAndSendInvoice(order, type, request.getNote());
    }

    @Override
    @Transactional
    public InvoiceDto regenerateInvoice(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.INVOICE_NOT_FOUND, "ID: " + invoiceId));

        if (invoice.getStatus() != InvoiceStatus.ERROR && invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new WarehouseManagementException(ErrorCode.INVOICE_CANNOT_REGENERATE,
                    "Sadece DRAFT veya ERROR durumundaki faturalar yeniden oluşturulabilir.");
        }

        return sendToProvider(invoice);
    }

    @Override
    @Transactional
    public InvoiceDto cancelInvoice(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.INVOICE_NOT_FOUND, "ID: " + invoiceId));

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new WarehouseManagementException(ErrorCode.INVOICE_ALREADY_CANCELLED,
                    "Fatura zaten iptal edilmiş.");
        }

        try {
            if (invoice.getProviderInvoiceId() != null) {
                InvoiceProvider activeProvider = getActiveProvider();
                if (activeProvider != null) {
                    InvoiceResult result = activeProvider.cancelInvoice(invoice.getProviderInvoiceId());
                    invoice.setGibResponse(result.getGibResponse());
                }
            }
            invoice.setStatus(InvoiceStatus.CANCELLED);
            invoiceRepository.save(invoice);
            logger.info("Fatura iptal edildi: {} (sipariş: {})", invoice.getInvoiceNumber(),
                    invoice.getOrder().getOrderNumber());
        } catch (Exception e) {
            logger.error("Fatura iptal hatası: {}", invoiceId, e);
            invoice.setErrorMessage("İptal hatası: " + e.getMessage());
            invoiceRepository.save(invoice);
        }

        return toDto(invoice);
    }

    @Override
    @Transactional
    public InvoiceDto createCreditNote(Long originalInvoiceId, java.math.BigDecimal refundAmount, String reason) {
        Invoice original = invoiceRepository.findById(originalInvoiceId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.INVOICE_NOT_FOUND,
                        "ID: " + originalInvoiceId));

        // Validation: original must be APPROVED; a credit note cannot be issued against a credit note
        if (original.getStatus() != InvoiceStatus.APPROVED) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Sadece APPROVED faturalar için iade faturası kesilebilir (mevcut: "
                    + original.getStatus() + ").");
        }
        if (original.isCreditNote()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Bir credit note'a karşı yeni credit note kesilemez.");
        }

        // Block if an active credit note already exists for the same original invoice (idempotency)
        // (If the repository lacks this query, filter within a simple findAll)
        java.util.List<Invoice> existingCreditNotes = invoiceRepository
                .findByCreditedInvoiceId(originalInvoiceId);
        boolean hasActiveCreditNote = existingCreditNotes.stream()
                .anyMatch(c -> c.getStatus() != InvoiceStatus.CANCELLED
                            && c.getStatus() != InvoiceStatus.ERROR);
        if (hasActiveCreditNote) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Bu fatura için zaten aktif bir iade faturası mevcut.");
        }

        java.math.BigDecimal totalRefund = refundAmount != null
                ? refundAmount
                : original.getTotalAmount();
        // Proportional VAT: preserve the original VAT/Total ratio
        java.math.BigDecimal vatRatio = (original.getVatAmount() != null && original.getTotalAmount() != null
                && original.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) > 0)
                ? original.getVatAmount().divide(original.getTotalAmount(), 4, java.math.RoundingMode.HALF_UP)
                : java.math.BigDecimal.ZERO;
        java.math.BigDecimal refundVat = totalRefund.multiply(vatRatio)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal refundSubtotal = totalRefund.subtract(refundVat);

        // New Invoice row — preserves the original's recipient + seller snapshot
        Invoice creditNote = Invoice.builder()
                .order(original.getOrder())
                .invoiceType(original.getInvoiceType())
                .status(InvoiceStatus.DRAFT)
                .recipientName(original.getRecipientName())
                .recipientTaxId(original.getRecipientTaxId())
                .recipientTaxOffice(original.getRecipientTaxOffice())
                .recipientAddress(original.getRecipientAddress())
                .recipientEmail(original.getRecipientEmail())
                .recipientPhone(original.getRecipientPhone())
                .recipientCity(original.getRecipientCity())
                .recipientDistrict(original.getRecipientDistrict())
                .recipientPostalCode(original.getRecipientPostalCode())
                .individual(original.isIndividual())
                .subtotal(refundSubtotal)
                .vatAmount(refundVat)
                .totalAmount(totalRefund)
                .providerName(original.getProviderName())
                .note("İade Faturası — orijinal: " + original.getInvoiceNumber())
                .creditNote(true)
                .creditedInvoice(original)
                .creditNoteReason(reason != null && !reason.isBlank() ? reason : "Cayma hakkı / iade")
                .build();
        creditNote = invoiceRepository.save(creditNote);

        logger.info("[Invoice] Credit note draft oluşturuldu: id={}, orijinal={}, tutar={}",
                creditNote.getId(), original.getInvoiceNumber(), totalRefund);

        // Send to the provider (UBL-TR contains ProfileID=IADE + BillingReference;
        // UblTrInvoiceBuilder automatically selects the credit note format)
        return sendToProvider(creditNote);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InvoiceDto> getInvoiceById(Long invoiceId) {
        return invoiceRepository.findById(invoiceId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InvoiceDto> getInvoiceByOrderId(Long orderId) {
        return invoiceRepository.findByOrderId(orderId).map(this::toDto);
    }

    /**
     * Invoice PDF — with a Caffeine cache we avoid hitting the provider repeatedly
     * (PDFs are immutable, so a 24h cache is safe). Even if the customer clicks the
     * "Download my invoice" button 10 times, Logo SOAP is called only once.
     *
     * <p>Cache key: invoiceId. APPROVED invoices are cached
     * (no caching for DRAFT/PENDING — they may change).</p>
     */
    @Override
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(
            value = "invoicePdf",
            key = "#invoiceId",
            condition = "#invoiceId != null",
            unless = "#result == null || #result.length == 0")
    public byte[] downloadInvoicePdf(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.INVOICE_NOT_FOUND, "ID: " + invoiceId));

        if (invoice.getProviderInvoiceId() == null) {
            throw new WarehouseManagementException(ErrorCode.INVOICE_PDF_NOT_AVAILABLE,
                    "Fatura henüz sağlayıcıya gönderilmemiş.");
        }

        InvoiceProvider activeProvider = getActiveProvider();
        if (activeProvider == null) {
            return new byte[0];
        }
        return activeProvider.downloadPdf(invoice.getProviderInvoiceId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InvoiceDto> getInvoices(InvoiceStatus status, InvoiceType invoiceType,
                                         String search, LocalDateTime from, LocalDateTime to,
                                         Pageable pageable) {
        LocalDateTime safeFrom = from != null ? from : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime safeTo = to != null ? to : LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        String safeSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        return invoiceRepository.findByFilters(status, invoiceType, safeSearch, safeFrom, safeTo, pageable)
                .map(this::toDto);
    }

    @Override
    @Transactional
    public int refreshPendingStatuses() {
        InvoiceProvider activeProvider = getActiveProvider();
        if (activeProvider == null) return 0;

        var pageable = org.springframework.data.domain.PageRequest.of(0, 50);
        List<Invoice> pending = invoiceRepository.findByStatusWithProviderId(
                InvoiceStatus.PENDING, pageable);
        if (pending.isEmpty()) return 0;

        int updated = 0;
        for (Invoice inv : pending) {
            try {
                InvoiceResult result = activeProvider.queryStatus(inv.getProviderInvoiceId());
                if (result == null || result.getStatus() == null) continue;
                if (result.getStatus() == InvoiceStatus.PENDING) continue;  // unchanged

                InvoiceStatus oldStatus = inv.getStatus();
                inv.setStatus(result.getStatus());
                if (result.getGibResponse() != null) inv.setGibResponse(result.getGibResponse());
                if (result.getPdfUrl() != null) inv.setPdfUrl(result.getPdfUrl());
                if (result.getStatus() == InvoiceStatus.APPROVED && inv.getIssuedAt() == null) {
                    inv.setIssuedAt(LocalDateTime.now());
                }
                invoiceRepository.save(inv);
                updated++;
                logger.info("Fatura statüsü güncellendi: {} → {} (invoiceId={})",
                        oldStatus, result.getStatus(), inv.getId());

                // On PENDING → APPROVED transition, email the customer
                if (oldStatus == InvoiceStatus.PENDING && result.getStatus() == InvoiceStatus.APPROVED) {
                    notifyCustomerInvoiceReady(inv);
                }
            } catch (Exception e) {
                logger.warn("Status polling hatası (invoiceId={}): {}", inv.getId(), e.getMessage());
            }
        }
        return updated;
    }

    // === Private helpers ===

    private InvoiceDto createAndSendInvoice(Order order, InvoiceType type, String note) {
        // Take recipient details from the order's billing address snapshot
        Map<String, Object> billing = order.getBillingAddressSnapshot();
        String recipientName = extractBillingField(billing, "firstName", "") + " " +
                extractBillingField(billing, "lastName", "");
        String recipientAddress = buildRecipientAddress(billing);

        // Corporate/individual detection: corporate if taxNumber or companyName is present
        String taxNumber = extractBillingField(billing, "taxNumber", "");
        String taxOffice = extractBillingField(billing, "taxOffice", "");
        String companyName = extractBillingField(billing, "companyName", "");
        String tcKimlikNo = extractBillingField(billing, "tcKimlikNo", "");
        boolean individual = companyName.isBlank() && taxNumber.isBlank();

        // ── Algorithmic VKN/TCKN validation ──
        // If we send an invalid ID to Logo, GİB rejects it. Caught early here.
        // For individuals, TCKN is optional (not mandatory for e-Arşiv); but if provided, it must be valid.
        // For corporate entities, VKN is mandatory and must be valid.
        if (individual && !tcKimlikNo.isBlank()
                && !com.warehouse.util.TurkishTaxIdValidator.isValidTckn(tcKimlikNo)) {
            throw new com.warehouse.exception.WarehouseManagementException(
                    com.warehouse.exception.ErrorCode.VALIDATION_ERROR,
                    "TCKN algoritmik olarak geçersiz: " + tcKimlikNo);
        }
        if (!individual && !com.warehouse.util.TurkishTaxIdValidator.isValidVkn(taxNumber)) {
            throw new com.warehouse.exception.WarehouseManagementException(
                    com.warehouse.exception.ErrorCode.VALIDATION_ERROR,
                    "VKN algoritmik olarak geçersiz: " + taxNumber);
        }

        InvoiceProvider activeProvider = getActiveProvider();
        String providerName = activeProvider != null ? activeProvider.getProviderName() : "NONE";

        // E-Fatura vs E-Arşiv selection:
        //   - If the caller passed an explicit type, honor it
        //   - Individual customer → E_ARSIV
        //   - Corporate customer: if registered as an e-Fatura taxpayer in GİB → E_FATURA,
        //     otherwise E_ARSIV is required (Logo rejects it if wrong)
        InvoiceType resolvedType;
        if (type != null) {
            resolvedType = type;
        } else if (individual) {
            resolvedType = InvoiceType.E_ARSIV;
        } else {
            boolean gibRegistered = activeProvider != null
                    && activeProvider.isGibRegistered(taxNumber);
            resolvedType = gibRegistered ? InvoiceType.E_FATURA : InvoiceType.E_ARSIV;
            if (!gibRegistered) {
                logger.info("Tüzel müşteri (VKN={}) GİB kayıtlı değil — E_ARSIV kesilecek",
                        taxNumber);
            }
        }

        Invoice invoice = Invoice.builder()
                .order(order)
                .invoiceType(resolvedType)
                .status(InvoiceStatus.DRAFT)
                .recipientName(individual ? recipientName.trim() : (companyName.isBlank() ? recipientName.trim() : companyName))
                .recipientTaxId(individual ? (tcKimlikNo.isBlank() ? null : tcKimlikNo) : taxNumber)
                .recipientTaxOffice(individual ? null : (taxOffice.isBlank() ? null : taxOffice))
                .recipientAddress(recipientAddress)
                .recipientEmail(order.getCustomer() != null ? order.getCustomer().getEmail() : null)
                .recipientPhone(extractBillingField(billing, "phone", null))
                .recipientCity(extractBillingField(billing, "city", null))
                .recipientDistrict(extractBillingField(billing, "district", null))
                .recipientPostalCode(extractBillingField(billing, "postalCode", null))
                .individual(individual)
                .subtotal(order.getSubtotal())
                .vatAmount(order.getVatTotal())
                .totalAmount(order.getGrandTotal())
                .providerName(providerName)
                .note(note)
                .build();

        invoice = invoiceRepository.save(invoice);
        logger.info("Fatura taslağı oluşturuldu: sipariş={}, tip={}, bireysel={}, provider={}",
                order.getOrderNumber(), resolvedType, individual, providerName);

        // If auto-generation is enabled, send to the provider
        boolean autoGenerate = "true".equalsIgnoreCase(settingService.getSetting("invoice_auto_generate"));
        if (autoGenerate) {
            return sendToProvider(invoice);
        }

        return toDto(invoice);
    }

    private InvoiceDto sendToProvider(Invoice invoice) {
        try {
            // 1) Assign invoice number BEFORE sending (GİB requires the seller to
            //    provide the number). Regeneration keeps the existing number.
            if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
                String number = invoiceNumberGenerator.next(invoice.getInvoiceType());
                invoice.setInvoiceNumber(number);
                logger.info("Fatura numarası atandı: {} (sipariş: {})",
                        number, invoice.getOrder().getOrderNumber());
            }

            invoice.setStatus(InvoiceStatus.PENDING);
            invoiceRepository.save(invoice);

            InvoiceProvider activeProvider = getActiveProvider();
            if (activeProvider == null) {
                invoice.setStatus(InvoiceStatus.ERROR);
                invoice.setErrorMessage("Aktif e-Fatura sağlayıcısı yok");
                invoiceRepository.save(invoice);
                return toDto(invoice);
            }

            // Order items (for the UBL-TR XML)
            Order order = invoice.getOrder();
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

            // 2) Build UBL-TR XML once and persist it BEFORE sending — even if
            //    Logo rejects, we still have the submitted payload for audit.
            try {
                Map<String, String> companyInfo = getCompanyInfoMap();
                String ublXml = UblTrInvoiceBuilder.build(invoice, order,
                        items != null ? items : List.of(), companyInfo);
                invoice.setXmlContent(ublXml);
                invoiceRepository.save(invoice);
            } catch (Exception xmlEx) {
                logger.warn("UBL XML üretilip kaydedilemedi (yine de provider'a iletilecek): {}", xmlEx.getMessage());
            }

            // Set the invoice number on the Order up front — so that even if the
            // provider fails, the customer can still see an "invoice being prepared" state.
            order.setInvoiceNumber(invoice.getInvoiceNumber());
            orderRepository.save(order);

            InvoiceResult result = activeProvider.createInvoice(invoice, order, items);

            if (result.isSuccess()) {
                if (result.getInvoiceNumber() != null && !result.getInvoiceNumber().isBlank()) {
                    invoice.setInvoiceNumber(result.getInvoiceNumber());
                    order.setInvoiceNumber(result.getInvoiceNumber());
                    orderRepository.save(order);
                }
                invoice.setProviderInvoiceId(result.getProviderInvoiceId());
                invoice.setStatus(result.getStatus());
                invoice.setGibResponse(result.getGibResponse());
                if (result.getPdfUrl() != null) invoice.setPdfUrl(result.getPdfUrl());
                invoice.setIssuedAt(LocalDateTime.now());
                invoice.setErrorMessage(null);

                logger.info("Fatura başarıyla oluşturuldu: {} (sipariş: {})",
                        invoice.getInvoiceNumber(), order.getOrderNumber());

                // "Your Invoice Is Ready" notification to the customer — only for APPROVED ones
                if (result.getStatus() == InvoiceStatus.APPROVED) {
                    notifyCustomerInvoiceReady(invoice);
                }
            } else {
                invoice.setStatus(InvoiceStatus.ERROR);
                invoice.setErrorMessage(result.getErrorMessage());
                logger.error("Fatura oluşturma hatası: sipariş={}, hata={}",
                        invoice.getOrder().getOrderNumber(), result.getErrorMessage());
            }

            invoiceRepository.save(invoice);
        } catch (Exception e) {
            logger.error("Fatura sağlayıcı hatası: invoiceId={}", invoice.getId(), e);
            invoice.setStatus(InvoiceStatus.ERROR);
            invoice.setErrorMessage("Sağlayıcı hatası: " + e.getMessage());
            invoiceRepository.save(invoice);
        }

        return toDto(invoice);
    }

    /** When the invoice becomes APPROVED, send a "Your Invoice Is Ready" email to the customer — errors do not block the flow. */
    private void notifyCustomerInvoiceReady(Invoice invoice) {
        try {
            Order order = invoice.getOrder();
            if (order == null || order.getCustomer() == null) return;
            String email = order.getCustomer().getEmail();
            if (email == null || email.isBlank()) return;

            String firstName = order.getCustomer().getFirstName();
            String total = new java.text.DecimalFormat("#,##0.00 'TL'",
                    new java.text.DecimalFormatSymbols(new java.util.Locale("tr", "TR")))
                    .format(invoice.getTotalAmount() != null ? invoice.getTotalAmount() : java.math.BigDecimal.ZERO);

            emailService.sendInvoiceReady(
                    email,
                    firstName,
                    order.getOrderNumber(),
                    invoice.getInvoiceNumber(),
                    invoice.getInvoiceType() != null ? invoice.getInvoiceType().name() : "E_ARSIV",
                    total);
        } catch (Exception e) {
            logger.warn("Fatura hazır bildirim mail'i gönderilemedi (invoiceId={}): {}",
                    invoice.getId(), e.getMessage());
        }
    }

    /** Seller company info — collected from site_settings for use inside the UBL-TR XML. */
    private Map<String, String> getCompanyInfoMap() {
        String[] keys = {
                "logo_company_vkn", "logo_company_title", "logo_company_tax_office",
                "logo_company_mersis_no", "logo_company_trade_registry",
                "logo_company_address", "logo_company_city", "logo_company_district",
                "logo_company_postal_code", "logo_company_country",
                "logo_company_email", "logo_company_phone", "logo_company_website",
                "logo_company_bank_name", "logo_company_bank_iban",
                "logo_customer_alias"
        };
        Map<String, String> map = new java.util.HashMap<>();
        for (String k : keys) map.put(k, settingService.getSetting(k));
        return map;
    }

    private InvoiceDto toDto(Invoice invoice) {
        return InvoiceDto.builder()
                .id(invoice.getId())
                .orderId(invoice.getOrder().getId())
                .orderNumber(invoice.getOrder().getOrderNumber())
                .invoiceNumber(invoice.getInvoiceNumber())
                .invoiceType(invoice.getInvoiceType())
                .status(invoice.getStatus())
                .recipientName(invoice.getRecipientName())
                .recipientTaxId(invoice.getRecipientTaxId())
                .recipientTaxOffice(invoice.getRecipientTaxOffice())
                .recipientAddress(invoice.getRecipientAddress())
                .subtotal(invoice.getSubtotal())
                .vatAmount(invoice.getVatAmount())
                .totalAmount(invoice.getTotalAmount())
                .providerName(invoice.getProviderName())
                .providerInvoiceId(invoice.getProviderInvoiceId())
                .errorMessage(invoice.getErrorMessage())
                .pdfUrl(invoice.getPdfUrl())
                .hasPdf(invoice.getProviderInvoiceId() != null)
                .note(invoice.getNote())
                .issuedAt(invoice.getIssuedAt())
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt())
                .build();
    }

    @SuppressWarnings("unchecked")
    private String extractBillingField(Map<String, Object> billing, String key, String defaultValue) {
        if (billing == null) return defaultValue;
        Object val = billing.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private String buildRecipientAddress(Map<String, Object> billing) {
        if (billing == null) return "";
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, billing, "addressLine");
        appendIfPresent(sb, billing, "district");
        appendIfPresent(sb, billing, "city");
        appendIfPresent(sb, billing, "postalCode");
        return sb.toString().trim();
    }

    private void appendIfPresent(StringBuilder sb, Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val != null && !val.toString().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(val);
        }
    }
}
