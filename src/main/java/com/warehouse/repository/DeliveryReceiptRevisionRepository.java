package com.warehouse.repository;

import com.warehouse.entity.DeliveryReceiptRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryReceiptRevisionRepository extends JpaRepository<DeliveryReceiptRevision, Long> {

    List<DeliveryReceiptRevision> findByReceiptIdOrderByEditedAtDescIdDesc(Long receiptId);
}
