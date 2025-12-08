package com.warehouse.repository;

import com.warehouse.entity.AdminSecurityCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminSecurityCodeRepository extends JpaRepository<AdminSecurityCode, Long> {

    Optional<AdminSecurityCode> findTopByOrderByIdAsc();
}

