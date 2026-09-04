package com.warehouse.repository;

import com.warehouse.entity.DeliveryReceiptAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryReceiptAttachmentRepository extends JpaRepository<DeliveryReceiptAttachment, Long> {

    List<DeliveryReceiptAttachment> findByReceiptIdOrderByUploadedAtAsc(Long receiptId);

    long countByReceiptId(Long receiptId);
}
