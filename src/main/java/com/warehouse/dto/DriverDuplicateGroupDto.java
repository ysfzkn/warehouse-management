package com.warehouse.dto;

import com.warehouse.entity.Driver;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A set of driver records that look like the same person, offered for merging.
 *
 * <p>Nothing is merged automatically: names are matched loosely on purpose (that is what finds
 * "İSMAİL ÇINAR" and "ismail çınar"), so the operator confirms each group and picks which record
 * survives.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverDuplicateGroupDto {

    /** What made these look alike — a name or a phone number. */
    private String matchedOn;

    /** The normalised value the group was built on, shown so the grouping is auditable. */
    private String matchedValue;

    /** Suggested survivor: the one with the most transfers. */
    private Long suggestedPrimaryId;

    /** Transfers that would be repointed if the whole group were merged. */
    private long affectedTransfers;

    private List<Candidate> candidates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        private Long id;
        private String name;
        private String phone;
        private String tcId;
        private String vehiclePlate;
        private Integer transferCount;
        private java.time.LocalDateTime lastUsedAt;
        private boolean active;
        /** Transfers currently linked to this record. */
        private long linkedTransfers;

        public static Candidate of(Driver d, long linkedTransfers) {
            return Candidate.builder()
                .id(d.getId())
                .name(d.getName())
                .phone(d.getPhone())
                .tcId(d.getTcId())
                .vehiclePlate(d.getVehiclePlate())
                .transferCount(d.getTransferCount())
                .lastUsedAt(d.getLastUsedAt())
                .active(d.isActive())
                .linkedTransfers(linkedTransfers)
                .build();
        }
    }
}
