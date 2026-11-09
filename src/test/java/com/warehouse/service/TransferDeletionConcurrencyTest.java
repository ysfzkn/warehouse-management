package com.warehouse.service;

import com.warehouse.dto.ServiceHandoverRequest;
import com.warehouse.entity.Category;
import com.warehouse.entity.Product;
import com.warehouse.entity.Stock;
import com.warehouse.entity.Warehouse;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Çift tıklama ya da iki yöneticinin aynı makbuzu aynı anda silmesi.
 *
 * <p>Sınıf bilerek {@code @Transactional} değil: her silme kendi işleminde, gerçekten
 * paralel koşmalı. İki işlem de kaydı "henüz silinmemiş" görüp stoğu ayrı ayrı geri
 * eklerse depo, çıkmamış bir malı fazladan sayar.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class TransferDeletionConcurrencyTest {

    private static final int ON_HAND = 40;

    @Autowired private ServiceHandoverService handoverService;
    @Autowired private StockTransferService transferService;
    @Autowired private StockRepository stockRepository;
    @Autowired private StockTransferRepository transferRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    @MockBean private AdminSecurityService adminSecurityService;

    private final List<Runnable> cleanup = new ArrayList<>();

    @AfterEach
    void tearDown() {
        loginAsAdmin();
        cleanup.forEach(Runnable::run);
        SecurityContextHolder.clearContext();
    }

    private static void loginAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "pw",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @Test
    @DisplayName("Aynı makbuz aynı anda iki kez silinirse stok yalnızca bir kez geri eklenir")
    void concurrentDeletesRestoreStockOnce() throws Exception {
        loginAsAdmin();
        Category category = new Category();
        category.setName("Eşzamanlı Kategori");
        category.setSlug("eszamanli-" + System.nanoTime());
        Category savedCategory = categoryRepository.save(category);

        Product product = new Product();
        product.setName("Fırın");
        product.setSku("ESZ-" + System.nanoTime());
        product.setSlug("eszamanli-firin-" + System.nanoTime());
        product.setCategory(savedCategory);
        Product savedProduct = productRepository.save(product);

        Warehouse warehouse = new Warehouse();
        warehouse.setName("Eşzamanlı Depo " + System.nanoTime());
        warehouse.setLocation("Niğde");
        Warehouse savedWarehouse = warehouseRepository.save(warehouse);

        Stock stock = new Stock();
        stock.setProduct(savedProduct);
        stock.setWarehouse(savedWarehouse);
        stock.setQuantity(ON_HAND);
        Stock savedStock = stockRepository.save(stock);

        ServiceHandoverRequest request = new ServiceHandoverRequest();
        request.setSourceWarehouseId(savedWarehouse.getId());
        request.setHandoverToName("Servis");
        request.setCustomerFullName("Ayşe Gültekin");
        request.setCustomerPhone("05559876543");
        request.setCustomerAddress("Kale Mah. Niğde");
        ServiceHandoverRequest.Item item = new ServiceHandoverRequest.Item();
        item.setProductId(savedProduct.getId());
        item.setQuantity(3);
        request.setItems(List.of(item));
        Long transferId = handoverService.handOver(request, "admin").transfer().getId();

        cleanup.add(() -> {
            transferRepository.findById(transferId).ifPresent(transferRepository::delete);
            stockRepository.deleteById(savedStock.getId());
            warehouseRepository.deleteById(savedWarehouse.getId());
            productRepository.deleteById(savedProduct.getId());
            categoryRepository.deleteById(savedCategory.getId());
        });
        assertThat(stockRepository.findById(savedStock.getId()).orElseThrow().getQuantity())
                .isEqualTo(ON_HAND - 3);

        int attempts = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                loginAsAdmin();
                start.await();
                try {
                    return transferService.deleteTransfer(transferId, "kod").isDeleted();
                } catch (Exception e) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get(30, TimeUnit.SECONDS)) succeeded++;
        }
        pool.shutdown();

        assertThat(succeeded).as("yalnızca bir silme başarılı olmalı").isEqualTo(1);
        Stock after = stockRepository.findById(savedStock.getId()).orElseThrow();
        assertThat(after.getQuantity()).as("stok bir kez geri eklenmeli").isEqualTo(ON_HAND);
        assertThat(after.getReservedQuantity()).isZero();
        assertThat(transferRepository.findById(transferId)).isEmpty();
    }
}
