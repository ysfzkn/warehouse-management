package com.warehouse.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.dto.AuditMetadata;
import com.warehouse.dto.DeliveryReceiptDto;
import com.warehouse.entity.DeliveryReceipt;
import com.warehouse.entity.DeliveryReceiptAttachment;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.StockTransferItem;
import com.warehouse.enums.AuditAction;
import com.warehouse.enums.DeliveryReceiptKind;
import com.warehouse.enums.DeliveryReceiptStatus;
import com.warehouse.enums.DomainEntityType;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.DeliveryReceiptAttachmentRepository;
import com.warehouse.repository.DeliveryReceiptRepository;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.security.SignedUrlService;
import com.warehouse.security.UploadValidator;
import com.warehouse.service.AuditService;
import com.warehouse.service.DeliveryReceiptService;
import com.warehouse.service.PhotoStorageService;
import com.warehouse.service.SiteSettingService;
import com.warehouse.service.receipt.ReceiptPdfRenderer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The receipt lifecycle: snapshot on issue, render, confirm, archive the signed page.
 */
@Service
@Transactional
public class DeliveryReceiptServiceImpl implements DeliveryReceiptService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryReceiptServiceImpl.class);

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", new Locale("tr", "TR"));
    private static final String STORAGE_PREFIX = "delivery-receipts";
    private static final String SIGNED_RESOURCE = "receipt-attachment";
    /** Phone photographs of an A4 page are routinely 6-10 MB; scans rarely exceed this. */
    private static final long MAX_ATTACHMENT_BYTES = 15L * 1024 * 1024;
    private static final int MAX_ATTACHMENTS = 10;
    /** Blank rows printed under the items so the form can be completed by hand. */
    private static final int MIN_TABLE_ROWS = 8;
    private static final String PACKAGED_LETTERHEAD = "/receipt/company-logo.jpg";

    /** {@link #packagedLetterheadDataUri()} için tembel önbellek; "" = dosya yok. */
    private volatile String packagedLetterhead;

    private final DeliveryReceiptRepository receiptRepository;
    private final DeliveryReceiptAttachmentRepository attachmentRepository;
    private final StockTransferRepository transferRepository;
    private final PhotoStorageService photoStorageService;
    private final SiteSettingService siteSettingService;
    private final SpringTemplateEngine templateEngine;
    private final ReceiptPdfRenderer pdfRenderer;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeliveryReceiptServiceImpl(DeliveryReceiptRepository receiptRepository,
                                      DeliveryReceiptAttachmentRepository attachmentRepository,
                                      StockTransferRepository transferRepository,
                                      PhotoStorageService photoStorageService,
                                      SiteSettingService siteSettingService,
                                      SpringTemplateEngine templateEngine,
                                      ReceiptPdfRenderer pdfRenderer,
                                      AuditService auditService) {
        this.receiptRepository = receiptRepository;
        this.attachmentRepository = attachmentRepository;
        this.transferRepository = transferRepository;
        this.photoStorageService = photoStorageService;
        this.siteSettingService = siteSettingService;
        this.templateEngine = templateEngine;
        this.pdfRenderer = pdfRenderer;
        this.auditService = auditService;
    }

    // ─────────────────────────────── Issue ───────────────────────────────

    @Override
    public DeliveryReceiptDto issue(Long transferId, String username) {
        return issue(transferId, username, null);
    }

    @Override
    public DeliveryReceiptDto issue(Long transferId, String username, DeliveryReceiptKind kindWhenNew) {
        StockTransfer transfer = loadTransfer(transferId);
        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "İptal edilmiş sevkiyat için makbuz düzenlenemez.");
        }

        DeliveryReceipt receipt = receiptRepository.findByTransferId(transferId).orElse(null);
        boolean reissue = receipt != null;
        if (receipt == null) {
            receipt = new DeliveryReceipt();
            receipt.setTransfer(transfer);
            receipt.setStatus(DeliveryReceiptStatus.ISSUED);
            receipt.setRevision(1);
            receipt.setIssuedAt(LocalDateTime.now());
            receipt.setIssuedBy(username);
            // Fixed at first issue and never revisited. A shipment that went out on a depot
            // exit receipt keeps printing as one even after the carrier is filled in — the
            // page that was signed is a depot exit receipt, and a reprint that changed its
            // title and number series would no longer match the paper in the folder.
            //
            // The caller normally says which paper this is. The fallback is for a receipt
            // issued later from the panel on a shipment that already went out as a depot
            // exit: carrierPending is the mark left by that flow, and the two parties are
            // only a hint since both are optional on the form.
            receipt.setKind(kindWhenNew != null ? kindWhenNew : inferKind(transfer));
        } else {
            // The number stays; only the print count moves. Two different numbers for one
            // shipment would make the signed page impossible to match back.
            receipt.setRevision(receipt.getRevision() == null ? 2 : receipt.getRevision() + 1);
        }

        applySnapshot(receipt, transfer);

        boolean needsNumber = receipt.getReceiptNo() == null;
        if (needsNumber) {
            // The number is derived from the primary key — unique without a second
            // sequence to keep in step, and short enough to read out over the phone. But
            // the id only exists after the insert, and the column is NOT NULL, so the row
            // goes in with a placeholder that is replaced before the transaction commits.
            // Nothing outside this method ever observes it.
            receipt.setReceiptNo("TMP-" + java.util.UUID.randomUUID().toString()
                    .replace("-", "").substring(0, 20));
        }
        receipt = receiptRepository.saveAndFlush(receipt);

        if (needsNumber) {
            // Separate series per kind so a number read out over the phone says which paper
            // it is. Both derive from the same primary key, so they can never collide.
            String prefix = receipt.getKind() == DeliveryReceiptKind.SERVICE_HANDOVER ? "DC" : "TM";
            receipt.setReceiptNo(String.format("%s-%d-%06d",
                    prefix, receipt.getIssuedAt().getYear(), receipt.getId()));
            receipt = receiptRepository.saveAndFlush(receipt);
        }

        String label = receipt.getKind() == DeliveryReceiptKind.SERVICE_HANDOVER
                ? "Depo çıkış makbuzu" : "Teslimat makbuzu";
        auditService.log(reissue ? AuditAction.RECEIPT_REISSUE : AuditAction.RECEIPT_ISSUE,
                DomainEntityType.StockTransfer.name(), transferId, username,
                label + (reissue ? " yeniden basıldı" : " düzenlendi")
                        + " (" + receipt.getReceiptNo() + ", " + receipt.getRevision() + ". basım)",
                metadata(transfer));

        return toDto(receipt);
    }

    /** Which paper a shipment would print as, when the caller did not say. */
    private DeliveryReceiptKind inferKind(StockTransfer transfer) {
        boolean depotExit = transfer.isCarrierPending()
                || transfer.getHandoverToName() != null
                || transfer.getHandedOverBy() != null;
        return depotExit ? DeliveryReceiptKind.SERVICE_HANDOVER : DeliveryReceiptKind.DELIVERY;
    }

    /**
     * Copies the shipment onto the receipt. Called on every issue and re-issue, never
     * anywhere else — once the paper is printed the receipt must stop tracking the
     * transfer, or the signed page and the record drift apart.
     */
    private void applySnapshot(DeliveryReceipt receipt, StockTransfer transfer) {
        receipt.setCompanyName(setting("company_name", "invoice_company_name"));
        receipt.setCompanyAddress(setting("company_address", "invoice_company_address"));
        receipt.setCompanyPhone(setting("company_phone", "contact_phone"));

        receipt.setSourceWarehouseName(
                transfer.getSourceWarehouse() != null ? transfer.getSourceWarehouse().getName() : null);

        if (transfer.getTransferType() == TransferType.CUSTOMER_DELIVERY) {
            receipt.setCustomerFullName(transfer.getCustomerFullName());
            receipt.setCustomerPhone(transfer.getCustomerPhone());
            receipt.setCustomerAddress(transfer.getCustomerAddress());
        } else {
            // A warehouse-to-warehouse move still gets a receipt — the destination
            // warehouse simply takes the recipient slot on the form.
            receipt.setCustomerFullName(transfer.getDestinationWarehouse() != null
                    ? transfer.getDestinationWarehouse().getName() : null);
            receipt.setCustomerPhone(null);
            receipt.setCustomerAddress(transfer.getDestinationWarehouse() != null
                    ? transfer.getDestinationWarehouse().getLocation() : null);
        }

        receipt.setOrderNumber(transfer.getOrderNumber());
        receipt.setHandoverToName(transfer.getHandoverToName());
        receipt.setHandoverToPhone(transfer.getHandoverToPhone());
        receipt.setHandedOverByName(transfer.getHandedOverBy());
        receipt.setDriverName(transfer.getDriverName());
        receipt.setDriverPhone(transfer.getDriverPhone());
        receipt.setVehiclePlate(transfer.getVehiclePlate());
        receipt.setTransferDate(transfer.getTransferDate());
        receipt.setNotes(transfer.getNotes());
        receipt.setItemsJson(serialiseItems(transfer));

        // The driver on the paperwork is the obvious default for "handed over by"; the
        // panel can still overwrite it when someone else made the drop. A depot exit has no
        // driver yet, so the warehouse hand who signed the page stands in.
        if (receipt.getDeliveredByName() == null) {
            receipt.setDeliveredByName(transfer.getDriverName() != null
                    ? transfer.getDriverName()
                    : transfer.getHandedOverBy());
        }

        // Receipts are routinely issued after the fact — for a delivery that went out
        // last month and now needs paperwork. Without this the confirmation form would
        // offer today's date, and a document whose whole purpose is to record when the
        // goods changed hands would quietly carry the wrong one. The shipment already
        // knows when it completed.
        if (receipt.getDeliveredAt() == null && transfer.getStatus() == TransferStatus.COMPLETED) {
            receipt.setDeliveredAt(transfer.getCompletedDate());
        }
    }

    private String serialiseItems(StockTransfer transfer) {
        List<DeliveryReceiptDto.ItemLine> lines = new ArrayList<>();
        List<StockTransferItem> items = transfer.getItems();
        if (items != null && !items.isEmpty()) {
            for (StockTransferItem item : items) {
                lines.add(DeliveryReceiptDto.ItemLine.builder()
                        .sku(item.getProduct() != null ? item.getProduct().getSku() : null)
                        .name(item.getProduct() != null ? item.getProduct().getName() : null)
                        .quantity(item.getQuantity())
                        .build());
            }
        } else if (transfer.getProduct() != null) {
            // Transfers created before the multi-item model still carry a single product.
            lines.add(DeliveryReceiptDto.ItemLine.builder()
                    .sku(transfer.getProduct().getSku())
                    .name(transfer.getProduct().getName())
                    .quantity(transfer.getQuantity())
                    .build());
        }
        try {
            return objectMapper.writeValueAsString(lines);
        } catch (Exception e) {
            throw new IllegalStateException("Makbuz kalemleri kaydedilemedi", e);
        }
    }

    private List<DeliveryReceiptDto.ItemLine> deserialiseItems(DeliveryReceipt receipt) {
        if (receipt.getItemsJson() == null || receipt.getItemsJson().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(receipt.getItemsJson(), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Makbuz {} kalemleri okunamadı: {}", receipt.getReceiptNo(), e.getMessage());
            return List.of();
        }
    }

    // ─────────────────────────────── Read ────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public DeliveryReceiptDto findByTransfer(Long transferId) {
        return receiptRepository.findByTransferId(transferId).map(this::toDto).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, DeliveryReceiptDto> findByTransferIds(List<Long> transferIds) {
        if (transferIds == null || transferIds.isEmpty()) return Map.of();
        Map<Long, DeliveryReceiptDto> result = new LinkedHashMap<>();
        for (DeliveryReceipt receipt : receiptRepository.findByTransferIdIn(transferIds)) {
            result.put(receipt.getTransfer().getId(), toDto(receipt));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DeliveryReceiptDto> search(DeliveryReceiptStatus status,
                                           Boolean hasSignedCopy,
                                           LocalDateTime from,
                                           LocalDateTime to,
                                           String search,
                                           Pageable pageable) {
        return receiptRepository.findAll(
                filter(status, hasSignedCopy, from, to, search), pageable).map(this::toDto);
    }

    /**
     * Builds only the predicates the caller actually asked for.
     *
     * <p>The obvious alternative — one JPQL statement with a {@code (:param IS NULL OR ...)}
     * branch per filter — is what this replaced. It ran on H2 and failed on PostgreSQL with
     * {@code 42P18 could not determine data type of parameter}: a bare parameter compared
     * only against NULL has no type for the server to infer, so it refuses the statement
     * before looking at a single row. The receipts screen came up empty in production while
     * every test passed.</p>
     *
     * <p>A specification has no such hole. An absent filter contributes no predicate and
     * therefore no parameter, and the statement that reaches the database carries only the
     * conditions in use.</p>
     */
    private static Specification<DeliveryReceipt> filter(DeliveryReceiptStatus status,
                                                         Boolean hasSignedCopy,
                                                         LocalDateTime from,
                                                         LocalDateTime to,
                                                         String search) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("issuedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("issuedAt"), to));
            }

            // Arama, sütunları tek tek taramak yerine ASCII'ye katlanmış searchText üzerinden
            // yapılıyor. Türkçe'de küçültme geri döndürülemez olduğu için doğrudan
            // karşılaştırma güvenilir değil: "I" hem "ı" hem "i"nin büyüğü, "İ" küçülünce
            // birleşik bir diziye dönüşüyor — "IŞIK" hiçbir zaman "Işık"ı bulamazdı.
            String normalized = com.warehouse.util.TurkishText.normalize(search);
            String digits = search == null ? "" : search.replaceAll("\\D", "");
            List<jakarta.persistence.criteria.Predicate> anyOf = new ArrayList<>();

            if (!normalized.isEmpty()) {
                // Kelimeler ayrı ayrı aranıp VE'leniyor, tek parça olarak değil. "Yıldız
                // Nakliyat" yazan biri "Yıldız Kargo ve Nakliyat"ı bulabilmeli; bitişik
                // arama araya giren kelime yüzünden bulamazdı. Sıra da önemsizleşiyor.
                List<jakarta.persistence.criteria.Predicate> allTokens = new ArrayList<>();
                for (String token : normalized.split(" ")) {
                    allTokens.add(cb.like(root.get("searchText"), "%" + token + "%"));
                }
                anyOf.add(cb.and(allTokens.toArray(new jakarta.persistence.criteria.Predicate[0])));
            }

            if (digits.length() >= 7) {
                // Telefon her biçimde yazılabiliyor: "0553 999 33 03", "05539993303",
                // "+90 553 999 33 03". Kayıtta da bazen başında sıfırla bazen ülke koduyla
                // duruyor. Son on hane hepsinde ortak olan parça, o yüzden karşılaştırma
                // onun üzerinden yapılıyor. (On hane Türkiye'ye özgü, uygulama da öyle.)
                String tail = digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
                anyOf.add(cb.like(root.get("searchText"), "%" + tail + "%"));
            }

            if (!anyOf.isEmpty()) {
                predicates.add(anyOf.size() == 1 ? anyOf.get(0)
                        : cb.or(anyOf.toArray(new jakarta.persistence.criteria.Predicate[0])));
            }

            if (hasSignedCopy != null) {
                // EXISTS rather than a count: the question is only whether the receipt has
                // any attachment at all, and the server can stop at the first row.
                jakarta.persistence.criteria.Subquery<Integer> signed = query.subquery(Integer.class);
                var attachment = signed.from(DeliveryReceiptAttachment.class);
                signed.select(cb.literal(1))
                      .where(cb.equal(attachment.get("receipt"), root));
                predicates.add(hasSignedCopy ? cb.exists(signed) : cb.not(cb.exists(signed)));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", receiptRepository.count());
        stats.put("issued", receiptRepository.countByStatus(DeliveryReceiptStatus.ISSUED));
        stats.put("delivered", receiptRepository.countByStatus(DeliveryReceiptStatus.DELIVERED));
        stats.put("awaitingSignedCopy", receiptRepository.countAwaitingSignedCopy());
        return stats;
    }

    // ───────────────────────────── Confirm ───────────────────────────────

    @Override
    public DeliveryReceiptDto confirmDelivery(Long transferId,
                                              String deliveredByName,
                                              String receivedByName,
                                              LocalDateTime deliveredAt,
                                              String note,
                                              String username) {
        DeliveryReceipt receipt = requireReceipt(transferId);
        if (receivedByName == null || receivedByName.isBlank()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Teslim alan kişinin adı soyadı zorunludur.");
        }
        LocalDateTime when = deliveredAt != null ? deliveredAt : LocalDateTime.now();
        if (when.isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Teslim tarihi ileri bir tarih olamaz.");
        }

        receipt.setReceivedByName(receivedByName.trim());
        if (deliveredByName != null && !deliveredByName.isBlank()) {
            receipt.setDeliveredByName(deliveredByName.trim());
        }
        receipt.setDeliveredAt(when);
        receipt.setReceivedByNote(note);
        receipt.setConfirmedAt(LocalDateTime.now());
        receipt.setConfirmedBy(username);
        receipt.setStatus(DeliveryReceiptStatus.DELIVERED);
        receipt = receiptRepository.save(receipt);

        auditService.log(AuditAction.RECEIPT_DELIVERY_CONFIRM,
                DomainEntityType.StockTransfer.name(), transferId, username,
                "Teslimat onaylandı — teslim alan: " + receipt.getReceivedByName()
                        + " (" + receipt.getReceiptNo() + ")",
                metadata(receipt.getTransfer()));

        return toDto(receipt);
    }

    // ───────────────────────────── Render ────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public String renderHtml(Long transferId, boolean showPrintBar) {
        return buildHtml(requireReceipt(transferId), showPrintBar);
    }

    @Override
    public byte[] renderPdf(Long transferId, String username) {
        DeliveryReceipt receipt = requireReceipt(transferId);
        byte[] pdf = pdfRenderer.render(buildHtml(receipt, false));
        auditService.log(AuditAction.RECEIPT_DOWNLOAD,
                DomainEntityType.StockTransfer.name(), transferId, username,
                "Makbuz PDF indirildi (" + receipt.getReceiptNo() + ")",
                metadata(receipt.getTransfer()));
        return pdf;
    }

    @Override
    public byte[] renderBulkPdf(List<Long> transferIds, String username) {
        if (transferIds == null || transferIds.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "En az bir sevkiyat seçmelisiniz.");
        }
        List<DeliveryReceipt> receipts = receiptRepository.findByTransferIdIn(transferIds);
        if (receipts.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Seçilen sevkiyatların hiçbirinde makbuz düzenlenmemiş.");
        }
        // Keep the order the caller asked for rather than whatever the database returned,
        // so a printed batch matches the order of the rows on screen.
        Map<Long, DeliveryReceipt> byTransfer = receipts.stream()
                .collect(Collectors.toMap(r -> r.getTransfer().getId(), r -> r, (a, b) -> a));

        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        merger.setDestinationStream(out);
        int merged = 0;
        for (Long transferId : transferIds) {
            DeliveryReceipt receipt = byTransfer.get(transferId);
            if (receipt == null) continue;
            try {
                merger.addSource(new ByteArrayInputStream(pdfRenderer.render(buildHtml(receipt, false))));
                merged++;
            } catch (Exception e) {
                // One bad receipt must not lose the other forty in the batch.
                log.error("Toplu PDF: makbuz {} eklenemedi: {}", receipt.getReceiptNo(), e.getMessage());
            }
        }
        if (merged == 0) {
            throw new WarehouseManagementException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Makbuzlar oluşturulamadı.");
        }
        try {
            merger.mergeDocuments(org.apache.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly());
        } catch (Exception e) {
            throw new IllegalStateException("Makbuzlar birleştirilemedi: " + e.getMessage(), e);
        }
        auditService.log(AuditAction.RECEIPT_DOWNLOAD,
                DomainEntityType.StockTransfer.name(), null, username,
                merged + " makbuz toplu PDF olarak indirildi");
        return out.toByteArray();
    }

    private String buildHtml(DeliveryReceipt receipt, boolean showPrintBar) {
        List<DeliveryReceiptDto.ItemLine> items = deserialiseItems(receipt);
        int total = items.stream()
                .mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum();

        Context context = new Context(new Locale("tr", "TR"));
        context.setVariable("receipt", receipt);
        context.setVariable("items", items);
        context.setVariable("totalQuantity", total);
        context.setVariable("uniqueProductCount", items.size());
        context.setVariable("fillerRows",
                Collections.nCopies(Math.max(0, minTableRows(receipt) - items.size()), ""));
        context.setVariable("issuedAtText", format(receipt.getIssuedAt()));
        context.setVariable("transferDateText", format(receipt.getTransferDate()));
        context.setVariable("deliveredAtText", format(receipt.getDeliveredAt()));
        context.setVariable("logoDataUri", logoDataUri());
        context.setVariable("showPrintBar", showPrintBar);
        // A customer delivery prints twice — the driver leaves one copy and brings the signed
        // one back. A depot exit prints once: it is signed on the spot by whoever collected
        // the goods and stays with us, and there is no second party yet to leave a copy with.
        boolean handover = receipt.getKind() == DeliveryReceiptKind.SERVICE_HANDOVER;
        context.setVariable("handover", handover);

        // Live, not snapshotted, and deliberately so. Everything else on this page is frozen
        // as it was signed; this is an annotation on the reprint. A receipt for goods that
        // came back would otherwise keep printing as clean proof of a delivery that failed.
        StockTransfer transfer = receipt.getTransfer();
        int returned = transfer != null && transfer.getReturnedQuantity() != null
                ? transfer.getReturnedQuantity() : 0;
        context.setVariable("returnedQuantity", returned);
        context.setVariable("fullyReturned", returned > 0 && transfer != null
                && transfer.getQuantity() != null && returned >= transfer.getQuantity());
        context.setVariable("copies", handover
                ? List.of("TEK NÜSHA")
                : List.of("FİRMA NÜSHASI", "MÜŞTERİ NÜSHASI"));
        return templateEngine.process("receipt/delivery-receipt", context);
    }

    /**
     * The logo is inlined rather than linked. A {@code <img src="https://...">} would make
     * every PDF render an outbound HTTP request from the server, which is both a failure
     * mode (slow or unreachable host stalls the download) and an SSRF-shaped hole.
     *
     * <p>Kaynak sırası: {@code receipt_logo} ayarı, yoksa uygulamayla birlikte paketlenen
     * antet. Makbuz bilerek {@code site_logo}'yu okumuyor — o ayar vitrinin logosu ve
     * vitrin tasarımı değiştiğinde evrakın anteti kendiliğinden değişmemeli. Antetli
     * kâğıt paketin içinde geldiği için makbuz, hiçbir ayar girilmemiş bir kurulumda da
     * doğru logoyla basılıyor.</p>
     */
    private String logoDataUri() {
        String path = siteSettingService.getSetting("receipt_logo");
        if (path == null || path.isBlank()) {
            return packagedLetterheadDataUri();
        }
        try (InputStream in = photoStorageService.openPhotoStream(path)) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0 || bytes.length > 2 * 1024 * 1024) {
                return packagedLetterheadDataUri();
            }
            // The media type comes from the bytes, not the file name. Logos uploaded
            // before content validation existed are commonly stored under a .png path
            // with JPEG content — the browser sniffs its way through that, but a
            // data:image/png URI carrying JPEG bytes renders as nothing in the PDF, and
            // the failure is silent: a receipt with no letterhead.
            UploadValidator.ImageType type = UploadValidator.detectImageType(bytes);
            if (type == null) {
                log.warn("Makbuz logosu tanınmayan formatta ({}), pakete düşülüyor.", path);
                return packagedLetterheadDataUri();
            }
            // PDFBox can only embed JPEG and PNG. A WebP logo renders perfectly on the
            // site and then leaves the receipt's letterhead blank — again with no error.
            // ImageIO reads WebP through the twelvemonkeys plugin, so re-encode instead
            // of dropping the logo.
            if (type != UploadValidator.ImageType.JPEG && type != UploadValidator.ImageType.PNG) {
                bytes = transcodeToPng(bytes, path);
                if (bytes == null) return packagedLetterheadDataUri();
                type = UploadValidator.ImageType.PNG;
            }
            return "data:" + type.contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            // A missing logo must not stop a delivery: the header falls back to the
            // packaged letterhead, and failing that to the company name in text.
            log.warn("Makbuz logosu okunamadı ({}): {}", path, e.getMessage());
            return packagedLetterheadDataUri();
        }
    }

    /**
     * Uygulamayla birlikte gelen antet ({@code classpath:receipt/company-logo.png}).
     *
     * <p>Her makbuzda diskten okunup base64'e çevrilmemesi için ilk okumada saklanıyor;
     * dosya jar'ın içinde, çalışırken değişmesi mümkün değil. Dosya hiç yoksa {@code null}
     * dönüyor ve şablon firma adını yazıya döküyor — bu yol makbuz basımını durdurmamalı.</p>
     */
    private String packagedLetterheadDataUri() {
        String cached = packagedLetterhead;
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        String value = "";
        try (InputStream in = getClass().getResourceAsStream(PACKAGED_LETTERHEAD)) {
            if (in == null) {
                log.warn("Paketlenmiş makbuz anteti bulunamadı: {}", PACKAGED_LETTERHEAD);
            } else {
                byte[] bytes = in.readAllBytes();
                // Biçim dosya adından değil baytlardan okunuyor: PDF'e yanlış medya tipiyle
                // gömülen bir görsel hata vermeden, sessizce bomboş basılır.
                UploadValidator.ImageType type = UploadValidator.detectImageType(bytes);
                if (type == null) {
                    log.warn("Paketlenmiş makbuz anteti tanınmayan formatta: {}",
                            PACKAGED_LETTERHEAD);
                } else {
                    value = "data:" + type.contentType + ";base64,"
                            + Base64.getEncoder().encodeToString(bytes);
                }
            }
        } catch (Exception e) {
            log.warn("Paketlenmiş makbuz anteti okunamadı: {}", e.getMessage());
        }
        packagedLetterhead = value;
        return value.isEmpty() ? null : value;
    }

    /**
     * How many rows the items table is padded out to.
     *
     * <p>The blank rows are there so the form can be completed by hand, and eight of them
     * fill an A4 page to the millimetre — which means anything else added to the sheet pushes
     * the closing paragraph onto a second, near-empty page. It was twelve until the letterhead
     * became the full company card, which is roughly twice as tall as a plain wordmark and
     * costs four of them; a shorter logo would win them back. Two things add to the sheet, and
     * each costs two filler rows rather than a page: the return notice, and the depot exit's
     * hand-filled plate / TC line under the receiving party. The figures are empirical:
     * openhtmltopdf's box heights do not match the browser's, so change them only against a
     * rendered PDF, never by arithmetic — and check the single-item receipt, which is the
     * tallest one because it has the most filler rows.</p>
     */
    private int minTableRows(DeliveryReceipt receipt) {
        StockTransfer transfer = receipt.getTransfer();
        boolean hasReturn = transfer != null
                && transfer.getReturnedQuantity() != null
                && transfer.getReturnedQuantity() > 0;
        int rows = MIN_TABLE_ROWS;
        if (hasReturn) {
            rows -= 2;
        }
        if (receipt.getKind() == DeliveryReceiptKind.SERVICE_HANDOVER) {
            rows -= 2;
        }
        return rows;
    }

    /** Re-encodes an image PDFBox cannot embed (WebP, GIF) as PNG. Null if undecodable. */
    private byte[] transcodeToPng(byte[] bytes, String path) {
        try {
            java.awt.image.BufferedImage image =
                    javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (image == null) {
                log.warn("Makbuz logosu çözülemedi ({}), atlanıyor.", path);
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!javax.imageio.ImageIO.write(image, "png", out)) {
                log.warn("Makbuz logosu PNG'ye çevrilemedi ({}), atlanıyor.", path);
                return null;
            }
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("Makbuz logosu dönüştürülemedi ({}): {}", path, e.getMessage());
            return null;
        }
    }

    // ─────────────────────────── Attachments ─────────────────────────────

    @Override
    public DeliveryReceiptDto addAttachment(Long transferId, MultipartFile file, String username) {
        DeliveryReceipt receipt = requireReceipt(transferId);
        if (attachmentRepository.countByReceiptId(receipt.getId()) >= MAX_ATTACHMENTS) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Bir makbuza en fazla " + MAX_ATTACHMENTS + " dosya eklenebilir.");
        }

        UploadValidator.ScannedDocument scan;
        try {
            scan = UploadValidator.validateScan(file, MAX_ATTACHMENT_BYTES);
        } catch (UploadValidator.InvalidUploadException e) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }

        String storageKey;
        try (InputStream in = file.getInputStream()) {
            storageKey = photoStorageService.storeDocument(
                    STORAGE_PREFIX + "/" + receipt.getId(),
                    "imzali-nusha." + scan.extension(),
                    scan.contentType(),
                    in);
        } catch (Exception e) {
            throw new WarehouseManagementException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Dosya kaydedilemedi: " + e.getMessage());
        }

        DeliveryReceiptAttachment attachment = new DeliveryReceiptAttachment();
        attachment.setStorageKey(storageKey);
        // The uploader's file name is display-only; the stored name comes from the bytes.
        attachment.setFileName(trim(file.getOriginalFilename(), 255));
        attachment.setContentType(scan.contentType());
        attachment.setSizeBytes(file.getSize());
        attachment.setUploadedAt(LocalDateTime.now());
        attachment.setUploadedBy(username);
        receipt.addAttachment(attachment);
        attachmentRepository.save(attachment);

        auditService.log(AuditAction.RECEIPT_ATTACHMENT_UPLOAD,
                DomainEntityType.StockTransfer.name(), transferId, username,
                "İmzalı makbuz nüshası yüklendi (" + receipt.getReceiptNo() + ")",
                metadata(receipt.getTransfer()));

        return toDto(receiptRepository.save(receipt));
    }

    @Override
    public void deleteAttachment(Long attachmentId, String username) {
        DeliveryReceiptAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                        "Dosya bulunamadı."));
        DeliveryReceipt receipt = attachment.getReceipt();
        try {
            photoStorageService.deleteDocument(attachment.getStorageKey());
        } catch (Exception e) {
            // The row is what the panel reads; an orphaned blob is preferable to a
            // dangling row that renders as a broken image forever.
            log.warn("Makbuz eki silinemedi ({}): {}", attachment.getStorageKey(), e.getMessage());
        }
        attachmentRepository.delete(attachment);

        auditService.log(AuditAction.RECEIPT_ATTACHMENT_DELETE,
                DomainEntityType.StockTransfer.name(),
                receipt != null ? receipt.getTransfer().getId() : null, username,
                "İmzalı makbuz nüshası silindi"
                        + (receipt != null ? " (" + receipt.getReceiptNo() + ")" : ""));
    }

    @Override
    @Transactional(readOnly = true)
    public StoredAttachment loadAttachment(Long attachmentId) {
        DeliveryReceiptAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                        "Dosya bulunamadı."));
        try (InputStream in = photoStorageService.openDocumentStream(attachment.getStorageKey())) {
            return new StoredAttachment(in.readAllBytes(),
                    attachment.getContentType() != null
                            ? attachment.getContentType()
                            : UploadValidator.safeContentTypeFor(attachment.getStorageKey()),
                    attachment.getFileName());
        } catch (Exception e) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Dosya okunamadı.");
        }
    }

    // ─────────────────────────────── Helpers ─────────────────────────────

    private StockTransfer loadTransfer(Long transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.TRANSFER_NOT_FOUND,
                        "Sevkiyat bulunamadı."));
    }

    private DeliveryReceipt requireReceipt(Long transferId) {
        return receiptRepository.findByTransferId(transferId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                        "Bu sevkiyat için henüz makbuz düzenlenmemiş."));
    }

    private AuditMetadata metadata(StockTransfer transfer) {
        if (transfer == null) return null;
        return AuditMetadata.builder()
                .transferId(transfer.getId())
                .sourceWarehouseId(transfer.getSourceWarehouse() != null
                        ? transfer.getSourceWarehouse().getId() : null)
                .sourceWarehouseName(transfer.getSourceWarehouse() != null
                        ? transfer.getSourceWarehouse().getName() : null)
                .customerName(transfer.getCustomerFullName())
                .customerPhone(transfer.getCustomerPhone())
                .build();
    }

    /** First non-blank value among the given setting keys. */
    private String setting(String... keys) {
        for (String key : keys) {
            String value = siteSettingService.getSetting(key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String format(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME);
    }

    private static String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private DeliveryReceiptDto toDto(DeliveryReceipt receipt) {
        List<DeliveryReceiptAttachment> attachments =
                attachmentRepository.findByReceiptIdOrderByUploadedAtAsc(receipt.getId());

        return DeliveryReceiptDto.builder()
                .id(receipt.getId())
                .transferId(receipt.getTransfer() != null ? receipt.getTransfer().getId() : null)
                .receiptNo(receipt.getReceiptNo())
                .status(receipt.getStatus())
                .kind(receipt.getKind())
                .revision(receipt.getRevision())
                .sourceWarehouseName(receipt.getSourceWarehouseName())
                .customerFullName(receipt.getCustomerFullName())
                .customerPhone(receipt.getCustomerPhone())
                .customerAddress(receipt.getCustomerAddress())
                .orderNumber(receipt.getOrderNumber())
                .driverName(receipt.getDriverName())
                .driverPhone(receipt.getDriverPhone())
                .vehiclePlate(receipt.getVehiclePlate())
                .transferDate(receipt.getTransferDate())
                .notes(receipt.getNotes())
                .deliveredAt(receipt.getDeliveredAt())
                .deliveredByName(receipt.getDeliveredByName())
                .receivedByName(receipt.getReceivedByName())
                .receivedByNote(receipt.getReceivedByNote())
                .confirmedAt(receipt.getConfirmedAt())
                .confirmedBy(receipt.getConfirmedBy())
                .handoverToName(receipt.getHandoverToName())
                .handoverToPhone(receipt.getHandoverToPhone())
                .handedOverByName(receipt.getHandedOverByName())
                .issuedAt(receipt.getIssuedAt())
                .issuedBy(receipt.getIssuedBy())
                .items(deserialiseItems(receipt))
                .signedCopyOnFile(!attachments.isEmpty())
                .attachments(attachments.stream().map(this::toAttachmentDto).toList())
                .build();
    }

    private DeliveryReceiptDto.AttachmentDto toAttachmentDto(DeliveryReceiptAttachment attachment) {
        return DeliveryReceiptDto.AttachmentDto.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .contentType(attachment.getContentType())
                .sizeBytes(attachment.getSizeBytes())
                .uploadedAt(attachment.getUploadedAt())
                .uploadedBy(attachment.getUploadedBy())
                // Signed and expiring: the panel renders these in <img>/<iframe>, which
                // cannot carry the Bearer token, so the URL itself has to be the proof.
                .url("/api/admin/delivery-receipts/attachments/" + attachment.getId() + "/view"
                        + SignedUrlService.query(SIGNED_RESOURCE, attachment.getId()))
                .build();
    }
}
