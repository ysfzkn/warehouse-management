package com.warehouse.repository;

import com.warehouse.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT c FROM Category c WHERE c.isActive = true ORDER BY c.name")
    List<Category> findAllActive();

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.products WHERE c.id = :id")
    Optional<Category> findByIdWithProducts(Long id);

    @Query("SELECT c FROM Category c WHERE c.parent IS NULL ORDER BY c.name")
    List<Category> findTopLevelCategories();

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.children WHERE c.parent IS NULL ORDER BY c.name")
    List<Category> findAllWithChildren();

    @Query("SELECT p.category.id AS categoryId, COUNT(p.id) AS productCount FROM Product p WHERE p.category IS NOT NULL GROUP BY p.category.id")
    List<CategoryProductCount> fetchCategoryProductCounts();

    interface CategoryProductCount {
        Long getCategoryId();
        Long getProductCount();
    }
}
