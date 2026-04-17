package com.warehouse.repository;

import com.warehouse.entity.NotificationPreference;
import com.warehouse.service.notification.NotificationChannelType;
import com.warehouse.service.notification.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByCustomerIdAndChannelAndType(
            Long customerId, NotificationChannelType channel, NotificationType type);

    List<NotificationPreference> findByCustomerId(Long customerId);
}
