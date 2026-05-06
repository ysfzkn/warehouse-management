package com.warehouse.service;

import com.warehouse.entity.Product;
import com.warehouse.entity.Stock;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.AuditAction;
import com.warehouse.enums.WarehouseType;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.WarehouseRepository;
import com.warehouse.service.impl.StockServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationService notificationService;

    private StockService stockService;

    private Stock stock;
    private Product product;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        stockService = new StockServiceImpl(stockRepository, productRepository, warehouseRepository, auditService, notificationService);
        product = new Product();
        product.setId(1L);
        product.setName("Test Product");

        warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setName("Test Warehouse");

        stock = new Stock();
        stock.setId(1L);
        stock.setProduct(product);
        stock.setWarehouse(warehouse);
        stock.setQuantity(100);
        stock.setMinStockLevel(10);
        stock.setReservedQuantity(0);
        stock.setConsignedQuantity(0);
    }

    @Test
    void getAllStocks_ShouldReturnAllStocks() {
        List<Stock> stocks = Arrays.asList(stock);
        when(stockRepository.findAll()).thenReturn(stocks);

        List<Stock> result = stockService.getAllStocks();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(stockRepository, times(1)).findAll();
    }

    @Test
    void getStockByIdOrThrow_WhenStockExists_ShouldReturnStock() {
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));

        Stock result = stockService.getStockByIdOrThrow(1L);

        assertNotNull(result);
        assertEquals(100, result.getQuantity());
        verify(stockRepository, times(1)).findById(1L);
    }

    @Test
    void getStockByIdOrThrow_WhenStockNotExists_ShouldThrowException() {
        when(stockRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(WarehouseManagementException.class, () -> 
            stockService.getStockByIdOrThrow(999L)
        );
    }

    @Test
    void createStock_WhenValid_ShouldCreateStock() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(stockRepository.findByProductAndWarehouse(product, warehouse)).thenReturn(Optional.empty());
        when(stockRepository.save(any(Stock.class))).thenReturn(stock);

        Stock result = stockService.createStock(stock);

        assertNotNull(result);
        assertEquals(100, result.getQuantity());
        verify(stockRepository, times(1)).save(any(Stock.class));
    }

    @Test
    void createStock_WhenDuplicate_ShouldMergeIntoExisting() {
        Stock existing = new Stock();
        existing.setId(1L);
        existing.setProduct(product);
        existing.setWarehouse(warehouse);
        existing.setQuantity(100);
        existing.setMinStockLevel(10);
        existing.setReservedQuantity(0);
        existing.setConsignedQuantity(0);

        Stock request = new Stock();
        request.setProduct(product);
        request.setWarehouse(warehouse);
        request.setQuantity(30);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(stockRepository.findByProductAndWarehouse(product, warehouse)).thenReturn(Optional.of(existing));
        when(stockRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(stockRepository.save(any(Stock.class))).thenAnswer(inv -> inv.getArgument(0));

        Stock result = stockService.createStock(request);

        assertNotNull(result);
        assertEquals(130, result.getQuantity());
        verify(stockRepository, times(1)).save(any(Stock.class));
        verify(auditService, times(1)).log(eq(AuditAction.STOCK_ADD), anyString(), anyLong(), anyString(),
                anyString(), any());
        verify(auditService, never()).log(eq(AuditAction.STOCK_CREATE), anyString(), anyLong(), anyString(),
                anyString(), any());
    }

    @Test
    void createStock_WhenDuplicateEmanetDepo_ShouldMergeForSameCustomer() {
        Warehouse emanet = new Warehouse();
        emanet.setId(2L);
        emanet.setName("Emanet Depo");
        emanet.setWarehouseType(WarehouseType.EMANET_DEPO);

        Stock existing = new Stock();
        existing.setId(7L);
        existing.setProduct(product);
        existing.setWarehouse(emanet);
        existing.setQuantity(20);
        existing.setCustomerName("Acme");
        existing.setCustomerPhone("5551112233");

        Stock request = new Stock();
        request.setProduct(product);
        request.setWarehouse(emanet);
        request.setQuantity(10);
        request.setCustomerName("Acme");
        request.setCustomerPhone("5551112233");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(warehouseRepository.findById(2L)).thenReturn(Optional.of(emanet));
        when(stockRepository.findByProductAndWarehouseAndCustomerName(product, emanet, "Acme"))
                .thenReturn(Optional.of(existing));
        when(stockRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(stockRepository.save(any(Stock.class))).thenAnswer(inv -> inv.getArgument(0));

        Stock result = stockService.createStock(request);

        assertNotNull(result);
        assertEquals(30, result.getQuantity());
        verify(auditService, times(1)).log(eq(AuditAction.STOCK_ADD), anyString(), anyLong(), anyString(),
                anyString(), any());
    }

    @Test
    void createStock_WhenDuplicateEmanetDepo_DifferentCustomer_ShouldCreateNew() {
        Warehouse emanet = new Warehouse();
        emanet.setId(2L);
        emanet.setName("Emanet Depo");
        emanet.setWarehouseType(WarehouseType.EMANET_DEPO);

        Stock request = new Stock();
        request.setProduct(product);
        request.setWarehouse(emanet);
        request.setQuantity(5);
        request.setCustomerName("Beta");
        request.setCustomerPhone("5559998877");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(warehouseRepository.findById(2L)).thenReturn(Optional.of(emanet));
        when(stockRepository.findByProductAndWarehouseAndCustomerName(product, emanet, "Beta"))
                .thenReturn(Optional.empty());
        when(stockRepository.save(any(Stock.class))).thenAnswer(inv -> {
            Stock s = inv.getArgument(0);
            s.setId(99L);
            return s;
        });

        Stock result = stockService.createStock(request);

        assertNotNull(result);
        assertEquals(5, result.getQuantity());
        assertEquals("Beta", result.getCustomerName());
        verify(auditService, times(1)).log(eq(AuditAction.STOCK_CREATE), anyString(), anyLong(), anyString(),
                anyString(), any());
    }

    @Test
    void createStock_WhenEmanetDepoMissingCustomerName_ShouldThrow() {
        Warehouse emanet = new Warehouse();
        emanet.setId(2L);
        emanet.setName("Emanet Depo");
        emanet.setWarehouseType(WarehouseType.EMANET_DEPO);

        Stock request = new Stock();
        request.setProduct(product);
        request.setWarehouse(emanet);
        request.setQuantity(5);
        request.setCustomerName(null);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(warehouseRepository.findById(2L)).thenReturn(Optional.of(emanet));

        WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                () -> stockService.createStock(request));
        assertEquals(ErrorCode.REQUIRED_FIELD_MISSING, ex.getErrorCode());
        verify(stockRepository, never()).save(any(Stock.class));
    }

    @Test
    void createStock_WhenMergeWithZeroQuantity_ShouldThrow() {
        Stock existing = new Stock();
        existing.setId(1L);
        existing.setProduct(product);
        existing.setWarehouse(warehouse);
        existing.setQuantity(50);

        Stock request = new Stock();
        request.setProduct(product);
        request.setWarehouse(warehouse);
        request.setQuantity(0);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(stockRepository.findByProductAndWarehouse(product, warehouse)).thenReturn(Optional.of(existing));

        WarehouseManagementException ex = assertThrows(WarehouseManagementException.class,
                () -> stockService.createStock(request));
        assertEquals(ErrorCode.REQUIRED_FIELD_MISSING, ex.getErrorCode());
        verify(stockRepository, never()).save(any(Stock.class));
    }

    @Test
    void addToStock_WhenValid_ShouldIncreaseQuantity() {
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(stockRepository.save(any(Stock.class))).thenReturn(stock);

        Stock result = stockService.addToStock(1L, 50);

        assertNotNull(result);
        assertEquals(150, result.getQuantity());
        verify(stockRepository, times(1)).save(any(Stock.class));
    }

    @Test
    void removeFromStock_WhenValid_ShouldDecreaseQuantity() {
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(stockRepository.save(any(Stock.class))).thenReturn(stock);

        Stock result = stockService.removeFromStock(1L, 30);

        assertNotNull(result);
        assertEquals(70, result.getQuantity());
        verify(stockRepository, times(1)).save(any(Stock.class));
    }

    @Test
    void reserveStock_WhenSufficientStock_ShouldReserveStock() {
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(stockRepository.save(any(Stock.class))).thenReturn(stock);

        Stock result = stockService.reserveStock(1L, 20);

        assertNotNull(result);
        assertEquals(20, result.getReservedQuantity());
        verify(stockRepository, times(1)).save(any(Stock.class));
    }

    @Test
    void reserveStock_WhenInsufficientStock_ShouldThrowException() {
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));

        assertThrows(WarehouseManagementException.class, () -> 
            stockService.reserveStock(1L, 150)
        );
    }

    @Test
    void releaseStock_WhenValid_ShouldReleaseStock() {
        stock.setReservedQuantity(50);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(stockRepository.save(any(Stock.class))).thenReturn(stock);

        Stock result = stockService.releaseStock(1L, 30);

        assertNotNull(result);
        assertEquals(20, result.getReservedQuantity());
        verify(stockRepository, times(1)).save(any(Stock.class));
    }

    @Test
    void deleteStock_WhenStockExists_ShouldDeleteStock() {
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));

        stockService.deleteStock(1L);

        verify(stockRepository, times(1)).delete(stock);
    }
}
