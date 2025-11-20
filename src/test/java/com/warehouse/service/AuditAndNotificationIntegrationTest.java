package com.warehouse.service;

import com.warehouse.entity.*;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditAndNotificationIntegrationTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private StockService stockService;
    @Autowired private StockTransferService stockTransferService;

    private Product product;
    private Category category;
    private Warehouse wh1;
    private Warehouse wh2;

    @BeforeEach
    void init() {
        notificationRepository.deleteAll();
        auditLogRepository.deleteAll();
        stockRepository.deleteAll();
        productRepository.deleteAll();
        warehouseRepository.deleteAll();

        category = new Category();
        category.setName("Integration Category");
        category = categoryRepository.save(category);

        product = new Product();
        product.setName("Test Product");
        product.setSku("INT-TEST-001");
        product.setCategory(category);
        productRepository.save(product);

        wh1 = new Warehouse();
        wh1.setName("WH-1");
        wh1.setLocation("Istanbul");
        warehouseRepository.save(wh1);

        wh2 = new Warehouse();
        wh2.setName("WH-2");
        wh2.setLocation("Ankara");
        warehouseRepository.save(wh2);
    }

    @Test
    void stockCreateAndAddShouldProduceAuditAndNotification() {
        Stock s = new Stock();
        s.setProduct(product);
        s.setWarehouse(wh1);
        s.setQuantity(10);
        Stock created = stockService.createStock(s);
        stockService.addToStock(created.getId(), 5);

        assertThat(auditLogRepository.findTop100ByOrderByCreatedAtDesc()).isNotEmpty();
        assertThat(notificationRepository.findTop20ByOrderByCreatedAtDesc()).isNotEmpty();
    }

    @Test
    void transferFlowShouldProduceAuditAndNotification() {
        // prepare source stock
        Stock s = new Stock();
        s.setProduct(product);
        s.setWarehouse(wh1);
        s.setQuantity(50);
        stockRepository.save(s);

        StockTransfer t = new StockTransfer();
        t.setProduct(product);
        t.setSourceWarehouse(wh1);
        t.setDestinationWarehouse(wh2);
        t.setQuantity(10);
        t.setDriverName("Test Driver");
        t.setDriverTcId("12345678901");
        t.setDriverPhone("05555555555");
        t.setVehiclePlate("34TEST34");
        t.setTransferType(TransferType.WAREHOUSE);

        t = stockTransferService.createTransfer(t);
        assertThat(t.getStatus()).isEqualTo(TransferStatus.PENDING);

        t = stockTransferService.startTransfer(t.getId());
        assertThat(t.getStatus()).isEqualTo(TransferStatus.IN_TRANSIT);

        t = stockTransferService.completeTransfer(t.getId(), null);
        assertThat(t.getStatus()).isEqualTo(TransferStatus.COMPLETED);

        List<Notification> notifs = notificationRepository.findTop20ByOrderByCreatedAtDesc();
        assertThat(notifs).isNotEmpty();
        assertThat(auditLogRepository.findTop100ByOrderByCreatedAtDesc()).isNotEmpty();
    }
}


