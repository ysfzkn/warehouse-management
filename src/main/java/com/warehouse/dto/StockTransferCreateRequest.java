package com.warehouse.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.warehouse.enums.TransferType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class StockTransferCreateRequest {

    @NotNull(message = "Source warehouse is required")
    private Long sourceWarehouseId;

    private Long destinationWarehouseId;

    private TransferType transferType = TransferType.WAREHOUSE;

    @NotBlank(message = "Driver name is required")
    @Size(min = 3, max = 100, message = "Driver name must be between 3 and 100 characters")
    private String driverName;

    @NotBlank(message = "Driver TC ID is required")
    @Pattern(regexp = "^[0-9]{11}$", message = "Driver TC ID must be 11 digits")
    private String driverTcId;

    @NotBlank(message = "Driver phone is required")
    @Size(min = 10, max = 20, message = "Driver phone must be between 10 and 20 characters")
    private String driverPhone;

    @NotBlank(message = "Vehicle plate is required")
    @Size(min = 2, max = 20, message = "Vehicle plate must be between 2 and 20 characters")
    private String vehiclePlate;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @NotNull(message = "Transfer date is required")
    private LocalDateTime transferDate;

    @Size(max = 150, message = "Customer full name cannot exceed 150 characters")
    private String customerFullName;

    @Size(max = 20, message = "Customer phone cannot exceed 20 characters")
    private String customerPhone;

    @Size(max = 500, message = "Customer address cannot exceed 500 characters")
    private String customerAddress;

    /** Optional link to the order this customer delivery fulfils. */
    private Long orderId;

    /** Optional link to the recipient's e-commerce customer record. */
    private Long customerId;

    @Valid
    @NotEmpty(message = "At least one product must be selected for transfer")
    private List<TransferItemRequest> items;

    @Data
    public static class TransferItemRequest {
        private Long stockId;
        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;
    }
}


