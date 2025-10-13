package com.warehouse.repository;

import com.warehouse.entity.Color;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ColorRepository extends JpaRepository<Color, Long> {

    Optional<Color> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT c FROM Color c WHERE c.isActive = true ORDER BY c.name")
    List<Color> findAllActive();

    @Query("SELECT c FROM Color c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%')) AND c.isActive = true")
    List<Color> searchActiveByName(@Param("name") String name);
}


