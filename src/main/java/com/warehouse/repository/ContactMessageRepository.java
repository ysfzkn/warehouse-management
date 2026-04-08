package com.warehouse.repository;

import com.warehouse.entity.ContactMessage;
import com.warehouse.enums.ContactMessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {

    Page<ContactMessage> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ContactMessage> findByStatusOrderByCreatedAtDesc(ContactMessageStatus status, Pageable pageable);

    long countByStatus(ContactMessageStatus status);
}
