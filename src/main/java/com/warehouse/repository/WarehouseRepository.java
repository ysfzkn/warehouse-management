package com.warehouse.repository;

import com.warehouse.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT w FROM Warehouse w WHERE w.isActive = true ORDER BY w.name")
    List<Warehouse> findAllActive();

    @Query("SELECT w FROM Warehouse w LEFT JOIN FETCH w.stocks WHERE w.id = :id")
    Optional<Warehouse> findByIdWithStocks(Long id);
}
