package com.warehouse.service;

import com.warehouse.entity.Driver;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.dto.DriverDuplicateGroupDto;
import com.warehouse.repository.DriverRepository;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.util.TurkishText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final StockTransferRepository transfers;

    public DriverService(DriverRepository drivers, StockTransferRepository transfers) {
        this.drivers = drivers;
        this.transfers = transfers;
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
    public Driver recordUsage(String name, String tcId, String phone, String vehiclePlate) {
        try {
            if (name == null || name.isBlank()) return null;
            String cleanPhone = trimToNull(phone);
            String cleanTc = trimToNull(tcId);

            Driver driver = null;
            if (cleanPhone != null) driver = drivers.findByPhone(cleanPhone).orElse(null);
            if (driver == null && cleanTc != null) driver = drivers.findByTcId(cleanTc).orElse(null);

            if (driver == null) {
                driver = Driver.builder()
                    .name(normalizeName(name))
                    .tcId(cleanTc)
                    .phone(cleanPhone)
                    .vehiclePlate(upper(vehiclePlate))
                    .transferCount(0)
                    .active(true)
                    .build();
            } else {
                // The freshest spelling wins — a driver who changed vehicle should not keep
                // showing the old plate in the suggestions.
                driver.setName(normalizeName(name));
                if (cleanTc != null) driver.setTcId(cleanTc);
                if (upper(vehiclePlate) != null) driver.setVehiclePlate(upper(vehiclePlate));
                driver.setActive(true);
            }
            driver.setTransferCount((driver.getTransferCount() == null ? 0 : driver.getTransferCount()) + 1);
            driver.setLastUsedAt(LocalDateTime.now());
            driver.setSearchText(searchTextOf(driver));
            return drivers.save(driver);
        } catch (Exception e) {
            logger.warn("Şoför rehberine yazılamadı ({}): {}", name, e.toString());
            return null;
        }
    }

    @Transactional
    public Driver create(Driver input) {
        Driver driver = Driver.builder()
            .name(normalizeName(input.getName()))
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
        driver.setName(normalizeName(input.getName()));
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

    /**
     * Finds driver records that look like the same person.
     *
     * <p>Two signals, in order of trust: the same phone number (unambiguous), then the same
     * normalised name — which is what catches the same driver entered on three different days
     * with three different spellings and three different numbers.</p>
     *
     * <p>Deliberately advisory. Two real people can share a name, so nothing is merged without
     * the operator confirming the group and choosing which record survives.</p>
     */
    @Transactional(readOnly = true)
    public List<DriverDuplicateGroupDto> findDuplicateGroups() {
        List<Driver> all = drivers.findAll();
        Map<String, List<Driver>> byPhone = new LinkedHashMap<>();
        Map<String, List<Driver>> byName = new LinkedHashMap<>();

        for (Driver d : all) {
            String phone = TurkishText.normalizePhone(d.getPhone());
            if (phone.length() == 10) {
                byPhone.computeIfAbsent(phone, k -> new ArrayList<>()).add(d);
            }
            String name = TurkishText.normalize(d.getName());
            if (!name.isEmpty()) {
                byName.computeIfAbsent(name, k -> new ArrayList<>()).add(d);
            }
        }

        List<DriverDuplicateGroupDto> groups = new ArrayList<>();
        Set<Long> grouped = new LinkedHashSet<>();

        // Phone matches first: a record belongs to one group only, and this is the stronger
        // signal, so it claims its members before name matching runs.
        for (Map.Entry<String, List<Driver>> entry : byPhone.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            groups.add(toGroup("telefon", entry.getKey(), entry.getValue()));
            entry.getValue().forEach(d -> grouped.add(d.getId()));
        }
        for (Map.Entry<String, List<Driver>> entry : byName.entrySet()) {
            List<Driver> members = entry.getValue().stream()
                .filter(d -> !grouped.contains(d.getId()))
                .collect(Collectors.toList());
            if (members.size() < 2) continue;
            groups.add(toGroup("ad", entry.getKey(), members));
            members.forEach(d -> grouped.add(d.getId()));
        }
        groups.sort(Comparator.comparingLong(DriverDuplicateGroupDto::getAffectedTransfers).reversed());
        return groups;
    }

    private DriverDuplicateGroupDto toGroup(String matchedOn, String matchedValue, List<Driver> members) {
        List<DriverDuplicateGroupDto.Candidate> candidates = new ArrayList<>();
        long affected = 0;
        for (Driver d : members) {
            long linked = transfers.countByDriverId(d.getId());
            affected += linked;
            candidates.add(DriverDuplicateGroupDto.Candidate.of(d, linked));
        }
        // Busiest record first — that is the one worth keeping, and the one we suggest.
        candidates.sort(Comparator.comparingInt(
            (DriverDuplicateGroupDto.Candidate c) -> c.getTransferCount() == null ? 0 : c.getTransferCount())
            .reversed());
        return DriverDuplicateGroupDto.builder()
            .matchedOn(matchedOn)
            .matchedValue(matchedValue)
            .suggestedPrimaryId(candidates.get(0).getId())
            .affectedTransfers(affected)
            .candidates(candidates)
            .build();
    }

    /**
     * Folds {@code duplicateIds} into {@code primaryId}.
     *
     * <p>Transfers are repointed, never rewritten: each one keeps the driver name, TC, phone and
     * plate it was saved with, so an old delivery record still reads the way it was signed. Only
     * the directory link moves.</p>
     *
     * <p>Blank fields on the survivor are filled from the records being absorbed — that is
     * usually where a missing TC or plate is — and the usage counts are added together.</p>
     */
    @Transactional
    public MergeResult merge(Long primaryId, List<Long> duplicateIds) {
        if (primaryId == null || duplicateIds == null || duplicateIds.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Birleştirme için bir ana kayıt ve en az bir mükerrer kayıt seçilmelidir.");
        }
        List<Long> sources = duplicateIds.stream()
            .filter(Objects::nonNull)
            .filter(id -> !id.equals(primaryId))
            .distinct()
            .collect(Collectors.toList());
        if (sources.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Ana kayıt ile birleştirilecek başka bir kayıt seçilmedi.");
        }

        Driver primary = get(primaryId);
        List<Driver> duplicates = drivers.findAllById(sources);
        if (duplicates.size() != sources.size()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Birleştirilecek kayıtlardan bazıları bulunamadı; listeyi yenileyip tekrar deneyin.");
        }

        int mergedCount = primary.getTransferCount() == null ? 0 : primary.getTransferCount();
        LocalDateTime lastUsed = primary.getLastUsedAt();
        for (Driver duplicate : duplicates) {
            mergedCount += duplicate.getTransferCount() == null ? 0 : duplicate.getTransferCount();
            if (lastUsed == null
                    || (duplicate.getLastUsedAt() != null && duplicate.getLastUsedAt().isAfter(lastUsed))) {
                lastUsed = duplicate.getLastUsedAt();
            }
            if (primary.getTcId() == null) primary.setTcId(duplicate.getTcId());
            if (primary.getPhone() == null) primary.setPhone(duplicate.getPhone());
            if (primary.getVehiclePlate() == null) primary.setVehiclePlate(duplicate.getVehiclePlate());
            if (primary.getNotes() == null) primary.setNotes(duplicate.getNotes());
        }

        int repointed = transfers.repointDriver(primaryId, sources);
        drivers.deleteAll(duplicates);
        // Flushed before the survivor is saved, so the unique phone index cannot trip on a
        // number that the delete is about to free.
        drivers.flush();

        primary.setTransferCount(mergedCount);
        primary.setLastUsedAt(lastUsed);
        primary.setActive(true);
        primary.setSearchText(searchTextOf(primary));
        Driver saved = drivers.save(primary);
        logger.info("Sofor birlestirme: {} kaydi {} mukerrer kaydi devraldi, {} transfer tasindi",
            saved.getId(), duplicates.size(), repointed);
        return new MergeResult(saved, duplicates.size(), repointed);
    }

    /** What a merge actually did, so the screen can report it rather than guess. */
    public record MergeResult(Driver driver, int mergedRecords, int repointedTransfers) {}

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

    /**
     * Title casing lower-cases everything after the first letter, which is right for "AYŞE
     * YILMAZ" but destroys anything else: a driver entered as "51TV51" came back as "51tv51".
     * Values carrying digits are not names, so they are stored exactly as typed.
     */
    private static String normalizeName(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) return null;
        for (char c : trimmed.toCharArray()) {
            if (Character.isDigit(c)) return trimmed;
        }
        return TurkishText.toTitleCase(trimmed);
    }

    private static String upper(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.forLanguageTag("tr-TR"));
    }
}
