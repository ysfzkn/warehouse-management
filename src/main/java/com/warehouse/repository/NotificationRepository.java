package com.warehouse.repository;

import com.warehouse.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    List<Notification> findTop20ByOrderByCreatedAtDesc();

    List<Notification> findByReadFalseOrderByCreatedAtDesc();

    List<Notification> findByTargetUserIdOrTargetUserIdIsNullOrderByCreatedAtDesc(Long targetUserId);

    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Returns the total number of unread notifications.
     */
    long countByReadFalse();

    /** Unread count for a single workspace domain (WMS / ECOM). */
    long countByReadFalseAndDomain(String domain);
}


