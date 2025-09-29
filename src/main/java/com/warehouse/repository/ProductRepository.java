package com.warehouse.repository;

import com.warehouse.entity.Product;
import com.warehouse.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    @Query("SELECT p FROM Product p WHERE p.isActive = true ORDER BY p.name")
    List<Product> findAllActive();

    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.isActive = true ORDER BY p.name")
    List<Product> findByCategoryAndActive(@Param("category") Category category);

    @Query("SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')) AND p.isActive = true")
    List<Product> findByNameContainingIgnoreCaseAndActive(@Param("name") String name);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.stocks WHERE p.id = :id")
    Optional<Product> findByIdWithStocks(Long id);
}
