package com.warehouse.mapper;

import com.warehouse.dto.StockTransferDto;
import com.warehouse.entity.Product;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StockTransferMapperTest {

    private StockTransferMapper mapper;
    private StockTransfer stockTransfer;
    private Warehouse sourceWarehouse;
    private Warehouse destinationWarehouse;
    private Product product;

    @BeforeEach
    void setUp() {
        mapper = new StockTransferMapper();

        sourceWarehouse = new Warehouse();
        sourceWarehouse.setId(1L);
        sourceWarehouse.setName("Source Warehouse");
        sourceWarehouse.setLocation("Istanbul");

        destinationWarehouse = new Warehouse();
        destinationWarehouse.setId(2L);
        destinationWarehouse.setName("Destination Warehouse");
        destinationWarehouse.setLocation("Ankara");

        product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        product.setSku("TEST-001");

        stockTransfer = new StockTransfer();
        stockTransfer.setId(1L);
        stockTransfer.setSourceWarehouse(sourceWarehouse);
        stockTransfer.setDestinationWarehouse(destinationWarehouse);
        stockTransfer.setProduct(product);
        stockTransfer.setQuantity(50);
        stockTransfer.setDriverName("John Doe");
        stockTransfer.setDriverTcId("12345678901");
        stockTransfer.setDriverPhone("+905551234567");
        stockTransfer.setVehiclePlate("34ABC123");
        stockTransfer.setStatus(TransferStatus.PENDING);
        stockTransfer.setTransferDate(LocalDateTime.now());
        stockTransfer.setNotes("Test transfer");
        stockTransfer.setTransferType(TransferType.CUSTOMER_DELIVERY);
        stockTransfer.setCustomerFullName("Müşteri Test");
        stockTransfer.setCustomerPhone("05550001122");
        stockTransfer.setCustomerAddress("Test adres 123");
        stockTransfer.setCompletionNote("Teslim edildi");
    }

    @Test
    void toDto_WhenTransferIsValid_ShouldMapCorrectly() {
        StockTransferDto result = mapper.toDto(stockTransfer);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(50, result.getQuantity());
        assertEquals("John Doe", result.getDriverName());
        assertEquals("12345678901", result.getDriverTcId());
        assertEquals("+905551234567", result.getDriverPhone());
        assertEquals("34ABC123", result.getVehiclePlate());
        assertEquals(TransferStatus.PENDING, result.getStatus());
        assertEquals(TransferType.CUSTOMER_DELIVERY, result.getTransferType());
        assertEquals("Müşteri Test", result.getCustomerFullName());
        assertEquals("05550001122", result.getCustomerPhone());
        assertEquals("Test adres 123", result.getCustomerAddress());
        assertEquals("Teslim edildi", result.getCompletionNote());
        assertEquals("Test transfer", result.getNotes());
    }

    @Test
    void toDto_ShouldMapWarehouses() {
        StockTransferDto result = mapper.toDto(stockTransfer);

        assertNotNull(result.getSourceWarehouse());
        assertEquals(1L, result.getSourceWarehouse().getId());
        assertEquals("Source Warehouse", result.getSourceWarehouse().getName());
        assertEquals("Istanbul", result.getSourceWarehouse().getLocation());

        assertNotNull(result.getDestinationWarehouse());
        assertEquals(2L, result.getDestinationWarehouse().getId());
        assertEquals("Destination Warehouse", result.getDestinationWarehouse().getName());
        assertEquals("Ankara", result.getDestinationWarehouse().getLocation());
    }

    @Test
    void toDto_ShouldMapProduct() {
        StockTransferDto result = mapper.toDto(stockTransfer);

        assertNotNull(result.getProduct());
        assertEquals(1L, result.getProduct().getId());
        assertEquals("Test Product", result.getProduct().getName());
        assertEquals("TEST-001", result.getProduct().getSku());
    }

    @Test
    void toDto_WhenTransferIsNull_ShouldReturnNull() {
        StockTransferDto result = mapper.toDto(null);

        assertNull(result);
    }

    @Test
    void toDtoList_WhenListIsValid_ShouldMapAll() {
        StockTransfer transfer2 = new StockTransfer();
        transfer2.setId(2L);
        transfer2.setSourceWarehouse(sourceWarehouse);
        transfer2.setDestinationWarehouse(destinationWarehouse);
        transfer2.setProduct(product);
        transfer2.setQuantity(30);
        transfer2.setStatus(TransferStatus.COMPLETED);
        transfer2.setTransferType(TransferType.WAREHOUSE);

        List<StockTransfer> transfers = Arrays.asList(stockTransfer, transfer2);
        List<StockTransferDto> result = mapper.toDtoList(transfers);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
    }

    @Test
    void toDtoList_WhenListIsNull_ShouldReturnNull() {
        List<StockTransferDto> result = mapper.toDtoList(null);

        assertNull(result);
    }
}

