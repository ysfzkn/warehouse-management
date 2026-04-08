package com.warehouse.repository;

import com.warehouse.entity.CargoProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CargoProviderRepository extends JpaRepository<CargoProvider, Long> {
    List<CargoProvider> findByActiveTrueOrderBySortOrderAsc();
    Optional<CargoProvider> findByCode(String code);
    boolean existsByCode(String code);
}
