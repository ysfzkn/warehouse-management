package com.warehouse.service;

import com.warehouse.dto.StockTransferFilter;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sevkiyat listesinin sayfalanması.
 *
 * <p>Liste sorgusu {@code items} koleksiyonunu da fetch ediyordu. Bu birleşim yüzünden
 * Hibernate SQL tarafında LIMIT/OFFSET kullanamıyor: bir sevkiyat, kalem sayısı kadar
 * satıra dönüştüğü için "20 satır" "20 sevkiyat" demek değil. Hibernate o durumda sonucun
 * tamamını belleğe çekip sayfalamayı Java tarafında yapıyor ve bunu yalnızca bir uyarı
 * satırıyla (HHH90003004) bildiriyor. Otuz bin sevkiyatlık bir veritabanında tek sayfa
 * isteği 68 saniye sürüyordu.</p>
 *
 * <p>Çözüm iki aşamalı: önce sayfanın kimlikleri gerçek LIMIT/OFFSET ile alınıyor, sonra o
 * kimlikler ilişkileriyle yükleniyor. Buradaki testler çözümün <em>doğruluğunu</em>
 * koruyor; hızlanma ölçümle doğrulandı, ama asıl risk hızda değil — sayfalar arasında
 * kayan ya da tekrar eden kayıtlarda.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransferPagingTest {

    @Autowired private StockTransferService transferService;
    @Autowired private StockTransferRepository transferRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    private static final int TOTAL = 25;
    private Warehouse source;

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setName("Sayfalama Kategori " + System.nanoTime());
        category.setSlug("sayfalama-" + System.nanoTime());
        category = categoryRepository.save(category);

        source = new Warehouse();
        source.setName("Merkez Depo");
        source.setLocation("Niğde");
        source = warehouseRepository.save(source);

        Warehouse destination = new Warehouse();
        destination.setName("Şube Depo");
        destination.setLocation("Niğde");
        destination = warehouseRepository.save(destination);

        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Product p = new Product();
            p.setName("Ürün " + i);
            p.setSku("SYF-" + i + "-" + System.nanoTime());
            p.setSlug("syf-" + i + "-" + System.nanoTime());
            p.setCategory(category);
            products.add(productRepository.save(p));
        }

        // Her sevkiyat birden çok kalem taşıyor: hatanın ortaya çıkması için gereken şart
        // tam olarak bu. Tek kalemli kayıtlarda birleşim satır sayısını değiştirmediği
        // için bellekte sayfalama fark edilmeden doğru sonuç veriyor.
        LocalDateTime base = LocalDateTime.now().minusDays(TOTAL);
        for (int i = 0; i < TOTAL; i++) {
            StockTransfer t = new StockTransfer();
            t.setSourceWarehouse(source);
            t.setDestinationWarehouse(destination);
            t.setTransferType(TransferType.WAREHOUSE);
            t.setStatus(TransferStatus.PENDING);
            t.setTransferDate(base.plusDays(i));
            t.setQuantity(3);
            t.setCreatedBy("admin");
            for (Product p : products) {
                StockTransferItem item = new StockTransferItem();
                item.setProduct(p);
                item.setQuantity(1);
                item.setTransfer(t);
                t.getItems().add(item);
            }
            transferRepository.save(t);
        }
    }

    @Test
    @DisplayName("Sayfalar birbirini tekrar etmiyor ve toplam sabit kalıyor")
    void pagesDoNotOverlap() {
        int size = 10;
        List<Long> seen = new ArrayList<>();
        long total = -1;
        for (int page = 0; page < 3; page++) {
            Page<StockTransfer> result =
                    transferService.getTransfersPaged(new StockTransferFilter(), PageRequest.of(page, size));
            if (total < 0) {
                total = result.getTotalElements();
            }
            assertThat(result.getTotalElements())
                    .as("toplam sayı sayfadan sayfaya değişmemeli")
                    .isEqualTo(total);
            result.getContent().forEach(t -> seen.add(t.getId()));
        }
        assertThat(total).isGreaterThanOrEqualTo(TOTAL);
        assertThat(seen).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Sıralama en yeniden eskiye; ikinci sorgu sırayı bozmuyor")
    void newestFirstSurvivesTheSecondQuery() {
        // İkinci aşama "WHERE id IN (...)" ile yüklüyor ve IN sırayı korumuyor: kayıtlar
        // veritabanının uygun gördüğü düzende dönebilir. Sıra ilk aşamada belirlendiği
        // için çağıran taraf yeniden diziyor; bu test o dizmenin yapıldığını doğruluyor.
        Page<StockTransfer> result =
                transferService.getTransfersPaged(new StockTransferFilter(), PageRequest.of(0, 10));

        List<LocalDateTime> dates = result.getContent().stream()
                .map(StockTransfer::getTransferDate).toList();
        assertThat(dates).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @DisplayName("Kalemler ve depolar yüklenmiş geliyor")
    void relationsComeBackLoaded() {
        // İkinci aşama ilişkileri fetch etmeseydi burada ya LazyInitializationException
        // ya da satır başına bir sorgu (N+1) olurdu; ikisi de sessizce yavaşlatır.
        Page<StockTransfer> result =
                transferService.getTransfersPaged(new StockTransferFilter(), PageRequest.of(0, 10));

        assertThat(result.getContent()).isNotEmpty();
        for (StockTransfer t : result.getContent()) {
            assertThat(t.getItems()).hasSize(3);
            assertThat(t.getItems().get(0).getProduct().getName()).isNotBlank();
            assertThat(t.getSourceWarehouse().getName()).isNotBlank();
        }
    }

    @Test
    @DisplayName("Depo bazlı liste sınırlanıyor ve en yeniden başlıyor")
    void warehouseListingIsCapped() {
        // Sınırsız hâli bir deponun tüm sevkiyatlarını kalemleriyle döndürüyordu: gerçek
        // hacimde 13 MB gövde ve on altı saniye, üstelik istek boyunca bloke bir iş
        // parçacığı.
        List<StockTransfer> limited = transferService.getTransfersByWarehouse(source.getId(), 5);

        assertThat(limited).hasSize(5);
        assertThat(limited.stream().map(StockTransfer::getTransferDate).toList())
                .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(limited.get(0).getItems()).hasSize(3);
    }
}
