package com.warehouse.service;

import com.warehouse.dto.ServiceHandoverRequest;
import com.warehouse.dto.StockTransferDto;
import com.warehouse.dto.TransferReturnDto;
import com.warehouse.dto.TransferReturnRequest;
import com.warehouse.entity.Category;
import com.warehouse.entity.Product;
import com.warehouse.entity.Stock;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.TransferReturnReason;
import com.warehouse.enums.TransferStatus;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.CategoryRepository;
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
 * Goods coming back from a shipment that already left.
 *
 * <p>The thing worth pinning is the arithmetic: what came back is on the shelf again, exactly
 * once, and never more than what went out — including across several partial returns, which is
 * how a failed delivery actually unwinds.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransferReturnTest {

    @Autowired private ServiceHandoverService handoverService;
    @Autowired private StockTransferService transferService;
    @Autowired private StockRepository stockRepository;
    @Autowired private StockTransferRepository transferRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Warehouse warehouse;
    private Product fridge;
    private Product oven;
    private Stock fridgeStock;
    private Stock ovenStock;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "pw",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        Category category = new Category();
        category.setName("İade Kategori");
        category.setSlug("iade-kategori-" + System.nanoTime());
        category = categoryRepository.save(category);

        fridge = newProduct(category, "IAD-001", "Buzdolabı Çift Kapılı");
        oven = newProduct(category, "IAD-002", "Ankastre Fırın Seti");

        warehouse = new Warehouse();
        warehouse.setName("Merkez Depo");
        warehouse.setLocation("Niğde");
        warehouse = warehouseRepository.save(warehouse);

        fridgeStock = newStock(fridge, 40);
        ovenStock = newStock(oven, 40);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Product newProduct(Category category, String sku, String name) {
        Product product = new Product();
        product.setName(name);
        product.setSku(sku);
        product.setSlug(sku.toLowerCase() + "-" + System.nanoTime());
        product.setCategory(category);
        return productRepository.save(product);
    }

    private Stock newStock(Product product, int quantity) {
        Stock stock = new Stock();
        stock.setProduct(product);
        stock.setWarehouse(warehouse);
        stock.setQuantity(quantity);
        return stockRepository.save(stock);
    }

    /** A depot exit of 3 fridges and 2 ovens, already completed and deducted. */
    private StockTransferDto ship() {
        ServiceHandoverRequest request = new ServiceHandoverRequest();
        request.setSourceWarehouseId(warehouse.getId());
        request.setHandoverToName("Yıldız Kargo ve Nakliyat Ltd. Şti.");
        request.setHandedOverBy("Mehmet Güneş");
        request.setCustomerFullName("Ayşe Gültekin");
        request.setCustomerPhone("05559876543");
        request.setCustomerAddress("Kale Mah. Paşakapı Cad. No: 28 Niğde");

        ServiceHandoverRequest.Item fridgeLine = new ServiceHandoverRequest.Item();
        fridgeLine.setProductId(fridge.getId());
        fridgeLine.setQuantity(3);
        ServiceHandoverRequest.Item ovenLine = new ServiceHandoverRequest.Item();
        ovenLine.setProductId(oven.getId());
        ovenLine.setQuantity(2);
        request.setItems(List.of(fridgeLine, ovenLine));

        return handoverService.handOver(request, "admin").transfer();
    }

    private Long lineIdFor(StockTransferDto transfer, Product product) {
        return transfer.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst().orElseThrow().getId();
    }

    private TransferReturnRequest returnOf(Long lineId, int quantity, TransferReturnReason reason) {
        TransferReturnRequest request = new TransferReturnRequest();
        request.setReason(reason);
        TransferReturnRequest.Item line = new TransferReturnRequest.Item();
        line.setTransferItemId(lineId);
        line.setQuantity(quantity);
        request.setItems(List.of(line));
        return request;
    }

    private int onHand(Stock stock) {
        return stockRepository.findById(stock.getId()).orElseThrow().getQuantity();
    }

    @Test
    @DisplayName("İade edilen adet stoğa geri eklenir, sevkiyat tamamlanmış kalır")
    void returnPutsStockBack() {
        StockTransferDto shipped = ship();
        assertThat(onHand(fridgeStock)).isEqualTo(37);

        TransferReturnDto recorded = transferService.recordReturn(shipped.getId(),
                returnOf(lineIdFor(shipped, fridge), 2, TransferReturnReason.UNDELIVERED));

        assertThat(recorded.getTotalQuantity()).isEqualTo(2);
        assertThat(recorded.getReason()).isEqualTo(TransferReturnReason.UNDELIVERED);
        assertThat(recorded.getItems()).singleElement()
                .satisfies(line -> assertThat(line.getProductSku()).isEqualTo("IAD-001"));

        assertThat(onHand(fridgeStock)).as("iki buzdolabı rafa döndü").isEqualTo(39);
        assertThat(onHand(ovenStock)).as("fırınlar dönmedi").isEqualTo(38);

        // The shipment happened. Its receipt was signed for goods that did go out the door,
        // so it stays completed and the return sits on top of it as its own record.
        StockTransfer reloaded = transferRepository.findById(shipped.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(reloaded.getReturnedQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("Kısmi iadeler birikir; sevk edilenden fazlası iade edilemez")
    void partialReturnsAccumulateAndAreCapped() {
        StockTransferDto shipped = ship();
        Long fridgeLine = lineIdFor(shipped, fridge);

        transferService.recordReturn(shipped.getId(),
                returnOf(fridgeLine, 1, TransferReturnReason.REFUSED));
        transferService.recordReturn(shipped.getId(),
                returnOf(fridgeLine, 2, TransferReturnReason.DAMAGED));

        assertThat(onHand(fridgeStock)).as("üçü de döndü").isEqualTo(40);
        assertThat(transferRepository.findById(shipped.getId()).orElseThrow().getReturnedQuantity())
                .isEqualTo(3);

        // A fourth would be inventing goods that never left.
        assertThatThrownBy(() -> transferService.recordReturn(shipped.getId(),
                returnOf(fridgeLine, 1, TransferReturnReason.OTHER)))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("en fazla adet: 0");

        assertThat(onHand(fridgeStock)).isEqualTo(40);
        assertThat(transferService.getReturns(shipped.getId())).hasSize(2);
    }

    @Test
    @DisplayName("Tek istekte aynı kalem iki kez gelirse toplamı sınırı aşamaz")
    void duplicateLinesInOneRequestAreFoldedBeforeChecking() {
        StockTransferDto shipped = ship();
        Long ovenLine = lineIdFor(shipped, oven);

        TransferReturnRequest request = new TransferReturnRequest();
        request.setReason(TransferReturnReason.WRONG_ITEM);
        TransferReturnRequest.Item first = new TransferReturnRequest.Item();
        first.setTransferItemId(ovenLine);
        first.setQuantity(2);
        TransferReturnRequest.Item second = new TransferReturnRequest.Item();
        second.setTransferItemId(ovenLine);
        second.setQuantity(1);
        request.setItems(List.of(first, second));

        // Two units shipped; 2 + 1 must be rejected as 3, not waved through as two separate
        // lines that each look small enough.
        assertThatThrownBy(() -> transferService.recordReturn(shipped.getId(), request))
                .isInstanceOf(WarehouseManagementException.class);
        assertThat(onHand(ovenStock)).isEqualTo(38);
    }

    @Test
    @DisplayName("Birden çok kalem tek iadede birlikte dönebilir")
    void severalLinesComeBackTogether() {
        StockTransferDto shipped = ship();

        TransferReturnRequest request = new TransferReturnRequest();
        request.setReason(TransferReturnReason.UNDELIVERED);
        request.setNote("Adres bulunamadı, tamamı geri getirildi.");
        TransferReturnRequest.Item fridgeLine = new TransferReturnRequest.Item();
        fridgeLine.setTransferItemId(lineIdFor(shipped, fridge));
        fridgeLine.setQuantity(3);
        TransferReturnRequest.Item ovenLine = new TransferReturnRequest.Item();
        ovenLine.setTransferItemId(lineIdFor(shipped, oven));
        ovenLine.setQuantity(2);
        request.setItems(List.of(fridgeLine, ovenLine));

        TransferReturnDto recorded = transferService.recordReturn(shipped.getId(), request);

        assertThat(recorded.getTotalQuantity()).isEqualTo(5);
        assertThat(recorded.getItems()).hasSize(2);
        assertThat(onHand(fridgeStock)).isEqualTo(40);
        assertThat(onHand(ovenStock)).isEqualTo(40);
        assertThat(transferRepository.findById(shipped.getId()).orElseThrow().getReturnedQuantity())
                .as("tamamı döndü").isEqualTo(5);
    }

    @Test
    @DisplayName("Henüz tamamlanmamış sevkiyat için iade kaydedilemez")
    void pendingShipmentCannotBeReturned() {
        StockTransferDto shipped = ship();
        StockTransfer transfer = transferRepository.findById(shipped.getId()).orElseThrow();
        transfer.setStatus(TransferStatus.PENDING);
        transferRepository.save(transfer);

        // Nothing left the building, so there is nothing to bring back — and restocking here
        // would conjure units that were never deducted.
        assertThatThrownBy(() -> transferService.recordReturn(shipped.getId(),
                returnOf(lineIdFor(shipped, fridge), 1, TransferReturnReason.OTHER)))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("tamamlanmış");
    }

    @Test
    @DisplayName("İade tarihi çıkış tarihinden önce ya da ileri bir tarih olamaz")
    void returnDateMustSitAfterTheShipment() {
        StockTransferDto shipped = ship();
        Long fridgeLine = lineIdFor(shipped, fridge);

        TransferReturnRequest backdated = returnOf(fridgeLine, 1, TransferReturnReason.OTHER);
        backdated.setReturnedAt(LocalDateTime.now().minusDays(30));
        assertThatThrownBy(() -> transferService.recordReturn(shipped.getId(), backdated))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("çıkış tarihinden önce");

        TransferReturnRequest future = returnOf(fridgeLine, 1, TransferReturnReason.OTHER);
        future.setReturnedAt(LocalDateTime.now().plusDays(2));
        assertThatThrownBy(() -> transferService.recordReturn(shipped.getId(), future))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("ileri bir tarih");

        assertThat(onHand(fridgeStock)).isEqualTo(37);
    }

    @Test
    @DisplayName("Başka sevkiyatın kalemi iade edilemez")
    void linesFromAnotherShipmentAreRejected() {
        StockTransferDto first = ship();
        StockTransferDto second = ship();

        assertThatThrownBy(() -> transferService.recordReturn(first.getId(),
                returnOf(lineIdFor(second, fridge), 1, TransferReturnReason.OTHER)))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("bu sevkiyata ait değil");
    }
}
