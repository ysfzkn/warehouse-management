package com.warehouse.repository;

import com.warehouse.entity.Brand;
import com.warehouse.entity.Category;
import com.warehouse.entity.Color;
import com.warehouse.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Query("SELECT p FROM Product p WHERE (:brand IS NULL OR p.brand = :brand) AND (:color IS NULL OR p.color = :color) AND p.isActive = true ORDER BY p.name")
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    List<Product> findActiveByBrandAndColor(@Param("brand") Brand brand, @Param("color") Color color);

    @Query("SELECT p FROM Product p WHERE (:categoryId IS NULL OR p.category.id = :categoryId) AND (:brandId IS NULL OR p.brand.id = :brandId) AND (:colorId IS NULL OR p.color.id = :colorId) AND (:onlyActive = false OR p.isActive = true)")
    @EntityGraph(value = Product.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    List<Product> findByOptionalFilters(@Param("categoryId") Long categoryId,
                                        @Param("brandId") Long brandId,
                                        @Param("colorId") Long colorId,
                                        @Param("onlyActive") boolean onlyActive);
}
