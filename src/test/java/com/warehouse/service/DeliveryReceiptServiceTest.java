package com.warehouse.service;

import com.warehouse.dto.DeliveryReceiptDto;
import com.warehouse.entity.Category;
import com.warehouse.entity.Product;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.StockTransferItem;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.DeliveryReceiptStatus;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.AuditLogRepository;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.repository.WarehouseRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The receipt is a document that gets signed and filed, so the properties worth pinning
 * are the ones that would quietly ruin it: Turkish characters, the snapshot not tracking
 * later edits, and the number staying put across reprints.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeliveryReceiptServiceTest {

    @Autowired private DeliveryReceiptService receiptService;
    @Autowired private StockTransferRepository transferRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private StockTransfer transfer;

    /** Every Turkish letter missing from WinAnsi, in one string. */
    private static final String TURKISH_CUSTOMER = "Işık Çğüöş Mobilya Ltd. Şti.";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "pw",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        Category category = new Category();
        category.setName("Makbuz Kategori");
        category.setSlug("makbuz-kategori-" + System.nanoTime());
        category = categoryRepository.save(category);

        Product product = new Product();
        product.setName("Buzdolabı Çift Kapılı");
        product.setSku("BZD-001");
        product.setSlug("buzdolabi-" + System.nanoTime());
        product.setCategory(category);
        product = productRepository.save(product);

        Warehouse warehouse = new Warehouse();
        warehouse.setName("Merkez Depo");
        warehouse.setLocation("Niğde");
        warehouse = warehouseRepository.save(warehouse);

        transfer = new StockTransfer();
        transfer.setSourceWarehouse(warehouse);
        transfer.setProduct(product);
        transfer.setQuantity(3);
        transfer.setDriverName("Ahmet Yılmaz");
        transfer.setDriverTcId("12345678901");
        transfer.setDriverPhone("05551234567");
        transfer.setVehiclePlate("51 ABC 123");
        transfer.setTransferType(TransferType.CUSTOMER_DELIVERY);
        transfer.setCustomerFullName(TURKISH_CUSTOMER);
        transfer.setCustomerPhone("05559876543");
        transfer.setCustomerAddress("Kale Mah. Paşakapı Cad. No: 28 Niğde");
        transfer.setStatus(TransferStatus.IN_TRANSIT);
        transfer.setTransferDate(LocalDateTime.now());

        StockTransferItem item = new StockTransferItem();
        item.setProduct(product);
        item.setQuantity(3);
        transfer.addItem(item);

        transfer = transferRepository.save(transfer);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Makbuz düzenlenince numara, revizyon ve kalemler kaydedilir")
    void issueCreatesReceipt() {
        DeliveryReceiptDto dto = receiptService.issue(transfer.getId(), "admin");

        assertThat(dto.getReceiptNo()).matches("TM-\\d{4}-\\d{6}");
        assertThat(dto.getRevision()).isEqualTo(1);
        assertThat(dto.getStatus()).isEqualTo(DeliveryReceiptStatus.ISSUED);
        assertThat(dto.getCustomerFullName()).isEqualTo(TURKISH_CUSTOMER);
        assertThat(dto.getVehiclePlate()).isEqualTo("51 ABC 123");
        assertThat(dto.getItems()).hasSize(1);
        assertThat(dto.getItems().get(0).getSku()).isEqualTo("BZD-001");
        assertThat(dto.getItems().get(0).getQuantity()).isEqualTo(3);
        assertThat(dto.isSignedCopyOnFile()).isFalse();
        // The driver is the obvious person to have handed the goods over.
        assertThat(dto.getDeliveredByName()).isEqualTo("Ahmet Yılmaz");
    }

    @Test
    @DisplayName("Yeniden basımda numara sabit kalır, sadece revizyon artar")
    void reissueKeepsTheNumber() {
        DeliveryReceiptDto first = receiptService.issue(transfer.getId(), "admin");
        DeliveryReceiptDto second = receiptService.issue(transfer.getId(), "admin");

        assertThat(second.getReceiptNo()).isEqualTo(first.getReceiptNo());
        assertThat(second.getRevision()).isEqualTo(2);
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    /**
     * The whole reason the receipt copies the shipment instead of joining to it: the
     * signed paper has to keep saying what the customer signed for.
     */
    @Test
    @DisplayName("Makbuz basıldıktan sonra transferdeki değişiklik makbuzu etkilemez")
    void theSnapshotDoesNotTrackLaterEdits() {
        DeliveryReceiptDto issued = receiptService.issue(transfer.getId(), "admin");
        assertThat(issued.getDriverName()).isEqualTo("Ahmet Yılmaz");

        transfer.setDriverName("Mehmet Demir");
        transfer.setVehiclePlate("06 XYZ 999");
        transfer.setCustomerAddress("Başka bir adres");
        transferRepository.save(transfer);

        DeliveryReceiptDto reloaded = receiptService.findByTransfer(transfer.getId());
        assertThat(reloaded.getDriverName()).isEqualTo("Ahmet Yılmaz");
        assertThat(reloaded.getVehiclePlate()).isEqualTo("51 ABC 123");
        assertThat(reloaded.getCustomerAddress()).isEqualTo("Kale Mah. Paşakapı Cad. No: 28 Niğde");

        // ...until it is deliberately reprinted, which is how a correction is made.
        DeliveryReceiptDto reissued = receiptService.issue(transfer.getId(), "admin");
        assertThat(reissued.getDriverName()).isEqualTo("Mehmet Demir");
        assertThat(reissued.getVehiclePlate()).isEqualTo("06 XYZ 999");
    }

    /**
     * PDFBox's built-in Helvetica cannot encode ı, ğ, ş, İ, Ğ or Ş. Without the bundled
     * Noto Sans the customer name would print as "Isik" or as empty boxes — on the one
     * document that exists to be signed as proof of delivery. Extracting the text back
     * out of the PDF is the only way to catch a font regression.
     */
    @Test
    @DisplayName("PDF üretilir ve Türkçe karakterler doğru gömülür")
    void pdfContainsTurkishCharacters() throws Exception {
        receiptService.issue(transfer.getId(), "admin");
        byte[] pdf = receiptService.renderPdf(transfer.getId(), "admin");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        // The layout is a two-column table, so extraction interleaves the columns and a
        // long address wraps. Asserting on contiguous strings would pin the layout, not
        // the encoding; these tokens are the ones that carry the characters at risk.
        String text = extractText(pdf);
        assertThat(text).contains("TESLİMAT MAKBUZU");
        assertThat(text).contains(TURKISH_CUSTOMER);
        assertThat(text).contains("Paşakapı");
        assertThat(text).contains("Niğde");
        assertThat(text).contains("Buzdolabı Çift Kapılı");
        assertThat(text).contains("Ahmet Yılmaz");
        assertThat(text).contains("51 ABC 123");
        // The characters WinAnsi cannot encode, spelled out so a font regression names itself.
        assertThat(text).contains("ı").contains("ğ").contains("ş").contains("İ").contains("Ş");
        // Firma ve müşteri nüshası: şoför birini bırakır, imzalıyı geri getirir.
        assertThat(text).contains("FİRMA NÜSHASI");
        assertThat(text).contains("MÜŞTERİ NÜSHASI");
        assertThat(text).contains("TESLİM EDEN");
        assertThat(text).contains("TESLİM ALAN");
    }

    @Test
    @DisplayName("Yazdırılabilir HTML aynı verileri içerir")
    void printableHtmlRenders() {
        receiptService.issue(transfer.getId(), "admin");
        String html = receiptService.renderHtml(transfer.getId(), true);

        assertThat(html).contains("TESLİMAT MAKBUZU");
        assertThat(html).contains(TURKISH_CUSTOMER);
        assertThat(html).contains("window.print()");
    }

    @Test
    @DisplayName("Teslim onayı teslim alanı ve tarihi kaydeder")
    void confirmDeliveryRecordsRecipient() {
        receiptService.issue(transfer.getId(), "admin");
        LocalDateTime when = LocalDateTime.now().minusHours(2);

        DeliveryReceiptDto dto = receiptService.confirmDelivery(
                transfer.getId(), "Ahmet Yılmaz", "Ayşe Kaya", when, "Kapıda teslim edildi", "admin");

        assertThat(dto.getStatus()).isEqualTo(DeliveryReceiptStatus.DELIVERED);
        assertThat(dto.getReceivedByName()).isEqualTo("Ayşe Kaya");
        assertThat(dto.getDeliveredAt()).isEqualTo(when);
        assertThat(dto.getConfirmedBy()).isEqualTo("admin");

        String text;
        try {
            text = extractText(receiptService.renderPdf(transfer.getId(), "admin"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertThat(text).contains("Ayşe Kaya");
    }

    @Test
    @DisplayName("Teslim alan adı olmadan onay reddedilir")
    void confirmRequiresRecipientName() {
        receiptService.issue(transfer.getId(), "admin");
        assertThatThrownBy(() -> receiptService.confirmDelivery(
                transfer.getId(), "Ahmet", "  ", LocalDateTime.now(), null, "admin"))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("Teslim alan");
    }

    @Test
    @DisplayName("İleri tarihli teslim reddedilir")
    void confirmRejectsFutureDate() {
        receiptService.issue(transfer.getId(), "admin");
        assertThatThrownBy(() -> receiptService.confirmDelivery(
                transfer.getId(), null, "Ayşe Kaya", LocalDateTime.now().plusDays(1), null, "admin"))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("ileri bir tarih");
    }

    @Test
    @DisplayName("İptal edilmiş sevkiyata makbuz düzenlenemez")
    void cancelledTransferCannotBeIssued() {
        transfer.setStatus(TransferStatus.CANCELLED);
        transferRepository.save(transfer);

        assertThatThrownBy(() -> receiptService.issue(transfer.getId(), "admin"))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("İptal edilmiş");
    }

    @Test
    @DisplayName("Makbuz düzenlenmemişken PDF istenirse anlaşılır hata döner")
    void pdfWithoutReceiptFails() {
        assertThatThrownBy(() -> receiptService.renderPdf(transfer.getId(), "admin"))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("makbuz düzenlenmemiş");
    }

    @Test
    @DisplayName("İmzalı nüsha yüklenir, sahte uzantı reddedilir")
    void signedCopyUpload() {
        receiptService.issue(transfer.getId(), "admin");

        DeliveryReceiptDto dto = receiptService.addAttachment(transfer.getId(),
                new MockMultipartFile("file", "imzali.png", "image/png", pngBytes()), "admin");

        assertThat(dto.isSignedCopyOnFile()).isTrue();
        assertThat(dto.getAttachments()).hasSize(1);
        assertThat(dto.getAttachments().get(0).getContentType()).isEqualTo("image/png");
        // Signed and expiring, because <img> cannot carry the Bearer token.
        assertThat(dto.getAttachments().get(0).getUrl()).contains("sig=").contains("exp=");

        // A script renamed to .png must not be stored and later served from our origin.
        assertThatThrownBy(() -> receiptService.addAttachment(transfer.getId(),
                new MockMultipartFile("file", "kotu.png", "image/png",
                        "<script>alert(1)</script>".getBytes()), "admin"))
                .isInstanceOf(WarehouseManagementException.class);
    }

    @Test
    @DisplayName("Toplu PDF sadece makbuzu olan sevkiyatları birleştirir")
    void bulkPdfSkipsTransfersWithoutReceipt() throws Exception {
        receiptService.issue(transfer.getId(), "admin");

        byte[] pdf = receiptService.renderBulkPdf(List.of(transfer.getId(), 999_999L), "admin");
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(extractText(pdf)).contains(TURKISH_CUSTOMER);

        assertThatThrownBy(() -> receiptService.renderBulkPdf(List.of(999_999L), "admin"))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("makbuz düzenlenmemiş");
    }

    @Test
    @DisplayName("Makbuz yaşam döngüsü denetim kaydına yazılır")
    void lifecycleIsAudited() {
        long before = auditLogRepository.count();
        receiptService.issue(transfer.getId(), "admin");
        receiptService.confirmDelivery(transfer.getId(), null, "Ayşe Kaya",
                LocalDateTime.now(), null, "admin");
        assertThat(auditLogRepository.count()).isGreaterThanOrEqualTo(before + 2);
    }

    @Test
    @DisplayName("Arşiv araması imzalı nüsha durumuna göre filtreler")
    void archiveSearchFiltersBySignedCopy() {
        receiptService.issue(transfer.getId(), "admin");

        assertThat(receiptService.search(null, false, null, null, null, PageRequest.of(0, 20))
                .getContent()).extracting(DeliveryReceiptDto::getTransferId).contains(transfer.getId());
        assertThat(receiptService.search(null, true, null, null, null, PageRequest.of(0, 20))
                .getContent()).isEmpty();

        receiptService.addAttachment(transfer.getId(),
                new MockMultipartFile("file", "n.png", "image/png", pngBytes()), "admin");

        assertThat(receiptService.search(null, true, null, null, null, PageRequest.of(0, 20))
                .getContent()).hasSize(1);
        assertThat(receiptService.search(null, null, null, null, "Işık", PageRequest.of(0, 20))
                .getContent()).hasSize(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static String extractText(byte[] pdf) throws Exception {
        try (PDDocument document = PDDocument.load(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /** Smallest valid PNG: the magic bytes are what the validator checks. */
    private static byte[] pngBytes() {
        try {
            java.awt.image.BufferedImage image =
                    new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
