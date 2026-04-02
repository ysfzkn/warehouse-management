package com.warehouse.repository;

import com.warehouse.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);
    Optional<PaymentTransaction> findByConversationId(String conversationId);
    List<PaymentTransaction> findByOrderId(Long orderId);

    Optional<PaymentTransaction> findByToken(String token);

    @Query("SELECT p FROM PaymentTransaction p WHERE p.status IN :statuses AND p.expiresAt < :threshold")
    List<PaymentTransaction> findExpiredTransactions(@Param("statuses") List<com.warehouse.enums.PaymentStatus> statuses,
                                                     @Param("threshold") java.time.LocalDateTime threshold);

    @Query("SELECT p FROM PaymentTransaction p WHERE p.order.id = :orderId AND p.status = :status")
    Optional<PaymentTransaction> findByOrderIdAndStatus(@Param("orderId") Long orderId,
                                                         @Param("status") com.warehouse.enums.PaymentStatus status);

    org.springframework.data.domain.Page<PaymentTransaction> findAll(org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<PaymentTransaction> findByStatus(com.warehouse.enums.PaymentStatus status,
                                                                           org.springframework.data.domain.Pageable pageable);
}
