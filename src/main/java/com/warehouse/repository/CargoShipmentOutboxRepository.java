package com.warehouse.repository;

import com.warehouse.entity.CargoShipmentOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CargoShipmentOutboxRepository extends JpaRepository<CargoShipmentOutbox, Long> {

    Optional<CargoShipmentOutbox> findByOrderId(Long orderId);

    /** Entries whose next attempt is due, oldest first. */
    @Query("SELECT o FROM CargoShipmentOutbox o WHERE o.status = :status AND o.nextAttemptAt <= :now " +
           "ORDER BY o.nextAttemptAt ASC")
    List<CargoShipmentOutbox> findDue(@Param("status") String status,
                                       @Param("now") LocalDateTime now,
                                       Pageable pageable);

    long countByStatus(String status);
}
