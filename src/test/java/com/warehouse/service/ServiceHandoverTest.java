package com.warehouse.service;

import com.warehouse.dto.CarrierAssignmentRequest;
import com.warehouse.dto.ServiceHandoverRequest;
import com.warehouse.entity.Category;
import com.warehouse.entity.Product;
import com.warehouse.entity.Stock;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.DeliveryReceiptKind;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.WarehouseRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Depot exit — goods handed to a service company before the carrier is known.
 *
 * <p>The property that matters most here is not on the paper: the goods must leave the
 * books exactly once. The whole design routes this through the ordinary transfer so that
 * naming the driver afterwards cannot become a second departure, and these tests are what
 * hold that line.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ServiceHandoverTest {

    @Autowired private ServiceHandoverService handoverService;
    @Autowired private StockTransferService transferService;
    @Autowired private DeliveryReceiptService receiptService;
    @Autowired private StockRepository stockRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Warehouse warehouse;
    private Product product;
    private Stock stock;

    /** The service company name carries every Turkish letter WinAnsi cannot encode. */
    private static final String SERVICE_NAME = "Işık Çğüöş Nakliyat Ltd. Şti.";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "pw",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        Category category = new Category();
        category.setName("Devir Kategori");
        category.setSlug("devir-kategori-" + System.nanoTime());
        category = categoryRepository.save(category);

        product = new Product();
        product.setName("Buzdolabı Çift Kapılı");
        product.setSku("DVR-001");
        product.setSlug("devir-buzdolabi-" + System.nanoTime());
        product.setCategory(category);
        product = productRepository.save(product);

        warehouse = new Warehouse();
        warehouse.setName("Merkez Depo");
        warehouse.setLocation("Niğde");
        warehouse = warehouseRepository.save(warehouse);

        stock = new Stock();
        stock.setProduct(product);
        stock.setWarehouse(warehouse);
        stock.setQuantity(40);
        stock = stockRepository.save(stock);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ServiceHandoverRequest request(int quantity) {
        ServiceHandoverRequest request = new ServiceHandoverRequest();
        request.setSourceWarehouseId(warehouse.getId());
        request.setHandoverToName(SERVICE_NAME);
        request.setHandoverToPhone("0553 261 33 03");
        request.setHandedOverBy("Mehmet Güneş");
        request.setCustomerFullName("Ayşe Gültekin");
        request.setCustomerPhone("05559876543");
        request.setCustomerAddress("Kale Mah. Paşakapı Cad. No: 28 Niğde");

        ServiceHandoverRequest.Item item = new ServiceHandoverRequest.Item();
        item.setProductId(product.getId());
        item.setQuantity(quantity);
        request.setItems(List.of(item));
        return request;
    }

    @Test
    @DisplayName("Servise teslim stoktan hemen düşer ve sevkiyat taşıyıcı bekler durumda kalır")
    void handoverDeductsStockImmediately() {
        var result = handoverService.handOver(request(3), "admin");

        assertThat(stockRepository.findById(stock.getId()).orElseThrow().getQuantity())
                .as("mal fiziken çıktı, stok hemen düşmeli")
                .isEqualTo(37);
        // Nothing is held back: a reservation would mean the goods are still ours, and they
        // are not — they left the building when the paper was signed.
        assertThat(stockRepository.findById(stock.getId()).orElseThrow().getReservedQuantity())
                .isZero();

        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.transfer().getTransferType()).isEqualTo(TransferType.CUSTOMER_DELIVERY);
        assertThat(result.transfer().isCarrierPending()).isTrue();
        assertThat(result.transfer().getDriverName()).isNull();
        assertThat(result.transfer().getVehiclePlate()).isNull();
        assertThat(result.transfer().getHandoverToName()).isEqualTo(SERVICE_NAME);
    }

    @Test
    @DisplayName("Depo çıkış makbuzu tek nüsha, DC serisinden ve devralan servisi taşır")
    void handoverReceiptIsSingleCopy() throws Exception {
        var result = handoverService.handOver(request(2), "admin");

        assertThat(result.receipt().getKind()).isEqualTo(DeliveryReceiptKind.SERVICE_HANDOVER);
        assertThat(result.receipt().getReceiptNo()).startsWith("DC-");
        assertThat(result.receipt().getHandoverToName()).isEqualTo(SERVICE_NAME);
        assertThat(result.receipt().getHandedOverByName()).isEqualTo("Mehmet Güneş");

        byte[] pdf = receiptService.renderPdf(result.transfer().getId(), "admin");
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdf))) {
            assertThat(document.getNumberOfPages())
                    .as("tek nüsha: servis yerinde imzalar, kâğıt bizde kalır")
                    .isEqualTo(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("DEPO ÇIKIŞ MAKBUZU");
            assertThat(text).contains("Nakliyat");          // Turkish letters survived
            assertThat(text).contains("Sonradan belirlenecek");
            assertThat(text).doesNotContain("MÜŞTERİ NÜSHASI");
        }
    }

    @Test
    @DisplayName("Taşıyıcı sonradan girilir; stok ikinci kez düşmez")
    void assigningCarrierDoesNotTouchStock() {
        var result = handoverService.handOver(request(5), "admin");
        int afterHandover = stockRepository.findById(stock.getId()).orElseThrow().getQuantity();

        CarrierAssignmentRequest carrier = new CarrierAssignmentRequest();
        carrier.setDriverName("Ahmet Yılmaz");
        carrier.setDriverTcId("12345678901");
        carrier.setDriverPhone("05551234567");
        carrier.setVehiclePlate("51 ats 303");

        StockTransfer updated = transferService.assignCarrier(result.transfer().getId(), carrier);

        assertThat(updated.isCarrierPending()).isFalse();
        assertThat(updated.getDriverName()).isEqualTo("Ahmet Yılmaz");
        assertThat(updated.getVehiclePlate()).isEqualTo("51 ATS 303");
        assertThat(stockRepository.findById(stock.getId()).orElseThrow().getQuantity())
                .as("taşıyıcının adının yazılması ikinci bir çıkış değil")
                .isEqualTo(afterHandover);

        // Filling the carrier in twice would be an attempt to send the same goods out again.
        assertThatThrownBy(() -> transferService.assignCarrier(result.transfer().getId(), carrier))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("zaten girilmiş");
    }

    @Test
    @DisplayName("Taşıyıcı girildikten sonra da makbuz depo çıkış makbuzu olarak basılır")
    void receiptKeepsItsIdentityAfterCarrierIsKnown() throws Exception {
        var result = handoverService.handOver(request(1), "admin");
        String receiptNo = result.receipt().getReceiptNo();

        CarrierAssignmentRequest carrier = new CarrierAssignmentRequest();
        carrier.setDriverName("Ahmet Yılmaz");
        carrier.setDriverTcId("12345678901");
        carrier.setDriverPhone("05551234567");
        carrier.setVehiclePlate("51 ATS 303");
        transferService.assignCarrier(result.transfer().getId(), carrier);

        // Reprinting re-snapshots the shipment, so the driver now appears — but the document
        // is still the paper that was signed at the counter, with the same number.
        var reissued = receiptService.issue(result.transfer().getId(), "admin");
        assertThat(reissued.getKind()).isEqualTo(DeliveryReceiptKind.SERVICE_HANDOVER);
        assertThat(reissued.getReceiptNo()).isEqualTo(receiptNo);
        assertThat(reissued.getRevision()).isEqualTo(2);
        assertThat(reissued.getDriverName()).isEqualTo("Ahmet Yılmaz");

        byte[] pdf = receiptService.renderPdf(result.transfer().getId(), "admin");
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdf))) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("DEPO ÇIKIŞ MAKBUZU");
            assertThat(text).doesNotContain("Sonradan belirlenecek");
        }
    }

    @Test
    @DisplayName("Stok yetmiyorsa çıkış yapılamaz")
    void handoverRespectsAvailableStock() {
        assertThatThrownBy(() -> handoverService.handOver(request(999), "admin"))
                .isInstanceOf(WarehouseManagementException.class);

        assertThat(stockRepository.findById(stock.getId()).orElseThrow().getQuantity())
                .isEqualTo(40);
    }

    @Test
    @DisplayName("Normal transfer hâlâ şoför bilgisi olmadan oluşturulamaz")
    void ordinaryTransferStillRequiresACarrier() {
        // The carrier columns became nullable for the depot exit. This is the test that keeps
        // that from quietly becoming "the driver is optional everywhere".
        StockTransfer transfer = new StockTransfer();
        transfer.setSourceWarehouse(warehouse);
        transfer.setTransferType(TransferType.CUSTOMER_DELIVERY);
        transfer.setCustomerFullName("Ayşe Gültekin");
        transfer.setCustomerPhone("05559876543");
        transfer.setCustomerAddress("Kale Mah. No: 28 Niğde");

        com.warehouse.entity.StockTransferItem item = new com.warehouse.entity.StockTransferItem();
        item.setProduct(product);
        item.setQuantity(1);
        transfer.addItem(item);

        assertThatThrownBy(() -> transferService.createTransfer(transfer))
                .isInstanceOf(WarehouseManagementException.class);
    }
}
