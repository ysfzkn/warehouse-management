package com.warehouse.dto;

import com.warehouse.entity.Warehouse;
import com.warehouse.enums.WarehouseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseResponse {

    private Long id;
    private String name;
    private String location;
    private String phone;
    private String manager;
    private Double capacitySqm;
    private boolean active;
    private WarehouseType warehouseType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long totalQuantity;

    public static WarehouseResponse from(Warehouse warehouse, long totalQuantity) {
        return WarehouseResponse.builder()
                .id(warehouse.getId())
                .name(warehouse.getName())
                .location(warehouse.getLocation())
                .phone(warehouse.getPhone())
                .manager(warehouse.getManager())
                .capacitySqm(warehouse.getCapacitySqm())
                .active(warehouse.isActive())
                .warehouseType(warehouse.getWarehouseType())
                .createdAt(warehouse.getCreatedAt())
                .updatedAt(warehouse.getUpdatedAt())
                .totalQuantity(totalQuantity)
                .build();
    }
}
