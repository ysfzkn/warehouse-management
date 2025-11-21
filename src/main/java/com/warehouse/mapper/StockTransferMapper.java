package com.warehouse.mapper;

import com.warehouse.dto.StockTransferDto;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.StockTransferItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
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
        dto.setTransferType(entity.getTransferType());
        dto.setTransferDate(entity.getTransferDate());
        dto.setCompletedDate(entity.getCompletedDate());
        dto.setCancelledDate(entity.getCancelledDate());
        dto.setNotes(entity.getNotes());
        dto.setCancellationReason(entity.getCancellationReason());
        dto.setCustomerFullName(entity.getCustomerFullName());
        dto.setCustomerPhone(entity.getCustomerPhone());
        dto.setCustomerAddress(entity.getCustomerAddress());
        dto.setCompletionNote(entity.getCompletionNote());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setApprovalStatus(entity.getApprovalStatus());
        dto.setApprovalRequestedBy(entity.getApprovalRequestedBy());
        dto.setApprovalRequestedAt(entity.getApprovalRequestedAt());
        dto.setApprovalDecisionBy(entity.getApprovalDecisionBy());
        dto.setApprovalDecisionAt(entity.getApprovalDecisionAt());
        dto.setApprovalNote(entity.getApprovalNote());

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
            dto.setProduct(mapProduct(entity.getProduct().getId(), entity.getProduct().getName(), entity.getProduct().getSku()));
        }

        mapItems(entity, dto);

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

    private void mapItems(StockTransfer entity, StockTransferDto dto) {
        List<StockTransferItem> itemEntities = entity.getItems();
        if (itemEntities != null && !itemEntities.isEmpty()) {
            List<StockTransferDto.TransferItemDto> itemDtos = itemEntities.stream()
                    .map(item -> {
                        StockTransferDto.TransferItemDto dtoItem = new StockTransferDto.TransferItemDto();
                        dtoItem.setId(item.getId());
                        dtoItem.setQuantity(item.getQuantity());
                        if (item.getProduct() != null) {
                            dtoItem.setProduct(mapProduct(item.getProduct().getId(), item.getProduct().getName(), item.getProduct().getSku()));
                        }
                        return dtoItem;
                    })
                    .collect(Collectors.toList());

            dto.setItems(itemDtos);

            int totalQuantity = itemEntities.stream()
                    .map(StockTransferItem::getQuantity)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();

            dto.setTotalQuantity(totalQuantity > 0 ? totalQuantity : dto.getQuantity());
            dto.setQuantity(dto.getTotalQuantity());

            long productCount = itemEntities.stream()
                    .map(item -> item.getProduct() != null ? item.getProduct().getId() : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            dto.setUniqueProductCount((int) productCount);

            if (dto.getProduct() == null && !itemEntities.isEmpty()) {
                StockTransferItem firstItem = itemEntities.get(0);
                if (firstItem.getProduct() != null) {
                    dto.setProduct(mapProduct(firstItem.getProduct().getId(), firstItem.getProduct().getName(), firstItem.getProduct().getSku()));
                }
            }
        } else {
            dto.setTotalQuantity(dto.getQuantity());
            dto.setUniqueProductCount(dto.getProduct() != null ? 1 : 0);
        }
    }

    private StockTransferDto.SimpleProductDto mapProduct(Long id, String name, String sku) {
        StockTransferDto.SimpleProductDto productDto = new StockTransferDto.SimpleProductDto();
        productDto.setId(id);
        productDto.setName(name);
        productDto.setSku(sku);
        return productDto;
    }
}

