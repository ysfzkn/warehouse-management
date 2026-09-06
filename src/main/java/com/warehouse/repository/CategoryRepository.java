package com.warehouse.repository;

import com.warehouse.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT c FROM Category c WHERE c.isActive = true ORDER BY c.name")
    List<Category> findAllActive();

    @Query("SELECT c FROM Category c WHERE c.isActive = true")
    Page<Category> findAllActive(Pageable pageable);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.products WHERE c.id = :id")
    Optional<Category> findByIdWithProducts(Long id);

    @Query("SELECT c FROM Category c WHERE c.parent IS NULL ORDER BY c.name")
    List<Category> findTopLevelCategories();

    @Query("SELECT c FROM Category c WHERE c.parent IS NULL")
    Page<Category> findTopLevelCategories(Pageable pageable);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.children WHERE c.parent IS NULL ORDER BY c.name")
    List<Category> findAllWithChildren();

    @Query("SELECT p.category.id AS categoryId, COUNT(p.id) AS productCount FROM Product p WHERE p.category IS NOT NULL GROUP BY p.category.id")
    List<CategoryProductCount> fetchCategoryProductCounts();

    /**
     * Vitrinde görünen ürünlerin kategori bazında sayısı.
     *
     * <p>Yukarıdaki sayımdan ayrı durmasının sebebi, müşteriye gösterilen rakamın
     * tıkladığında karşısına çıkacak listeyle birebir aynı olması gerektiği. Mağaza
     * listesi {@code ProductRepository.findActiveByFilters} ile geliyor ve oradaki
     * görünürlük kuralı isActive + ecommerceVisible; yönetim panelindeki sayım ise
     * pasif ve vitrine kapalı ürünleri de kapsıyor. İkisi karıştırılırsa çipte "12"
     * yazan kategori üç ürünlük bir sayfa açar.</p>
     *
     * <p>Sayı doğrudan o kategoriye bağlı ürünleri veriyor; bir üst kategorinin toplamı
     * çağıran tarafta çocuklarınkiyle toplanarak bulunuyor, çünkü mağaza listesi de üst
     * kategoriye tıklandığında alt kategori ürünlerini gösteriyor.</p>
     */
    @Query("""
        SELECT p.category.id AS categoryId, COUNT(p.id) AS productCount
          FROM Product p
         WHERE p.category IS NOT NULL
           AND p.isActive = true
           AND p.ecommerceVisible = true
         GROUP BY p.category.id
    """)
    List<CategoryProductCount> fetchStorefrontCategoryProductCounts();

    @Query("SELECT c FROM Category c JOIN FETCH c.parent WHERE c.parent.id IN :parentIds ORDER BY c.parent.id, c.name")
    List<Category> findByParentIdIn(@org.springframework.data.repository.query.Param("parentIds") Collection<Long> parentIds);

    interface CategoryProductCount {
        Long getCategoryId();
        Long getProductCount();
    }

    // E-commerce storefront queries
    Optional<Category> findBySlug(String slug);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.children WHERE c.parent IS NULL AND c.isActive = true ORDER BY c.sortOrder, c.name")
    List<Category> findActiveRootCategoriesWithChildren();
}
