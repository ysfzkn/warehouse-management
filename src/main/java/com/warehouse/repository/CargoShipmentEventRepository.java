package com.warehouse.repository;

import com.warehouse.entity.CargoShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CargoShipmentEventRepository extends JpaRepository<CargoShipmentEvent, Long> {

    /** An order's cargo history, newest movement first. */
    List<CargoShipmentEvent> findByOrderIdOrderByOccurredAtDescIdDesc(Long orderId);

    /** Dedupe guard for movements that carry a timestamp. */
    boolean existsByOrderIdAndStatusCodeAndOccurredAt(Long orderId, String statusCode, LocalDateTime occurredAt);

    /** Dedupe guard for status changes with no carrier timestamp. */
    boolean existsByOrderIdAndStatusCodeAndOccurredAtIsNull(Long orderId, String statusCode);
}
