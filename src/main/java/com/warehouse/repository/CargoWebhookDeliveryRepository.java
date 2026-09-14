package com.warehouse.repository;

import com.warehouse.entity.CargoWebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CargoWebhookDeliveryRepository extends JpaRepository<CargoWebhookDelivery, Long> {

    Optional<CargoWebhookDelivery> findByIdempotencyKey(String idempotencyKey);

    /** Retention: the raw log is for debugging, not forever. */
    @Modifying
    @Query("DELETE FROM CargoWebhookDelivery d WHERE d.receivedAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
