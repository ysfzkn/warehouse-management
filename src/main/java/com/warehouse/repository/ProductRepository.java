package com.warehouse.repository;

import com.warehouse.entity.Brand;
import com.warehouse.entity.Category;
import com.warehouse.entity.Color;
import com.warehouse.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Override
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    List<Product> findAll();

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    @Query("SELECT p FROM Product p WHERE p.isActive = true ORDER BY p.name")
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    List<Product> findAllActive();

    @Query("SELECT p FROM Product p WHERE p.isActive = true")
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    Page<Product> findAllActive(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.isActive = true ORDER BY p.name")
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    List<Product> findByCategoryAndActive(@Param("category") Category category);

    @Query("SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')) AND p.isActive = true ORDER BY p.name")
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    List<Product> findByNameContainingIgnoreCaseAndActive(@Param("name") String name);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.stocks WHERE p.id = :id")
    Optional<Product> findByIdWithStocks(Long id);

    @Query("SELECT p FROM Product p " +
           "LEFT JOIN FETCH p.category c " +
           "LEFT JOIN FETCH c.parent " +
           "LEFT JOIN FETCH p.brand " +
           "LEFT JOIN FETCH p.color " +
           "WHERE p.id = :id")
    Optional<Product> findByIdWithRelations(@Param("id") Long id);

    /**
     * Active products by id (with card relations fetched) — for recommendation
     * rails and recently-viewed hydration. DB order is unspecified; callers that
     * need a specific order must reorder by the input id list.
     */
    @Query("SELECT p FROM Product p " +
           "LEFT JOIN FETCH p.category c " +
           "LEFT JOIN FETCH c.parent " +
           "LEFT JOIN FETCH p.brand " +
           "LEFT JOIN FETCH p.color " +
           "WHERE p.id IN :ids AND p.isActive = true AND p.ecommerceVisible = true")
    List<Product> findActiveByIdInWithRelations(@Param("ids") List<Long> ids);

    /** Atomic, lock-free view-count bump. Bypasses optimistic versioning by design. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Product p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    /** Popular active product ids in a category (by view count), excluding one product — recommendation fallback. */
    @Query("SELECT p.id FROM Product p " +
           "WHERE p.category.id = :categoryId AND p.isActive = true AND p.ecommerceVisible = true AND p.id <> :excludeId " +
           "ORDER BY p.viewCount DESC, p.id DESC")
    List<Long> findPopularIdsByCategory(@Param("categoryId") Long categoryId,
                                        @Param("excludeId") Long excludeId,
                                        org.springframework.data.domain.Pageable pageable);

    @Query("SELECT p FROM Product p WHERE (:brand IS NULL OR p.brand = :brand) AND (:color IS NULL OR p.color = :color) AND p.isActive = true ORDER BY p.name")
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    List<Product> findActiveByBrandAndColor(@Param("brand") Brand brand, @Param("color") Color color);

    @Query("SELECT p FROM Product p WHERE (:categoryId IS NULL OR p.category.id = :categoryId) AND (:brandId IS NULL OR p.brand.id = :brandId) AND (:colorId IS NULL OR p.color.id = :colorId) AND (:onlyActive = false OR p.isActive = true)")
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    List<Product> findByOptionalFilters(@Param("categoryId") Long categoryId,
                                        @Param("brandId") Long brandId,
                                        @Param("colorId") Long colorId,
                                        @Param("onlyActive") boolean onlyActive);

    // Lightweight version without EntityGraph for bulk operations
    @Query("SELECT p FROM Product p WHERE (:categoryId IS NULL OR p.category.id = :categoryId) AND (:brandId IS NULL OR p.brand.id = :brandId) AND (:colorId IS NULL OR p.color.id = :colorId) AND (:onlyActive = false OR p.isActive = true)")
    List<Product> findByOptionalFiltersLightweight(@Param("categoryId") Long categoryId,
                                                   @Param("brandId") Long brandId,
                                                   @Param("colorId") Long colorId,
                                                   @Param("onlyActive") boolean onlyActive);

    // Bulk update with native SQL for percentage-based price increase
    @Modifying
    @Query(value = """
            UPDATE products 
            SET price = ROUND(CAST(price * (1 + :percentage / 100.0) AS numeric), 2)
            WHERE (:categoryId IS NULL OR category_id = :categoryId)
              AND (:brandId IS NULL OR brand_id = :brandId)
              AND (:colorId IS NULL OR color_id = :colorId)
              AND (CAST(:onlyActive AS boolean) = false OR is_active = CAST(:onlyActive AS boolean))
            """,
           nativeQuery = true)
    int bulkUpdatePriceByPercentage(@Param("percentage") BigDecimal percentage,
                                    @Param("categoryId") Long categoryId,
                                    @Param("brandId") Long brandId,
                                    @Param("colorId") Long colorId,
                                    @Param("onlyActive") boolean onlyActive);

    // Bulk update with native SQL for percentage-based price decrease
    @Modifying
    @Query(value = """
            UPDATE products 
            SET price = CASE 
                WHEN (price * (1 - :percentage / 100.0)) < 0 THEN 0 
                ELSE ROUND(CAST(price * (1 - :percentage / 100.0) AS numeric), 2) 
            END
            WHERE (:categoryId IS NULL OR category_id = :categoryId)
              AND (:brandId IS NULL OR brand_id = :brandId)
              AND (:colorId IS NULL OR color_id = :colorId)
              AND (CAST(:onlyActive AS boolean) = false OR is_active = CAST(:onlyActive AS boolean))
            """,
           nativeQuery = true)
    int bulkUpdatePriceByPercentageDecrease(@Param("percentage") BigDecimal percentage,
                                            @Param("categoryId") Long categoryId,
                                            @Param("brandId") Long brandId,
                                            @Param("colorId") Long colorId,
                                            @Param("onlyActive") boolean onlyActive);

    // Bulk update with native SQL for fixed amount increase
    @Modifying
    @Query(value = """
            UPDATE products 
            SET price = ROUND(CAST(price + :amount AS numeric), 2)
            WHERE (:categoryId IS NULL OR category_id = :categoryId)
              AND (:brandId IS NULL OR brand_id = :brandId)
              AND (:colorId IS NULL OR color_id = :colorId)
              AND (CAST(:onlyActive AS boolean) = false OR is_active = CAST(:onlyActive AS boolean))
            """,
           nativeQuery = true)
    int bulkUpdatePriceByAmount(@Param("amount") BigDecimal amount,
                                @Param("categoryId") Long categoryId,
                                @Param("brandId") Long brandId,
                                @Param("colorId") Long colorId,
                                @Param("onlyActive") boolean onlyActive);

    // Bulk update with native SQL for fixed amount decrease
    @Modifying
    @Query(value = """
            UPDATE products 
            SET price = CASE 
                WHEN (price - :amount) < 0 THEN 0 
                ELSE ROUND(CAST(price - :amount AS numeric), 2) 
            END
            WHERE (:categoryId IS NULL OR category_id = :categoryId)
              AND (:brandId IS NULL OR brand_id = :brandId)
              AND (:colorId IS NULL OR color_id = :colorId)
              AND (CAST(:onlyActive AS boolean) = false OR is_active = CAST(:onlyActive AS boolean))
            """,
           nativeQuery = true)
    int bulkUpdatePriceByAmountDecrease(@Param("amount") BigDecimal amount,
                                        @Param("categoryId") Long categoryId,
                                        @Param("brandId") Long brandId,
                                        @Param("colorId") Long colorId,
                                        @Param("onlyActive") boolean onlyActive);

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN p.category c
        LEFT JOIN c.parent cp
        LEFT JOIN p.brand b
        LEFT JOIN p.color col
        WHERE (:search IS NULL
               OR LOWER(p.name)  LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(p.sku)   LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(c.name)  LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(cp.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(b.name)  LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
          AND (:categoryId IS NULL OR c.id = :categoryId OR cp.id = :categoryId)
          AND (:brandId IS NULL OR b.id = :brandId)
          AND (:colorId IS NULL OR col.id = :colorId)
    """)
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    Page<Product> findByFilters(@Param("search") String search,
                                @Param("categoryId") Long categoryId,
                                @Param("brandId") Long brandId,
                                @Param("colorId") Long colorId,
                                Pageable pageable);

    /** Same as {@link #findByFilters} but also filters by product type (SIMPLE/BUNDLE). */
    @Query("""
        SELECT p FROM Product p
        LEFT JOIN p.category c
        LEFT JOIN c.parent cp
        LEFT JOIN p.brand b
        LEFT JOIN p.color col
        WHERE (:search IS NULL
               OR LOWER(p.name)  LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(p.sku)   LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(c.name)  LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(cp.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(b.name)  LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
          AND (:categoryId IS NULL OR c.id = :categoryId OR cp.id = :categoryId)
          AND (:brandId IS NULL OR b.id = :brandId)
          AND (:colorId IS NULL OR col.id = :colorId)
          AND (:productType IS NULL OR p.productType = :productType)
    """)
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    Page<Product> findByFilters(@Param("search") String search,
                                @Param("categoryId") Long categoryId,
                                @Param("brandId") Long brandId,
                                @Param("colorId") Long colorId,
                                @Param("productType") com.warehouse.entity.ProductType productType,
                                Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN p.category c LEFT JOIN c.parent cp LEFT JOIN p.brand b LEFT JOIN p.color col
        WHERE (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(p.sku) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
          AND (:categoryId IS NULL OR c.id = :categoryId OR cp.id = :categoryId)
          AND (:brandId IS NULL OR b.id = :brandId)
          AND (:colorId IS NULL OR col.id = :colorId)
          AND (:productType IS NULL OR p.productType = :productType)
          AND (:ecommerceVisible IS NULL OR p.ecommerceVisible = :ecommerceVisible)
          AND (:hasImage IS NULL OR
               (:hasImage = true AND EXISTS (SELECT 1 FROM ProductImage pi WHERE pi.product = p AND pi.slot IS NULL)) OR
               (:hasImage = false AND NOT EXISTS (SELECT 1 FROM ProductImage pi WHERE pi.product = p AND pi.slot IS NULL)))
    """)
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    Page<Product> findByAdminFilters(@Param("search") String search,
                                     @Param("categoryId") Long categoryId,
                                     @Param("brandId") Long brandId,
                                     @Param("colorId") Long colorId,
                                     @Param("productType") com.warehouse.entity.ProductType productType,
                                     @Param("ecommerceVisible") Boolean ecommerceVisible,
                                     @Param("hasImage") Boolean hasImage,
                                     Pageable pageable);

    @Modifying
    @Query("UPDATE Product p SET p.ecommerceVisible = :visible WHERE p.id IN :ids")
    int updateEcommerceVisibility(@Param("ids") List<Long> ids, @Param("visible") boolean visible);

    // Color variants — products sharing a variant_group_id are the same product in
    // different colors. Storefront wants only the active members (with relations for
    // color/image rendering); the admin reconcile path wants every member regardless
    // of active flag.
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT p FROM Product p WHERE p.variantGroupId = :variantGroupId AND p.isActive = true " +
           "AND p.ecommerceVisible = true ORDER BY p.id ASC")
    List<Product> findByVariantGroupIdAndIsActiveTrueOrderByIdAsc(@Param("variantGroupId") Long variantGroupId);

    List<Product> findByVariantGroupId(Long variantGroupId);

    // Batch: active members of many variant groups at once (storefront product-card swatches).
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT p FROM Product p WHERE p.variantGroupId IN :variantGroupIds AND p.isActive = true AND p.ecommerceVisible = true")
    List<Product> findByVariantGroupIdInAndIsActiveTrue(@Param("variantGroupIds") java.util.Collection<Long> variantGroupIds);

    // E-commerce storefront queries
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT p FROM Product p WHERE p.slug = :slug AND p.isActive = true AND p.ecommerceVisible = true")
    Optional<Product> findBySlug(@Param("slug") String slug);

    @Query("SELECT p FROM Product p WHERE p.slug = :slug")
    Optional<Product> findAnyBySlug(@Param("slug") String slug);

    /**
     * Store search — searches across product name + SKU + category + brand + sub-brand
     * + color + short description. The tsvector (V52 idx_products_search_tsv) still cannot
     * be used directly from JPQL; a broad LIKE pattern gives adequate performance up to
     * 1000+ products in the catalog. For a larger catalog, switch to a native query.
     */
    @Query("""
        SELECT p FROM Product p
        LEFT JOIN p.category c
        LEFT JOIN c.parent cp
        LEFT JOIN p.brand b
        LEFT JOIN p.color col
        WHERE p.isActive = true AND p.ecommerceVisible = true
          AND (:search IS NULL
               OR LOWER(p.name)             LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(p.sku)              LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(p.shortDescription) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(c.name)             LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(cp.name)            LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(b.name)             LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(col.name)           LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
          AND (:categoryId IS NULL OR c.id = :categoryId OR cp.id = :categoryId)
          AND (:brandId IS NULL OR b.id = :brandId)
          AND (:colorId IS NULL OR col.id = :colorId)
          AND (:productType IS NULL OR p.productType = :productType)
        ORDER BY
          CASE WHEN EXISTS (
                 SELECT 1 FROM ProductImage pi
                 WHERE pi.product = p AND pi.slot IS NULL
                   AND (pi.aiRole IS NULL OR pi.aiRole <> 'COVER_INPUT')
               ) THEN 0 ELSE 1 END,
          CASE WHEN p.productType = com.warehouse.entity.ProductType.BUNDLE
                    OR (SELECT COALESCE(SUM(COALESCE(s.quantity, 0)
                                            - COALESCE(s.reservedQuantity, 0)
                                            - COALESCE(s.consignedQuantity, 0)), 0)
                        FROM Stock s WHERE s.product = p) > 0
               THEN 0 ELSE 1 END
    """)
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    Page<Product> findActiveByFilters(@Param("search") String search,
                                      @Param("categoryId") Long categoryId,
                                      @Param("brandId") Long brandId,
                                      @Param("colorId") Long colorId,
                                      @Param("productType") com.warehouse.entity.ProductType productType,
                                      Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN p.category c
        LEFT JOIN c.parent cp
        LEFT JOIN p.brand b
        LEFT JOIN p.color col
        WHERE p.isActive = true AND p.ecommerceVisible = true
          AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
          AND (:categoryId IS NULL OR c.id = :categoryId OR cp.id = :categoryId)
          AND (:brandIds IS NULL OR b.id IN :brandIds)
          AND (:colorIds IS NULL OR col.id IN :colorIds)
        ORDER BY
          CASE WHEN EXISTS (
                 SELECT 1 FROM ProductImage pi
                 WHERE pi.product = p AND pi.slot IS NULL
                   AND (pi.aiRole IS NULL OR pi.aiRole <> 'COVER_INPUT')
               ) THEN 0 ELSE 1 END,
          CASE WHEN p.productType = com.warehouse.entity.ProductType.BUNDLE
                    OR (SELECT COALESCE(SUM(COALESCE(s.quantity, 0)
                                            - COALESCE(s.reservedQuantity, 0)
                                            - COALESCE(s.consignedQuantity, 0)), 0)
                        FROM Stock s WHERE s.product = p) > 0
               THEN 0 ELSE 1 END
    """)
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    Page<Product> findActiveByMultiFilters(@Param("search") String search,
                                           @Param("categoryId") Long categoryId,
                                           @Param("brandIds") java.util.List<Long> brandIds,
                                           @Param("colorIds") java.util.List<Long> colorIds,
                                           Pageable pageable);
}
