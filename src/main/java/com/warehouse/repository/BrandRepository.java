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

    @Query("SELECT b FROM Brand b WHERE b.isActive = true ORDER BY b.name")
    List<Brand> findAllActive();

    @Query("SELECT b FROM Brand b WHERE LOWER(b.name) LIKE LOWER(CONCAT('%', :name, '%')) AND b.isActive = true")
    List<Brand> searchActiveByName(@Param("name") String name);
}


