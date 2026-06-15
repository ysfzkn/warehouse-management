package com.warehouse.repository;

import com.warehouse.entity.ReturnRequest;
import com.warehouse.enums.ReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    Page<ReturnRequest> findByCustomerId(Long customerId, Pageable pageable);
    Page<ReturnRequest> findByStatus(ReturnStatus status, Pageable pageable);

    Optional<ReturnRequest> findByReturnNumber(String returnNumber);

    Page<ReturnRequest> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    /** Open (non-terminal) returns for an order — to block duplicate requests. */
    List<ReturnRequest> findByOrderIdAndStatusIn(Long orderId, List<ReturnStatus> statuses);

    long countByStatus(ReturnStatus status);
}
