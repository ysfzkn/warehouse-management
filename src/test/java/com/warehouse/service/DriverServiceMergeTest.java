package com.warehouse.service;

import com.warehouse.dto.DriverDuplicateGroupDto;
import com.warehouse.entity.Driver;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.DriverRepository;
import com.warehouse.repository.StockTransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Merging deletes rows and moves links, so the rules that keep it safe are pinned down here:
 * only look-alikes are grouped, the survivor absorbs what the others knew, and history is
 * repointed rather than rewritten.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DriverServiceMergeTest {

    @Mock private DriverRepository drivers;
    @Mock private StockTransferRepository transfers;

    private DriverService service;

    @BeforeEach
    void setUp() {
        service = new DriverService(drivers, transfers);
        when(transfers.countByDriverId(anyLong())).thenReturn(0L);
        when(drivers.save(any(Driver.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Driver driver(Long id, String name, String phone, int count) {
        return Driver.builder()
            .id(id).name(name).phone(phone)
            .transferCount(count).active(true)
            .lastUsedAt(LocalDateTime.now().minusDays(count))
            .build();
    }

    // ─── duplicate detection ─────────────────────────────────────────────────

    @Test
    void should_group_the_same_name_written_differently() {
        when(drivers.findAll()).thenReturn(List.of(
            driver(1L, "İSMAİL ÇINAR", "05001112233", 9),
            driver(2L, "ismail çınar", "05004445566", 3)));

        List<DriverDuplicateGroupDto> groups = service.findDuplicateGroups();

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getMatchedOn()).isEqualTo("ad");
        assertThat(groups.get(0).getCandidates()).hasSize(2);
        // Busiest record is the suggested survivor.
        assertThat(groups.get(0).getSuggestedPrimaryId()).isEqualTo(1L);
    }

    @Test
    void phone_matches_should_win_over_name_matches() {
        when(drivers.findAll()).thenReturn(List.of(
            driver(1L, "İsmail Çınar", "05001112233", 5),
            driver(2L, "İ. Çınar", "0500 111 22 33", 2)));

        List<DriverDuplicateGroupDto> groups = service.findDuplicateGroups();

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getMatchedOn()).isEqualTo("telefon");
    }

    @Test
    void should_not_group_two_different_people() {
        when(drivers.findAll()).thenReturn(List.of(
            driver(1L, "İsmail Çınar", "05001112233", 5),
            driver(2L, "Ayşe Yılmaz", "05009998877", 2)));

        assertThat(service.findDuplicateGroups()).isEmpty();
    }

    @Test
    void a_record_should_belong_to_at_most_one_group() {
        when(drivers.findAll()).thenReturn(List.of(
            driver(1L, "İsmail Çınar", "05001112233", 5),
            driver(2L, "İsmail Çınar", "05001112233", 2),
            driver(3L, "İsmail Çınar", "05007776655", 1)));

        List<DriverDuplicateGroupDto> groups = service.findDuplicateGroups();

        // 1 and 2 share a phone; 3 only shares the name and is left ungrouped rather than
        // being counted twice.
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getMatchedOn()).isEqualTo("telefon");
        assertThat(groups.get(0).getCandidates()).extracting(DriverDuplicateGroupDto.Candidate::getId)
            .containsExactlyInAnyOrder(1L, 2L);
    }

    // ─── merging ─────────────────────────────────────────────────────────────

    @Test
    void merge_should_repoint_transfers_and_absorb_counts() {
        Driver primary = driver(1L, "İsmail Çınar", "05001112233", 9);
        Driver dupe = driver(2L, "ismail çınar", "05004445566", 3);
        dupe.setTcId("11111111111");
        when(drivers.findById(1L)).thenReturn(java.util.Optional.of(primary));
        when(drivers.findAllById(List.of(2L))).thenReturn(List.of(dupe));
        when(transfers.repointDriver(eq(1L), eq(List.of(2L)))).thenReturn(4);

        DriverService.MergeResult result = service.merge(1L, List.of(2L));

        assertThat(result.repointedTransfers()).isEqualTo(4);
        assertThat(result.mergedRecords()).isEqualTo(1);
        assertThat(result.driver().getTransferCount()).isEqualTo(12);
        // The blank field on the survivor is filled from the record it absorbed.
        assertThat(result.driver().getTcId()).isEqualTo("11111111111");
        verify(drivers).deleteAll(List.of(dupe));
    }

    @Test
    void merge_should_ignore_the_primary_appearing_in_the_duplicate_list() {
        Driver primary = driver(1L, "İsmail Çınar", "05001112233", 9);
        Driver dupe = driver(2L, "ismail çınar", "05004445566", 3);
        when(drivers.findById(1L)).thenReturn(java.util.Optional.of(primary));
        when(drivers.findAllById(List.of(2L))).thenReturn(List.of(dupe));

        service.merge(1L, java.util.Arrays.asList(1L, 2L, 2L));

        verify(transfers).repointDriver(1L, List.of(2L));
    }

    @Test
    void merge_should_refuse_when_nothing_would_be_merged() {
        assertThatThrownBy(() -> service.merge(1L, List.of(1L)))
            .isInstanceOf(WarehouseManagementException.class);
        assertThatThrownBy(() -> service.merge(1L, List.of()))
            .isInstanceOf(WarehouseManagementException.class);
        verify(drivers, never()).deleteAll(any());
    }

    @Test
    void merge_should_refuse_a_stale_selection() {
        Driver primary = driver(1L, "İsmail Çınar", "05001112233", 9);
        when(drivers.findById(1L)).thenReturn(java.util.Optional.of(primary));
        // One of the chosen records was deleted in the meantime.
        when(drivers.findAllById(List.of(2L, 3L))).thenReturn(List.of(driver(2L, "x", "0500", 1)));

        assertThatThrownBy(() -> service.merge(1L, List.of(2L, 3L)))
            .isInstanceOf(WarehouseManagementException.class)
            .hasMessageContaining("bulunamadı");
        verify(transfers, never()).repointDriver(anyLong(), any());
    }
}
