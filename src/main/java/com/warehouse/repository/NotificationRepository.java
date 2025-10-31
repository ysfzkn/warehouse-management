package com.warehouse.repository;

import com.warehouse.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop20ByOrderByCreatedAtDesc();

    List<Notification> findByReadFalseOrderByCreatedAtDesc();

    List<Notification> findByTargetUserIdOrTargetUserIdIsNullOrderByCreatedAtDesc(Long targetUserId);

    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);
}


