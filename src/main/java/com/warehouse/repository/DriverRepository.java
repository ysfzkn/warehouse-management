package com.warehouse.repository;

import com.warehouse.entity.Driver;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    /** The phone is the driver's identity — see {@link Driver}. */
    Optional<Driver> findByPhone(String phone);

    Optional<Driver> findByTcId(String tcId);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    /**
     * Type-ahead over the normalised text, so "ballı", "BALLI" and "balli" all find the same
     * driver. Most-used first, then most-recent — the person you pick is usually one of those.
     */
    @Query("SELECT d FROM Driver d WHERE d.active = true " +
           "AND (:pattern IS NULL OR d.searchText LIKE :pattern) " +
           "ORDER BY d.transferCount DESC, d.lastUsedAt DESC NULLS LAST, d.name ASC")
    List<Driver> search(@Param("pattern") String pattern, Pageable pageable);

    @Query("SELECT d FROM Driver d WHERE (:activeOnly = false OR d.active = true) " +
           "AND (:pattern IS NULL OR d.searchText LIKE :pattern) " +
           "ORDER BY d.name ASC")
    List<Driver> findForList(@Param("activeOnly") boolean activeOnly,
                             @Param("pattern") String pattern);
}
