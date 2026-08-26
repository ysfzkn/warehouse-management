package com.warehouse.service;

import com.warehouse.entity.Driver;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.DriverRepository;
import com.warehouse.util.TurkishText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * The driver directory behind the transfer form.
 *
 * <p>Nothing here has to be curated by hand: {@link #recordUsage} runs on every transfer, so the
 * list fills itself from real work and stays ranked by who actually drives. The screen exists for
 * corrections — a mistyped plate, a driver who left — rather than for data entry.</p>
 */
@Service
public class DriverService {

    private static final Logger logger = LoggerFactory.getLogger(DriverService.class);
    private static final int SUGGESTION_LIMIT = 10;

    private final DriverRepository drivers;

    public DriverService(DriverRepository drivers) {
        this.drivers = drivers;
    }

    /** Type-ahead for the transfer form. A blank query returns the most-used drivers. */
    @Transactional(readOnly = true)
    public List<Driver> suggest(String query) {
        return drivers.search(TurkishText.searchPattern(query), PageRequest.of(0, SUGGESTION_LIMIT));
    }

    @Transactional(readOnly = true)
    public List<Driver> list(String query, boolean activeOnly) {
        return drivers.findForList(activeOnly, TurkishText.searchPattern(query));
    }

    @Transactional(readOnly = true)
    public Driver get(Long id) {
        return drivers.findById(id).orElseThrow(() -> notFound(id));
    }

    /**
     * Called whenever a transfer is saved. Matches on the phone first (the one field that is both
     * unique and reliably filled), then the TC number; anything else creates a new entry.
     *
     * <p>Never throws: a failure to file the driver away must not cost the operator a transfer.</p>
     */
    @Transactional
    public void recordUsage(String name, String tcId, String phone, String vehiclePlate) {
        try {
            if (name == null || name.isBlank()) return;
            String cleanPhone = trimToNull(phone);
            String cleanTc = trimToNull(tcId);

            Driver driver = null;
            if (cleanPhone != null) driver = drivers.findByPhone(cleanPhone).orElse(null);
            if (driver == null && cleanTc != null) driver = drivers.findByTcId(cleanTc).orElse(null);

            if (driver == null) {
                driver = Driver.builder()
                    .name(TurkishText.toTitleCase(name))
                    .tcId(cleanTc)
                    .phone(cleanPhone)
                    .vehiclePlate(upper(vehiclePlate))
                    .transferCount(0)
                    .active(true)
                    .build();
            } else {
                // The freshest spelling wins — a driver who changed vehicle should not keep
                // showing the old plate in the suggestions.
                driver.setName(TurkishText.toTitleCase(name));
                if (cleanTc != null) driver.setTcId(cleanTc);
                if (upper(vehiclePlate) != null) driver.setVehiclePlate(upper(vehiclePlate));
                driver.setActive(true);
            }
            driver.setTransferCount((driver.getTransferCount() == null ? 0 : driver.getTransferCount()) + 1);
            driver.setLastUsedAt(LocalDateTime.now());
            driver.setSearchText(searchTextOf(driver));
            drivers.save(driver);
        } catch (Exception e) {
            logger.warn("Şoför rehberine yazılamadı ({}): {}", name, e.toString());
        }
    }

    @Transactional
    public Driver create(Driver input) {
        Driver driver = Driver.builder()
            .name(TurkishText.toTitleCase(input.getName()))
            .tcId(trimToNull(input.getTcId()))
            .phone(trimToNull(input.getPhone()))
            .vehiclePlate(upper(input.getVehiclePlate()))
            .notes(trimToNull(input.getNotes()))
            .active(true)
            .transferCount(0)
            .build();
        requireUniquePhone(driver.getPhone(), null);
        driver.setSearchText(searchTextOf(driver));
        return drivers.save(driver);
    }

    @Transactional
    public Driver update(Long id, Driver input) {
        Driver driver = get(id);
        requireUniquePhone(trimToNull(input.getPhone()), id);
        driver.setName(TurkishText.toTitleCase(input.getName()));
        driver.setTcId(trimToNull(input.getTcId()));
        driver.setPhone(trimToNull(input.getPhone()));
        driver.setVehiclePlate(upper(input.getVehiclePlate()));
        driver.setNotes(trimToNull(input.getNotes()));
        driver.setActive(input.isActive());
        driver.setSearchText(searchTextOf(driver));
        return drivers.save(driver);
    }

    /**
     * Removes the driver from the directory. Transfers keep their own copy of the driver details,
     * so past records stay readable — this only stops the name being offered again.
     */
    @Transactional
    public void delete(Long id) {
        drivers.delete(get(id));
    }

    @Transactional
    public Driver toggleActive(Long id) {
        Driver driver = get(id);
        driver.setActive(!driver.isActive());
        return drivers.save(driver);
    }

    private void requireUniquePhone(String phone, Long selfId) {
        if (phone == null) return;
        boolean taken = selfId == null
            ? drivers.findByPhone(phone).isPresent()
            : drivers.existsByPhoneAndIdNot(phone, selfId);
        if (taken) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Bu telefon numarasıyla kayıtlı bir şoför zaten var: " + phone);
        }
    }

    private static String searchTextOf(Driver driver) {
        return TurkishText.normalizeForSearch(
            driver.getName(), driver.getPhone(), driver.getTcId(), driver.getVehiclePlate());
    }

    private static WarehouseManagementException notFound(Long id) {
        return new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Şoför bulunamadı: " + id);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String upper(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.forLanguageTag("tr-TR"));
    }
}
