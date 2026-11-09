package com.warehouse.service;

import com.warehouse.dto.ServiceHandoverRequest;
import com.warehouse.dto.TransferReturnRequest;
import com.warehouse.entity.Category;
import com.warehouse.entity.Product;
import com.warehouse.entity.Stock;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.TransferReturnReason;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.DeliveryReceiptRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sevkiyat silindiğinde stok, o sevkiyat hiç girilmemiş gibi olmalı.
 *
 * <p>Sahadaki vaka: aynı depo çıkışı yanlışlıkla iki kez girildi, biri silindi, stok iki kez
 * düşülmüş olarak kaldı. Silme yalnızca kaydı kaldırıyordu.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransferDeletionStockReversalTest {

    private static final int ON_HAND = 40;

    @Autowired private ServiceHandoverService handoverService;
    @Autowired private StockTransferService transferService;
    @Autowired private StockRepository stockRepository;
    @Autowired private StockTransferRepository transferRepository;
    @Autowired private DeliveryReceiptRepository receiptRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    /** Güvenlik şifresi bu testlerin konusu değil; doğrulamayı geçiriyoruz. */
    @MockBean private AdminSecurityService adminSecurityService;

    private Warehouse warehouse;
    private Warehouse destinationWarehouse;
    private Product product;
    private Stock stock;

    @BeforeEach
    void setUp() {
        loginAs("admin", "ADMIN");

        Category category = new Category();
        category.setName("Silme Kategori");
        category.setSlug("silme-kategori-" + System.nanoTime());
        category = categoryRepository.save(category);

        product = new Product();
        product.setName("Çamaşır Makinesi");
        product.setSku("SIL-001");
        product.setSlug("silme-camasir-" + System.nanoTime());
        product.setCategory(category);
        product = productRepository.save(product);

        warehouse = new Warehouse();
        warehouse.setName("Merkez Depo");
        warehouse.setLocation("Niğde");
        warehouse = warehouseRepository.save(warehouse);

        destinationWarehouse = new Warehouse();
        destinationWarehouse.setName("Şube Depo");
        destinationWarehouse.setLocation("Aksaray");
        destinationWarehouse = warehouseRepository.save(destinationWarehouse);

        stock = new Stock();
        stock.setProduct(product);
        stock.setWarehouse(warehouse);
        stock.setQuantity(ON_HAND);
        stock = stockRepository.save(stock);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "pw",
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private ServiceHandoverRequest handover(int quantity) {
        ServiceHandoverRequest request = new ServiceHandoverRequest();
        request.setSourceWarehouseId(warehouse.getId());
        request.setHandoverToName("Servis Firması");
        request.setHandedOverBy("Mehmet Güneş");
        request.setCustomerFullName("Ayşe Gültekin");
        request.setCustomerPhone("05559876543");
        request.setCustomerAddress("Kale Mah. No: 28 Niğde");
        ServiceHandoverRequest.Item item = new ServiceHandoverRequest.Item();
        item.setProductId(product.getId());
        item.setQuantity(quantity);
        request.setItems(List.of(item));
        return request;
    }

    private StockTransfer completedWarehouseTransfer(int quantity) {
        StockTransfer t = new StockTransfer();
        t.setProduct(product);
        t.setSourceWarehouse(warehouse);
        t.setDestinationWarehouse(destinationWarehouse);
        t.setQuantity(quantity);
        t.setDriverName("Ali Veli");
        t.setDriverTcId("12345678901");
        t.setDriverPhone("05555555555");
        t.setVehiclePlate("51ABC51");
        t.setTransferType(TransferType.WAREHOUSE);
        StockTransfer created = transferService.createTransfer(t);
        transferService.startTransfer(created.getId());
        return transferService.completeTransfer(created.getId(), null);
    }

    private Stock source() {
        return stockRepository.findById(stock.getId()).orElseThrow();
    }

    private Stock destination() {
        return stockRepository.findByProductAndWarehouse(product, destinationWarehouse).orElseThrow();
    }

    @Test
    @DisplayName("Mükerrer depo çıkışının biri silinince stok tek çıkış yapılmış hâline döner")
    void deletingDuplicateHandoverRestoresStock() {
        var first = handoverService.handOver(handover(3), "admin");
        var duplicate = handoverService.handOver(handover(3), "admin");
        assertThat(source().getQuantity()).isEqualTo(ON_HAND - 6);

        var result = transferService.deleteTransfer(duplicate.transfer().getId(), "kod");

        assertThat(result.isDeleted()).isTrue();
        assertThat(source().getQuantity()).as("yalnızca ilk çıkış düşülmüş olmalı").isEqualTo(ON_HAND - 3);
        assertThat(source().getReservedQuantity()).isZero();
        assertThat(transferRepository.findById(duplicate.transfer().getId())).isEmpty();
        assertThat(receiptRepository.findByTransferId(duplicate.transfer().getId()))
                .as("silinen çıkışın makbuzu arşivde karşılıksız kalmamalı").isEmpty();
        assertThat(receiptRepository.findByTransferId(first.transfer().getId())).isPresent();
    }

    @Test
    @DisplayName("Planlı (rezervede bekleyen) çıkış silinince rezervasyon serbest kalır")
    void deletingScheduledHandoverReleasesReservation() {
        ServiceHandoverRequest request = handover(5);
        request.setScheduledDeliveryAt(LocalDateTime.now().plusDays(2));
        var planned = handoverService.handOver(request, "admin");
        assertThat(source().getReservedQuantity()).isEqualTo(5);

        transferService.deleteTransfer(planned.transfer().getId(), "kod");

        assertThat(source().getQuantity()).isEqualTo(ON_HAND);
        assertThat(source().getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("Tamamlanmış depo transferi silinince mal kaynağa döner, hedeften düşer")
    void deletingCompletedWarehouseTransferMovesGoodsBack() {
        StockTransfer transfer = completedWarehouseTransfer(7);
        assertThat(destination().getQuantity()).isEqualTo(7);

        transferService.deleteTransfer(transfer.getId(), "kod");

        assertThat(source().getQuantity()).isEqualTo(ON_HAND);
        assertThat(destination().getQuantity()).isZero();
    }

    @Test
    @DisplayName("Hedefe giren mal başka işte kullanıldıysa silme reddedilir, stok kıpırdamaz")
    void refusesWhenDestinationNoLongerHoldsTheGoods() {
        StockTransfer transfer = completedWarehouseTransfer(7);
        Stock dest = destination();
        dest.setReservedQuantity(5);
        stockRepository.save(dest);

        assertThatThrownBy(() -> transferService.deleteTransfer(transfer.getId(), "kod"))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("geri alınacak yeterli stok yok");

        assertThat(source().getQuantity()).isEqualTo(ON_HAND - 7);
        assertThat(destination().getQuantity()).isEqualTo(7);
        assertThat(transferRepository.findById(transfer.getId())).isPresent();
    }

    @Test
    @DisplayName("Toplu silme de stoğu geri alır")
    void bulkDeleteRestoresStock() {
        var a = handoverService.handOver(handover(2), "admin");
        var b = handoverService.handOver(handover(4), "admin");

        var response = transferService.deleteTransfers(List.of(a.transfer().getId(), b.transfer().getId()));

        assertThat(response.getSuccessCount()).isEqualTo(2);
        assertThat(source().getQuantity()).isEqualTo(ON_HAND);
    }

    @Test
    @DisplayName("Yönetici olmayanın silme talebi onaylanınca stok geri alınır, kayıt iptal görünür")
    void approvedDeleteRequestRestoresStock() {
        var duplicate = handoverService.handOver(handover(3), "admin");
        Long id = duplicate.transfer().getId();

        loginAs("depocu", "STOCK_OUT");
        var request = transferService.deleteTransfer(id, null);
        assertThat(request.isApprovalRequested()).isTrue();
        assertThat(source().getQuantity()).as("talep tek başına stoğa dokunmaz").isEqualTo(ON_HAND - 3);

        loginAs("admin", "ADMIN");
        transferService.approveTransferStart(id, null, "kod");

        assertThat(source().getQuantity()).isEqualTo(ON_HAND);
        assertThat(transferRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(TransferStatus.CANCELLED);
    }

    @Test
    @DisplayName("Deposu sonradan düzeltilmiş makbuz silinince mal düzeltilmiş depoya döner, eski depo değişmez")
    void deletingEditedHandoverRestoresToCorrectedWarehouse() {
        Stock other = new Stock();
        other.setProduct(product);
        other.setWarehouse(destinationWarehouse);
        other.setQuantity(10);
        other = stockRepository.save(other);

        var shipped = handoverService.handOver(handover(3), "admin");
        com.warehouse.dto.HandoverContentsUpdateRequest edit = new com.warehouse.dto.HandoverContentsUpdateRequest();
        edit.setSourceWarehouseId(destinationWarehouse.getId());
        ServiceHandoverRequest.Item line = new ServiceHandoverRequest.Item();
        line.setProductId(product.getId());
        line.setStockId(other.getId());
        line.setQuantity(2);
        edit.setItems(List.of(line));
        edit.setReason("Yanlış depo seçilmiş");
        transferService.updateHandoverContents(shipped.transfer().getId(), edit);
        assertThat(source().getQuantity()).isEqualTo(ON_HAND);
        assertThat(stockRepository.findById(other.getId()).orElseThrow().getQuantity()).isEqualTo(8);

        transferService.deleteTransfer(shipped.transfer().getId(), "kod");

        assertThat(source().getQuantity()).as("eski depo zaten düzeltmeyle geri almıştı").isEqualTo(ON_HAND);
        assertThat(stockRepository.findById(other.getId()).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(receiptRepository.findByTransferId(shipped.transfer().getId()))
                .as("düzeltme geçmişi olan makbuz da silinir").isEmpty();
    }

    @Test
    @DisplayName("Çok kalemli çıkış silinince her kalem kendi satırına döner")
    void deletingMultiItemHandoverRestoresEveryLine() {
        Product second = new Product();
        second.setName("Bulaşık Makinesi");
        second.setSku("SIL-002");
        second.setSlug("silme-bulasik-" + System.nanoTime());
        second.setCategory(product.getCategory());
        second = productRepository.save(second);
        Stock secondStock = new Stock();
        secondStock.setProduct(second);
        secondStock.setWarehouse(warehouse);
        secondStock.setQuantity(12);
        secondStock = stockRepository.save(secondStock);

        ServiceHandoverRequest request = handover(4);
        ServiceHandoverRequest.Item extra = new ServiceHandoverRequest.Item();
        extra.setProductId(second.getId());
        extra.setQuantity(5);
        request.setItems(List.of(request.getItems().get(0), extra));
        var shipped = handoverService.handOver(request, "admin");
        assertThat(stockRepository.findById(secondStock.getId()).orElseThrow().getQuantity()).isEqualTo(7);

        transferService.deleteTransfer(shipped.transfer().getId(), "kod");

        assertThat(source().getQuantity()).isEqualTo(ON_HAND);
        assertThat(stockRepository.findById(secondStock.getId()).orElseThrow().getQuantity()).isEqualTo(12);
    }

    @Test
    @DisplayName("Beklemedeki ve iptal edilmiş transferin silinmesi stoğa dokunmaz")
    void pendingAndCancelledTransfersLeaveStockAlone() {
        StockTransfer t = new StockTransfer();
        t.setProduct(product);
        t.setSourceWarehouse(warehouse);
        t.setDestinationWarehouse(destinationWarehouse);
        t.setQuantity(6);
        t.setDriverName("Ali Veli");
        t.setDriverTcId("12345678901");
        t.setDriverPhone("05555555555");
        t.setVehiclePlate("51ABC51");
        t.setTransferType(TransferType.WAREHOUSE);
        StockTransfer pending = transferService.createTransfer(t);
        transferService.deleteTransfer(pending.getId(), "kod");
        assertThat(source().getQuantity()).isEqualTo(ON_HAND);
        assertThat(source().getReservedQuantity()).isZero();

        ServiceHandoverRequest request = handover(5);
        request.setScheduledDeliveryAt(LocalDateTime.now().plusDays(2));
        var planned = handoverService.handOver(request, "admin");
        transferService.cancelTransfer(planned.transfer().getId(), "vazgeçildi");
        assertThat(source().getReservedQuantity()).isZero();
        transferService.deleteTransfer(planned.transfer().getId(), "kod");
        assertThat(source().getQuantity()).isEqualTo(ON_HAND);
        assertThat(source().getReservedQuantity()).as("iptal rezervasyonu zaten bırakmıştı, ikinci kez bırakılmaz")
                .isZero();
    }

    @Test
    @DisplayName("Aynı kayıt ikinci kez silinmeye çalışılırsa stok ikinci kez geri eklenmez")
    void secondDeleteOfSameTransferIsRefused() {
        var shipped = handoverService.handOver(handover(3), "admin");
        Long id = shipped.transfer().getId();
        transferService.deleteTransfer(id, "kod");

        assertThatThrownBy(() -> transferService.deleteTransfer(id, "kod"))
                .isInstanceOf(WarehouseManagementException.class);
        assertThat(source().getQuantity()).isEqualTo(ON_HAND);
    }

    @Test
    @DisplayName("Planlı çıkışın silme talebi onaylanınca rezervasyon kalkar")
    void approvedDeleteRequestOnPlannedHandoverReleasesReservation() {
        ServiceHandoverRequest request = handover(4);
        request.setScheduledDeliveryAt(LocalDateTime.now().plusDays(2));
        var planned = handoverService.handOver(request, "admin");
        Long id = planned.transfer().getId();

        loginAs("depocu", "STOCK_OUT");
        transferService.deleteTransfer(id, null);
        loginAs("admin", "ADMIN");
        transferService.approveTransferStart(id, "mükerrer", "kod");

        assertThat(source().getQuantity()).isEqualTo(ON_HAND);
        assertThat(source().getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("Toplu silmede biri reddedilirse diğerleri yine doğru geri alınır, reddedilen kıpırdamaz")
    void bulkDeleteWithOneRefusalKeepsOthersCorrect() {
        var ok = handoverService.handOver(handover(2), "admin");
        StockTransfer blocked = completedWarehouseTransfer(7);
        Stock dest = destination();
        dest.setReservedQuantity(7);
        stockRepository.save(dest);

        var response = transferService.deleteTransfers(List.of(ok.transfer().getId(), blocked.getId()));

        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getErrorCount()).isEqualTo(1);
        assertThat(source().getQuantity()).as("yalnız başarılı silmenin 2 adedi döndü").isEqualTo(ON_HAND - 7);
        assertThat(destination().getQuantity()).isEqualTo(7);
        assertThat(transferRepository.findById(blocked.getId())).isPresent();
    }

    @Test
    @DisplayName("İade kaydı olan çıkış silinemez; stok olduğu gibi kalır")
    void refusesWhenTransferHasReturns() {
        var shipped = handoverService.handOver(handover(3), "admin");
        StockTransfer transfer = transferRepository.findByIdWithRelations(shipped.transfer().getId()).orElseThrow();
        TransferReturnRequest returnRequest = new TransferReturnRequest();
        returnRequest.setReason(TransferReturnReason.UNDELIVERED);
        TransferReturnRequest.Item line = new TransferReturnRequest.Item();
        line.setTransferItemId(transfer.getItems().get(0).getId());
        line.setQuantity(1);
        returnRequest.setItems(List.of(line));
        transferService.recordReturn(transfer.getId(), returnRequest);
        assertThat(source().getQuantity()).isEqualTo(ON_HAND - 2);

        assertThatThrownBy(() -> transferService.deleteTransfer(transfer.getId(), "kod"))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("iade kaydı var");
        assertThat(source().getQuantity()).isEqualTo(ON_HAND - 2);
    }
}
