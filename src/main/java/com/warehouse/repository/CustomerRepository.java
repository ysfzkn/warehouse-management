package com.warehouse.repository;

import com.warehouse.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {
    Optional<Customer> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<Customer> findByEmailVerifyToken(String token);
    Optional<Customer> findByPasswordResetToken(String token);
    Optional<Customer> findFirstByPhone(String phone);
}
