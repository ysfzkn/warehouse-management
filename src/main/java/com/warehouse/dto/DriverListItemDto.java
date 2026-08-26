package com.warehouse.dto;

import com.warehouse.entity.Driver;
import com.warehouse.entity.Vehicle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A driver plus the vehicles they are allowed to take.
 *
 * <p>The assignment list belongs on the row itself: without it the screen would have to ask the
 * server once per driver just to render the table.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverListItemDto {

    private Long id;
    private String name;
    private String phone;
    private String tcId;
    private String notes;
    private boolean active;
    private Integer transferCount;
    private LocalDateTime lastUsedAt;
    private List<VehicleRef> vehicles;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VehicleRef {
        private Long id;
        private String plate;
        private String brandModel;

        public static VehicleRef of(Vehicle v) {
            return new VehicleRef(v.getId(), v.getPlate(), v.getBrandModel());
        }
    }

    public static DriverListItemDto of(Driver d, List<Vehicle> vehicles) {
        return DriverListItemDto.builder()
            .id(d.getId())
            .name(d.getName())
            .phone(d.getPhone())
            .tcId(d.getTcId())
            .notes(d.getNotes())
            .active(d.isActive())
            .transferCount(d.getTransferCount())
            .lastUsedAt(d.getLastUsedAt())
            .vehicles(vehicles.stream().map(VehicleRef::of).toList())
            .build();
    }
}
