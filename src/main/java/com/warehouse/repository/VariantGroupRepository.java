package com.warehouse.repository;

import com.warehouse.entity.VariantGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Mints new color-variant group ids. See {@link com.warehouse.entity.VariantGroup}. */
@Repository
public interface VariantGroupRepository extends JpaRepository<VariantGroup, Long> {
}
