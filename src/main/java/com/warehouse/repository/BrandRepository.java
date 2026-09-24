package com.warehouse.repository;

import com.warehouse.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findByName(String name);

    boolean existsByName(String name);

    boolean existsBySlug(String slug);

    @Query("SELECT b FROM Brand b WHERE b.isActive = true ORDER BY b.name")
    List<Brand> findAllActive();

    /**
     * Brands the storefront can actually show a product for. The brands table also holds
     * model codes imported as brands ("32PA225EG") with nothing behind them; listing those
     * in the filter or the sitemap produces empty pages.
     */
    @Query("SELECT b FROM Brand b WHERE b.isActive = true AND EXISTS (" +
           "SELECT 1 FROM Product p WHERE p.brand = b AND p.isActive = true AND p.ecommerceVisible = true) " +
           "ORDER BY b.name")
    List<Brand> findActiveWithStorefrontProducts();

    @Query("SELECT b FROM Brand b WHERE b.slug = :slug AND b.isActive = true")
    Optional<Brand> findActiveBySlug(@Param("slug") String slug);

    @Query("SELECT b FROM Brand b WHERE LOWER(b.name) LIKE LOWER(CONCAT('%', :name, '%')) AND b.isActive = true")
    List<Brand> searchActiveByName(@Param("name") String name);
}


