package com.warehouse.service;

import com.warehouse.entity.Category;
import com.warehouse.entity.Product;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.StockTransferItem;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.repository.WarehouseRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Writes a filled-in receipt to disk so the layout can be eyeballed after a template
 * change. Not part of the normal run — the assertions live in
 * {@link DeliveryReceiptServiceTest}; this only produces artefacts.
 *
 * <pre>mvn test -Dtest=ReceiptPreviewDumpTest -Dreceipt.dump=target/receipt-preview</pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIfSystemProperty(named = "receipt.dump", matches = ".+")
class ReceiptPreviewDumpTest {

    @Autowired private DeliveryReceiptService receiptService;
    @Autowired private StockTransferRepository transferRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private com.warehouse.service.PhotoStorageService photoStorageService;
    @Autowired private com.warehouse.service.SiteSettingService siteSettingService;

    /**
     * Optionally installs a logo and company details so the preview shows the real
     * letterhead. Pass {@code -Dreceipt.logo=/path/to/logo.png}; without it the header
     * falls back to the company name in text, which is also a case worth looking at.
     */
    private void installBranding() throws Exception {
        String logoPath = System.getProperty("receipt.logo");
        java.util.Map<String, String> settings = new java.util.LinkedHashMap<>();
        settings.put("company_name", "ATS DTM Tarım Hayvancılık San. ve Tic. Ltd. Şti.");
        settings.put("company_address", "Kale Mah. Paşakapı Cad. No: 28, Merkez / NİĞDE");
        settings.put("company_phone", "0388 502 33 03");

        if (logoPath != null && !logoPath.isBlank()) {
            java.nio.file.Path file = java.nio.file.Path.of(logoPath);
            try (java.io.InputStream in = java.nio.file.Files.newInputStream(file)) {
                var stored = photoStorageService.storeSiteAsset(
                        "logo", file.getFileName().toString(), "image/png", in);
                settings.put("site_logo", stored.relativePath());
            }
        }
        siteSettingService.updateSettings(settings, "preview");
    }

    @Test
    void dumpPreview() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("yusuf", "pw",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        installBranding();

        Category category = new Category();
        category.setName("Beyaz Eşya");
        category.setSlug("beyaz-esya-" + System.nanoTime());
        category = categoryRepository.save(category);

        Warehouse warehouse = new Warehouse();
        warehouse.setName("Merkez Depo — Kale Mah.");
        warehouse.setLocation("Niğde");
        warehouse = warehouseRepository.save(warehouse);

        StockTransfer transfer = new StockTransfer();
        transfer.setSourceWarehouse(warehouse);
        transfer.setQuantity(1);
        transfer.setDriverName("Ahmet Yılmaz");
        transfer.setDriverTcId("12345678901");
        transfer.setDriverPhone("0553 261 33 03");
        transfer.setVehiclePlate("51 ATS 303");
        transfer.setTransferType(TransferType.CUSTOMER_DELIVERY);
        transfer.setCustomerFullName("Işık Şahin Mobilya Ltd. Şti.");
        transfer.setCustomerPhone("0553 999 33 03");
        transfer.setCustomerAddress("Selçuk Mah. Dr. Sami Yağız Cad. No: 53, Merkez / NİĞDE");
        transfer.setOrderNumber("ORD20260904A1B2C3");
        transfer.setNotes("Ürünler asansörle çıkarıldı, ambalajlar müşteride bırakıldı.");
        transfer.setStatus(TransferStatus.IN_TRANSIT);
        transfer.setTransferDate(LocalDateTime.now());

        String[][] rows = {
                {"PRF-BZD-4820", "Profilo BD3086 Çift Kapılı No-Frost Buzdolabı", "1"},
                {"SMF-ANK-9012", "Simfer Ankastre Fırın Seti (Fırın + Ocak + Davlumbaz)", "2"},
                {"FKR-SUP-3311", "Fakir Veyron Turbo XL Dikey Süpürge", "3"},
                {"KRC-YIK-7788", "Kärcher K5 Premium Yüksek Basınçlı Yıkama Makinesi", "1"},
                {"PHL-UTU-1150", "Philips PerfectCare 6000 Serisi Ütü", "2"},
        };
        for (String[] row : rows) {
            Product product = new Product();
            product.setName(row[1]);
            product.setSku(row[0]);
            product.setSlug(row[0].toLowerCase() + "-" + System.nanoTime());
            product.setCategory(category);
            product = productRepository.save(product);

            StockTransferItem item = new StockTransferItem();
            item.setProduct(product);
            item.setQuantity(Integer.parseInt(row[2]));
            transfer.addItem(item);
        }
        transfer = transferRepository.save(transfer);

        receiptService.issue(transfer.getId(), "yusuf");
        receiptService.confirmDelivery(transfer.getId(), "Ahmet Yılmaz", "Ayşe Gültekin",
                LocalDateTime.now(), "Eksiksiz teslim alındı.", "yusuf");

        Path dir = Path.of(System.getProperty("receipt.dump"));
        Files.createDirectories(dir);

        byte[] pdf = receiptService.renderPdf(transfer.getId(), "yusuf");
        Files.write(dir.resolve("makbuz.pdf"), pdf);
        Files.writeString(dir.resolve("makbuz.html"),
                receiptService.renderHtml(transfer.getId(), true));

        try (PDDocument document = PDDocument.load(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, 110);
                ImageIO.write(image, "png", dir.resolve("makbuz-s" + (page + 1) + ".png").toFile());
            }
        }
        SecurityContextHolder.clearContext();
    }
}
