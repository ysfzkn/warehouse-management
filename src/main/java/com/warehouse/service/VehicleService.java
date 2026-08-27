package com.warehouse.service;

import com.warehouse.entity.Vehicle;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.VehicleRepository;
import com.warehouse.util.TurkishText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * The vehicle directory.
 *
 * <p>Like drivers, it fills itself from real transfers — {@link #recordUsage} upserts on every
 * save — and the screen exists for corrections and for registering a vehicle before it is first
 * used. Plates are matched on their space-free upper-cased form, so one vehicle stays one row
 * however the operator typed it.</p>
 */
@Service
public class VehicleService {

    private static final Logger logger = LoggerFactory.getLogger(VehicleService.class);
    private static final int SUGGESTION_LIMIT = 15;

    private final VehicleRepository vehicles;

    /** Backs the best-effort writes below, which must commit or fail on their own. */
    private final TransactionTemplate ownTransaction;

    public VehicleService(VehicleRepository vehicles, PlatformTransactionManager transactionManager) {
        this.vehicles = vehicles;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public List<Vehicle> suggest(String query) {
        return vehicles.search(TurkishText.searchPattern(query), PageRequest.of(0, SUGGESTION_LIMIT));
    }

    @Transactional(readOnly = true)
    public List<Vehicle> list(String query, boolean activeOnly) {
        return vehicles.findForList(activeOnly, TurkishText.searchPattern(query));
    }

    @Transactional(readOnly = true)
    public List<Vehicle> forDriver(Long driverId) {
        return driverId == null ? List.of() : vehicles.findByDriverId(driverId);
    }

    @Transactional(readOnly = true)
    public Vehicle get(Long id) {
        return vehicles.findById(id).orElseThrow(() -> notFound(id));
    }

    /**
     * Called when a transfer is saved. Returns the directory entry so the transfer can link to
     * it. Never throws — filing the vehicle away must not cost the operator their transfer. That
     * takes more than a catch: a failed flush marks the surrounding transaction rollback-only, so
     * the transfer would still be lost at commit. See {@code DriverService.recordUsage} for why
     * the transaction is opened here rather than declared with {@code REQUIRES_NEW}.
     */
    public Vehicle recordUsage(String rawPlate) {
        try {
            return ownTransaction.execute(status -> upsertUsage(rawPlate));
        } catch (Exception e) {
            logger.warn("Araç rehberine yazılamadı ({}): {}", rawPlate, e.toString());
            return null;
        }
    }

    private Vehicle upsertUsage(String rawPlate) {
        String key = Vehicle.toPlateKey(rawPlate);
        if (key == null) return null;
        Vehicle vehicle = vehicles.findByPlateKey(key).orElseGet(() -> Vehicle.builder()
            .plate(display(rawPlate))
            .plateKey(key)
            .transferCount(0)
            .active(true)
            .build());
        vehicle.setPlate(display(rawPlate));
        vehicle.setActive(true);
        vehicle.setTransferCount((vehicle.getTransferCount() == null ? 0 : vehicle.getTransferCount()) + 1);
        vehicle.setLastUsedAt(LocalDateTime.now());
        vehicle.setSearchText(TurkishText.normalizeForSearch(vehicle.getPlate(), vehicle.getBrandModel()));
        return vehicles.save(vehicle);
    }

    @Transactional
    public Vehicle create(Vehicle input) {
        String key = Vehicle.toPlateKey(input.getPlate());
        if (key == null) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Geçerli bir plaka girin.");
        }
        requireUniquePlate(key, null);
        Vehicle vehicle = Vehicle.builder()
            .plate(display(input.getPlate()))
            .plateKey(key)
            .brandModel(trimToNull(input.getBrandModel()))
            .notes(trimToNull(input.getNotes()))
            .active(true)
            .transferCount(0)
            .build();
        vehicle.setSearchText(TurkishText.normalizeForSearch(vehicle.getPlate(), vehicle.getBrandModel()));
        return vehicles.save(vehicle);
    }

    @Transactional
    public Vehicle update(Long id, Vehicle input) {
        Vehicle vehicle = get(id);
        String key = Vehicle.toPlateKey(input.getPlate());
        if (key == null) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Geçerli bir plaka girin.");
        }
        requireUniquePlate(key, id);
        vehicle.setPlate(display(input.getPlate()));
        vehicle.setPlateKey(key);
        vehicle.setBrandModel(trimToNull(input.getBrandModel()));
        vehicle.setNotes(trimToNull(input.getNotes()));
        vehicle.setActive(input.isActive());
        vehicle.setSearchText(TurkishText.normalizeForSearch(vehicle.getPlate(), vehicle.getBrandModel()));
        return vehicles.save(vehicle);
    }

    /**
     * Removes the vehicle from the directory. Transfers keep the plate they were saved with, so
     * past records still read correctly — this only stops it being offered again.
     */
    @Transactional
    public void delete(Long id) {
        vehicles.delete(get(id));
    }

    @Transactional
    public Vehicle toggleActive(Long id) {
        Vehicle vehicle = get(id);
        vehicle.setActive(!vehicle.isActive());
        return vehicles.save(vehicle);
    }

    /** Links a vehicle to a driver. Idempotent, so re-assigning is harmless. */
    @Transactional
    public void assignToDriver(Long driverId, Long vehicleId) {
        get(vehicleId);
        vehicles.assign(driverId, vehicleId);
    }

    @Transactional
    public void unassignFromDriver(Long driverId, Long vehicleId) {
        vehicles.unassign(driverId, vehicleId);
    }

    /** Best-effort assignment used by the transfer flow; a failure must not break the save. */
    public void linkQuietly(Long driverId, Long vehicleId) {
        if (driverId == null || vehicleId == null) return;
        try {
            ownTransaction.executeWithoutResult(status -> vehicles.assign(driverId, vehicleId));
        } catch (Exception e) {
            logger.warn("Şoför-araç ataması yapılamadı ({} / {}): {}", driverId, vehicleId, e.toString());
        }
    }

    private void requireUniquePlate(String plateKey, Long selfId) {
        boolean taken = selfId == null
            ? vehicles.findByPlateKey(plateKey).isPresent()
            : vehicles.existsByPlateKeyAndIdNot(plateKey, selfId);
        if (taken) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Bu plakayla kayıtlı bir araç zaten var.");
        }
    }

    private static WarehouseManagementException notFound(Long id) {
        return new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Araç bulunamadı: " + id);
    }

    private static String display(String raw) {
        String trimmed = trimToNull(raw);
        return trimmed == null ? null
            : trimmed.replaceAll("\\s+", " ").toUpperCase(Locale.forLanguageTag("tr-TR"));
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
