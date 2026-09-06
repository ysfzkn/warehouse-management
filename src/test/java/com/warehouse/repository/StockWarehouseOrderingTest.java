package com.warehouse.repository;

import com.warehouse.entity.Category;
import com.warehouse.entity.Product;
import com.warehouse.entity.Stock;
import com.warehouse.entity.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The order a warehouse's stock comes back in.
 *
 * <p>Every product picker in the admin panel reads this list and the operator scans it by eye.
 * Rows with nothing left cannot be selected, so anything still shippable has to come first —
 * otherwise picking one item means scrolling past dead entries to reach it.</p>
 *
 * <p>Ordering is the kind of thing that survives a refactor by accident and disappears by
 * accident too, because nothing throws when it goes.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class StockWarehouseOrderingTest {

    @Autowired private StockRepository stockRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Warehouse warehouse;
    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Sıralama Kategori");
        category.setSlug("siralama-" + System.nanoTime());
        category = categoryRepository.save(category);

        warehouse = new Warehouse();
        warehouse.setName("Merkez Depo");
        warehouse.setLocation("Niğde");
        warehouse = warehouseRepository.save(warehouse);
    }

    private void stock(String name, int quantity, int reserved) {
        Product product = new Product();
        product.setName(name);
        product.setSku(name.replaceAll("\\s", "").toUpperCase() + "-" + System.nanoTime());
        product.setSlug(name.toLowerCase().replaceAll("\\s", "-") + "-" + System.nanoTime());
        product.setCategory(category);
        product = productRepository.save(product);

        Stock s = new Stock();
        s.setProduct(product);
        s.setWarehouse(warehouse);
        s.setQuantity(quantity);
        s.setReservedQuantity(reserved);
        stockRepository.save(s);
    }

    private List<String> names() {
        return stockRepository.findByWarehouse(warehouse).stream()
                .map(s -> s.getProduct().getName())
                .toList();
    }

    @Test
    @DisplayName("Stoğu olanlar üstte, biteni altta; her grup kendi içinde alfabetik")
    void availableStockComesFirst() {
        // Alfabetik olarak araya karışacak şekilde seçildi: sadece isme göre sıralansaydı
        // "Ampul" en üstte, "Zımba" en altta olurdu.
        stock("Ampul", 0, 0);
        stock("Buzdolabı", 5, 0);
        stock("Çamaşır Makinesi", 0, 0);
        stock("Davlumbaz", 2, 0);
        stock("Zımba", 7, 0);

        assertThat(names()).containsExactly(
                "Buzdolabı", "Davlumbaz", "Zımba",   // stoğu olanlar, alfabetik
                "Ampul", "Çamaşır Makinesi");        // biteni, alfabetik
    }

    @Test
    @DisplayName("Tamamı rezerve edilmiş ürün de aşağı iner")
    void fullyReservedCountsAsUnavailable() {
        // Seçicideki "Kalan" sütunu stok − rezerve gösteriyor. Gruplama başka bir formül
        // kullansaydı, ekranda "Kalan 0" yazan bir satır üst grupta durabilirdi.
        stock("Ankastre Fırın", 4, 4);
        stock("Buzdolabı", 4, 1);

        assertThat(names()).containsExactly("Buzdolabı", "Ankastre Fırın");
    }

    @Test
    @DisplayName("Rezerve alanı boş olan eski kayıtlar stoklu sayılır")
    void nullReservedIsTreatedAsZero() {
        // reserved_quantity kolonu nullable; V1'den kalma kayıtlarda boş olabiliyor.
        // COALESCE olmasaydı karşılaştırma NULL döner ve satır sessizce alt gruba düşerdi.
        Product product = new Product();
        product.setName("Eski Kayıt");
        product.setSku("ESKI-" + System.nanoTime());
        product.setSlug("eski-" + System.nanoTime());
        product.setCategory(category);
        product = productRepository.save(product);

        Stock legacy = new Stock();
        legacy.setProduct(product);
        legacy.setWarehouse(warehouse);
        legacy.setQuantity(3);
        legacy.setReservedQuantity(null);
        stockRepository.save(legacy);

        stock("Zımba", 0, 0);

        assertThat(names()).containsExactly("Eski Kayıt", "Zımba");
    }
}
