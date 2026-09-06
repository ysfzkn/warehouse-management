package com.warehouse.controller;

import com.warehouse.controller.store.StoreCategoryController;
import com.warehouse.dto.store.StoreCategoryDto;
import com.warehouse.entity.Category;
import com.warehouse.entity.Product;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kategori çiplerindeki ürün sayısı.
 *
 * <p>Bu rakam ana sayfada müşteriye gösteriliyor ve tıklandığında açılan listeyle birebir
 * tutmak zorunda. Tutmadığında hata veren bir şey yok — çipte "5" yazar, sayfada iki ürün
 * çıkar ve mağaza güvenilmez görünür. Sayımın kaynağı bu yüzden yönetim panelininkinden
 * ayrı: orada pasif ve vitrine kapalı ürünler de sayılıyor.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StoreCategoryCountTest {

    @Autowired private StoreCategoryController controller;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    private Category root;

    @BeforeEach
    void setUp() {
        root = category("Beyaz Eşya", null);
    }

    private Category category(String name, Category parent) {
        Category category = new Category();
        category.setName(name + " " + System.nanoTime());
        category.setSlug(name.toLowerCase().replace(" ", "-") + "-" + System.nanoTime());
        category.setActive(true);
        category.setShowInMenu(true);
        category.setParent(parent);
        return categoryRepository.save(category);
    }

    private void product(Category category, boolean active, boolean visibleInStore) {
        Product product = new Product();
        product.setName("Ürün " + System.nanoTime());
        product.setSku("SKU-" + System.nanoTime());
        product.setSlug("sku-" + System.nanoTime());
        product.setCategory(category);
        product.setPrice(BigDecimal.valueOf(4999));
        product.setActive(active);
        product.setEcommerceVisible(visibleInStore);
        productRepository.save(product);
    }

    private StoreCategoryDto find(Long id) {
        // Testin kurduğu nesneler ile sunucunun okuduğu nesneler aynı olmasın: test
        // metodu bir alt kategoriyi kaydettiğinde üst kategorinin bellekteki children
        // listesi boş kalıyor ve kontrolcü onu görmüyordu. Üretimde her istek kayıtları
        // veritabanından taze okuyor; burada da aynı şey olsun.
        entityManager.flush();
        entityManager.clear();

        List<StoreCategoryDto> tree = controller.getCategoryTree().getBody();
        assertThat(tree).isNotNull();
        return tree.stream().filter(c -> c.getId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Sayı yalnızca vitrinde görünen ürünleri kapsar")
    void countsOnlyStorefrontVisibleProducts() {
        product(root, true, true);
        product(root, true, true);
        product(root, false, true);   // pasif
        product(root, true, false);   // vitrine kapalı

        assertThat(find(root.getId()).getProductCount())
                .as("pasif ve vitrine kapalı ürünler müşteriye gösterilen listede yok")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Üst kategorinin sayısı alt kategorilerini de kapsar")
    void parentCountIncludesChildren() {
        // Mağaza listesi üst kategoriye tıklandığında alt kategori ürünlerini de
        // gösteriyor (findActiveByFilters: "c.id = :categoryId OR cp.id = :categoryId").
        // Çipte yalnızca doğrudan bağlı ürünler sayılsaydı, ürünlerini alt kategorilere
        // dağıtmış bir üst kategori "0" yazardı ve dolu bir sayfa açardı.
        Category child = category("Buzdolabı", root);
        categoryRepository.flush();

        product(root, true, true);
        product(child, true, true);
        product(child, true, true);

        assertThat(find(root.getId()).getProductCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Ürünü olmayan kategori sıfır döner, listeden düşmez")
    void emptyCategoryReportsZero() {
        // Sıfır dönmesi önemli: ön yüz rozeti sıfırda basmıyor ve sıralamada bu
        // kategorileri dibe indiriyor. Kategori listeden silinmiyor — yeni açılmış,
        // henüz ürün girilmemiş bir kategori yönetici için kaybolmamalı.
        assertThat(find(root.getId()).getProductCount()).isZero();
    }
}
