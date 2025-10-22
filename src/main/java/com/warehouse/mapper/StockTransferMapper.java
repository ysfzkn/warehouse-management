package com.warehouse.mapper;

import com.warehouse.dto.StockTransferDto;
import com.warehouse.entity.StockTransfer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class StockTransferMapper {

    public StockTransferDto toDto(StockTransfer entity) {
        if (entity == null) {
            return null;
        }

        StockTransferDto dto = new StockTransferDto();
        dto.setId(entity.getId());
        dto.setQuantity(entity.getQuantity());
        dto.setDriverName(entity.getDriverName());
        dto.setDriverTcId(entity.getDriverTcId());
        dto.setDriverPhone(entity.getDriverPhone());
        dto.setVehiclePlate(entity.getVehiclePlate());
        dto.setStatus(entity.getStatus());
        dto.setTransferDate(entity.getTransferDate());
        dto.setCompletedDate(entity.getCompletedDate());
        dto.setCancelledDate(entity.getCancelledDate());
        dto.setNotes(entity.getNotes());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        if (entity.getSourceWarehouse() != null) {
            StockTransferDto.SimpleWarehouseDto sourceDto = new StockTransferDto.SimpleWarehouseDto();
            sourceDto.setId(entity.getSourceWarehouse().getId());
            sourceDto.setName(entity.getSourceWarehouse().getName());
            sourceDto.setLocation(entity.getSourceWarehouse().getLocation());
            dto.setSourceWarehouse(sourceDto);
        }

        if (entity.getDestinationWarehouse() != null) {
            StockTransferDto.SimpleWarehouseDto destDto = new StockTransferDto.SimpleWarehouseDto();
            destDto.setId(entity.getDestinationWarehouse().getId());
            destDto.setName(entity.getDestinationWarehouse().getName());
            destDto.setLocation(entity.getDestinationWarehouse().getLocation());
            dto.setDestinationWarehouse(destDto);
        }

        if (entity.getProduct() != null) {
            StockTransferDto.SimpleProductDto productDto = new StockTransferDto.SimpleProductDto();
            productDto.setId(entity.getProduct().getId());
            productDto.setName(entity.getProduct().getName());
            productDto.setSku(entity.getProduct().getSku());
            dto.setProduct(productDto);
        }

        return dto;
    }

    public List<StockTransferDto> toDtoList(List<StockTransfer> entities) {
        if (entities == null) {
            return null;
        }
        return entities.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
}

