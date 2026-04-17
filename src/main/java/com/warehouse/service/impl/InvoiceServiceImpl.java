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
import com.warehouse.service.invoice.InvoiceProvider;
import com.warehouse.service.invoice.InvoiceResult;
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

    public InvoiceServiceImpl(InvoiceRepository invoiceRepository,
                               OrderRepository orderRepository,
                               OrderItemRepository orderItemRepository,
                               List<InvoiceProvider> providers,
                               SiteSettingService settingService) {
        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.providers = providers;
        this.settingService = settingService;
    }

    /**
     * Aktif (isEnabled()==true) olan ilk provider'ı döner.
     * Hiçbiri aktif değilse MOCK provider'ı fallback olarak kullanılır.
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
        // Mevcut fatura var mı kontrol et
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
    @Transactional(readOnly = true)
    public Optional<InvoiceDto> getInvoiceById(Long invoiceId) {
        return invoiceRepository.findById(invoiceId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InvoiceDto> getInvoiceByOrderId(Long orderId) {
        return invoiceRepository.findByOrderId(orderId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
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

    // === Private helpers ===

    private InvoiceDto createAndSendInvoice(Order order, InvoiceType type, String note) {
        // Alıcı bilgilerini sipariş billing address snapshot'ından al
        Map<String, Object> billing = order.getBillingAddressSnapshot();
        String recipientName = extractBillingField(billing, "firstName", "") + " " +
                extractBillingField(billing, "lastName", "");
        String recipientAddress = buildRecipientAddress(billing);

        // Kurumsal/bireysel tespiti: taxNumber veya companyName varsa kurumsal
        String taxNumber = extractBillingField(billing, "taxNumber", "");
        String taxOffice = extractBillingField(billing, "taxOffice", "");
        String companyName = extractBillingField(billing, "companyName", "");
        String tcKimlikNo = extractBillingField(billing, "tcKimlikNo", "");
        boolean individual = companyName.isBlank() && taxNumber.isBlank();

        // E-Fatura mı e-Arşiv mi? Tüzel kişiler için E_FATURA, bireyseller için E_ARSIV
        InvoiceType resolvedType = (type != null) ? type : (individual ? InvoiceType.E_ARSIV : InvoiceType.E_FATURA);

        InvoiceProvider activeProvider = getActiveProvider();
        String providerName = activeProvider != null ? activeProvider.getProviderName() : "NONE";

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

        // Otomatik oluşturma aktif ise sağlayıcıya gönder
        boolean autoGenerate = "true".equalsIgnoreCase(settingService.getSetting("invoice_auto_generate"));
        if (autoGenerate) {
            return sendToProvider(invoice);
        }

        return toDto(invoice);
    }

    private InvoiceDto sendToProvider(Invoice invoice) {
        try {
            invoice.setStatus(InvoiceStatus.PENDING);
            invoiceRepository.save(invoice);

            InvoiceProvider activeProvider = getActiveProvider();
            if (activeProvider == null) {
                invoice.setStatus(InvoiceStatus.ERROR);
                invoice.setErrorMessage("Aktif e-Fatura sağlayıcısı yok");
                invoiceRepository.save(invoice);
                return toDto(invoice);
            }

            // Sipariş kalemleri (UBL-TR XML için)
            Order order = invoice.getOrder();
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

            InvoiceResult result = activeProvider.createInvoice(invoice, order, items);

            if (result.isSuccess()) {
                invoice.setInvoiceNumber(result.getInvoiceNumber());
                invoice.setProviderInvoiceId(result.getProviderInvoiceId());
                invoice.setStatus(result.getStatus());
                invoice.setGibResponse(result.getGibResponse());
                invoice.setPdfUrl(result.getPdfUrl());
                invoice.setIssuedAt(LocalDateTime.now());
                invoice.setErrorMessage(null);

                // Order tablosundaki invoice bilgilerini de güncelle
                order.setInvoiceNumber(result.getInvoiceNumber());
                orderRepository.save(order);

                logger.info("Fatura başarıyla oluşturuldu: {} (sipariş: {})",
                        result.getInvoiceNumber(), order.getOrderNumber());
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
