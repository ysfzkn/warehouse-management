package com.warehouse.repository;

import com.warehouse.entity.Vehicle;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    /** Identity is the space-free upper-cased plate — see {@link Vehicle#toPlateKey}. */
    Optional<Vehicle> findByPlateKey(String plateKey);

    boolean existsByPlateKeyAndIdNot(String plateKey, Long id);

    /** Type-ahead: most-used first, so the vehicle you want is usually the first row. */
    @Query("SELECT v FROM Vehicle v WHERE v.active = true "
         + "AND (:pattern IS NULL OR v.searchText LIKE :pattern) "
         + "ORDER BY v.transferCount DESC, v.lastUsedAt DESC NULLS LAST, v.plate ASC")
    List<Vehicle> search(@Param("pattern") String pattern, Pageable pageable);

    @Query("SELECT v FROM Vehicle v WHERE (:activeOnly = false OR v.active = true) "
         + "AND (:pattern IS NULL OR v.searchText LIKE :pattern) "
         + "ORDER BY v.plate ASC")
    List<Vehicle> findForList(@Param("activeOnly") boolean activeOnly, @Param("pattern") String pattern);

    /** Vehicles assigned to a driver, busiest first. */
    @Query(value = "SELECT v.* FROM vehicles v JOIN driver_vehicles dv ON dv.vehicle_id = v.id "
                 + "WHERE dv.driver_id = :driverId ORDER BY v.transfer_count DESC, v.plate ASC",
           nativeQuery = true)
    List<Vehicle> findByDriverId(@Param("driverId") Long driverId);

    /** Assignments for a whole page of drivers in one query: {@code [driverId, vehicle]}. */
    @Query(value = "SELECT dv.driver_id AS driverId, v.* FROM vehicles v "
                 + "JOIN driver_vehicles dv ON dv.vehicle_id = v.id "
                 + "WHERE dv.driver_id IN (:driverIds) ORDER BY v.plate ASC", nativeQuery = true)
    List<Object[]> findAssignmentsForDrivers(@Param("driverIds") List<Long> driverIds);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "INSERT INTO driver_vehicles (driver_id, vehicle_id) VALUES (:driverId, :vehicleId) "
                 + "ON CONFLICT DO NOTHING", nativeQuery = true)
    void assign(@Param("driverId") Long driverId, @Param("vehicleId") Long vehicleId);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "DELETE FROM driver_vehicles WHERE driver_id = :driverId AND vehicle_id = :vehicleId",
           nativeQuery = true)
    void unassign(@Param("driverId") Long driverId, @Param("vehicleId") Long vehicleId);

    /** Merging drivers has to carry their vehicle assignments across. */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "INSERT INTO driver_vehicles (driver_id, vehicle_id) "
                 + "SELECT :targetId, dv.vehicle_id FROM driver_vehicles dv "
                 + "WHERE dv.driver_id IN (:sourceIds) ON CONFLICT DO NOTHING", nativeQuery = true)
    void moveAssignments(@Param("targetId") Long targetId, @Param("sourceIds") List<Long> sourceIds);
}
