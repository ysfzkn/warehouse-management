package com.warehouse.service;

import com.warehouse.entity.Brand;
import com.warehouse.entity.Category;
import com.warehouse.entity.Color;
import com.warehouse.entity.Product;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.BrandRepository;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.ColorRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.repository.VariantGroupRepository;
import com.warehouse.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private ColorRepository colorRepository;

    @Mock
    private StockTransferRepository stockTransferRepository;

    @Mock
    private VariantGroupRepository variantGroupRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProductService productService;

    private Product product;
    private Category category;
    private Brand brand;
    private Color color;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(
                productRepository,
                categoryRepository,
                brandRepository,
                colorRepository,
                stockTransferRepository,
                variantGroupRepository,
                eventPublisher
        );
        category = new Category();
        category.setId(1L);
        category.setName("Electronics");

        brand = new Brand();
        brand.setId(1L);
        brand.setName("Samsung");

        color = new Color();
        color.setId(1L);
        color.setName("Black");

        product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        product.setSku("TEST-001");
        product.setPrice(BigDecimal.valueOf(100.00));
        product.setCategory(category);
        product.setBrand(brand);
        product.setColor(color);
        product.setStocks(new ArrayList<>());
    }

    @Test
    void getAllProducts_ShouldReturnAllProducts() {
        List<Product> products = Arrays.asList(product);
        when(productRepository.findAll()).thenReturn(products);

        List<Product> result = productService.getAllProducts();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(productRepository, times(1)).findAll();
    }

    @Test
    void getAllActiveProducts_ShouldReturnActiveProducts() {
        List<Product> products = Arrays.asList(product);
        when(productRepository.findAllActive()).thenReturn(products);

        List<Product> result = productService.getAllActiveProducts();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(productRepository, times(1)).findAllActive();
    }

    @Test
    void getProductByIdOrThrow_WhenProductExists_ShouldReturnProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Product result = productService.getProductByIdOrThrow(1L);

        assertNotNull(result);
        assertEquals("Test Product", result.getName());
        verify(productRepository, times(1)).findById(1L);
    }

    @Test
    void getProductByIdOrThrow_WhenProductNotExists_ShouldThrowException() {
        when(productRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(WarehouseManagementException.class, () -> 
            productService.getProductByIdOrThrow(999L)
        );
    }

    @Test
    void createProduct_WhenValid_ShouldCreateProduct() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));
        when(colorRepository.findById(1L)).thenReturn(Optional.of(color));
        when(productRepository.existsBySku("TEST-001")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(product);

        Product result = productService.createProduct(product);

        assertNotNull(result);
        assertEquals("Test Product", result.getName());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void createProduct_WhenSkuExists_ShouldThrowException() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.existsBySku("TEST-001")).thenReturn(true);

        assertThrows(WarehouseManagementException.class, () -> 
            productService.createProduct(product)
        );
    }

    @Test
    void createProduct_WhenCategoryNotFound_ShouldThrowException() {
        when(categoryRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(WarehouseManagementException.class, () -> 
            productService.createProduct(product)
        );
    }

    @Test
    void updateProduct_WhenValid_ShouldUpdateProduct() {
        Product updatedProduct = new Product();
        updatedProduct.setName("Updated Product");
        updatedProduct.setSku("TEST-001");
        updatedProduct.setPrice(BigDecimal.valueOf(150.00));
        updatedProduct.setCategory(category);
        updatedProduct.setBrand(brand);
        updatedProduct.setColor(color);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        Product result = productService.updateProduct(1L, updatedProduct);

        assertNotNull(result);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void updateProduct_WhenProductNotFound_ShouldThrowException() {
        when(productRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(WarehouseManagementException.class, () -> 
            productService.updateProduct(999L, product)
        );
    }

    @Test
    void deleteProduct_WhenProductExists_ShouldDeleteProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productService.deleteProduct(1L);

        verify(productRepository, times(1)).delete(product);
    }

    @Test
    void activateProduct_ShouldActivateProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        productService.activateProduct(1L);

        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void deactivateProduct_ShouldDeactivateProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        productService.deactivateProduct(1L);

        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void filterProductsByBrandAndColor_ShouldReturnFilteredProducts() {
        List<Product> products = Arrays.asList(product);
        when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));
        when(colorRepository.findById(1L)).thenReturn(Optional.of(color));
        when(productRepository.findActiveByBrandAndColor(brand, color)).thenReturn(products);

        List<Product> result = productService.filterProductsByBrandAndColor(1L, 1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(productRepository, times(1)).findActiveByBrandAndColor(brand, color);
    }

    // ==================== BULK PRICE UPDATE TESTS ====================

    @Test
    void bulkAdjustPrices_PercentageIncrease_ShouldUseJPQLBulkUpdate() {
        com.warehouse.dto.BulkPriceUpdateRequest request = new com.warehouse.dto.BulkPriceUpdateRequest();
        request.setMode("PERCENTAGE");
        request.setDirection("INCREASE");
        request.setValue(BigDecimal.valueOf(10));
        request.setOnlyActive(true);
        request.setCategoryId(1L);

        when(productRepository.bulkUpdatePriceByPercentage(
            BigDecimal.valueOf(10), 1L, null, null, true
        )).thenReturn(150);

        int result = productService.bulkAdjustPrices(request);

        assertEquals(150, result);
        verify(productRepository, times(1)).bulkUpdatePriceByPercentage(
            BigDecimal.valueOf(10), 1L, null, null, true
        );
        // Verify that lightweight method is NOT called (JPQL used instead)
        verify(productRepository, never()).findByOptionalFiltersLightweight(any(), any(), any(), anyBoolean());
    }

    @Test
    void bulkAdjustPrices_PercentageDecrease_ShouldUseJPQLBulkUpdate() {
        com.warehouse.dto.BulkPriceUpdateRequest request = new com.warehouse.dto.BulkPriceUpdateRequest();
        request.setMode("PERCENTAGE");
        request.setDirection("DECREASE");
        request.setValue(BigDecimal.valueOf(15));
        request.setOnlyActive(true);

        when(productRepository.bulkUpdatePriceByPercentageDecrease(
            BigDecimal.valueOf(15), null, null, null, true
        )).thenReturn(200);

        int result = productService.bulkAdjustPrices(request);

        assertEquals(200, result);
        verify(productRepository, times(1)).bulkUpdatePriceByPercentageDecrease(
            BigDecimal.valueOf(15), null, null, null, true
        );
    }

    @Test
    void bulkAdjustPrices_AmountIncrease_ShouldUseJPQLBulkUpdate() {
        com.warehouse.dto.BulkPriceUpdateRequest request = new com.warehouse.dto.BulkPriceUpdateRequest();
        request.setMode("AMOUNT");
        request.setDirection("INCREASE");
        request.setValue(BigDecimal.valueOf(50));
        request.setOnlyActive(true);
        request.setBrandId(2L);

        when(productRepository.bulkUpdatePriceByAmount(
            BigDecimal.valueOf(50), null, 2L, null, true
        )).thenReturn(75);

        int result = productService.bulkAdjustPrices(request);

        assertEquals(75, result);
        verify(productRepository, times(1)).bulkUpdatePriceByAmount(
            BigDecimal.valueOf(50), null, 2L, null, true
        );
    }

    @Test
    void bulkAdjustPrices_AmountDecrease_ShouldUseJPQLBulkUpdate() {
        com.warehouse.dto.BulkPriceUpdateRequest request = new com.warehouse.dto.BulkPriceUpdateRequest();
        request.setMode("AMOUNT");
        request.setDirection("DECREASE");
        request.setValue(BigDecimal.valueOf(25));
        request.setOnlyActive(false);
        request.setColorId(3L);

        when(productRepository.bulkUpdatePriceByAmountDecrease(
            BigDecimal.valueOf(25), null, null, 3L, false
        )).thenReturn(100);

        int result = productService.bulkAdjustPrices(request);

        assertEquals(100, result);
        verify(productRepository, times(1)).bulkUpdatePriceByAmountDecrease(
            BigDecimal.valueOf(25), null, null, 3L, false
        );
    }

    @Test
    void bulkAdjustPrices_WithAllFilters_ShouldPassAllParameters() {
        com.warehouse.dto.BulkPriceUpdateRequest request = new com.warehouse.dto.BulkPriceUpdateRequest();
        request.setMode("PERCENTAGE");
        request.setDirection("INCREASE");
        request.setValue(BigDecimal.valueOf(20));
        request.setOnlyActive(true);
        request.setCategoryId(1L);
        request.setBrandId(2L);
        request.setColorId(3L);

        when(productRepository.bulkUpdatePriceByPercentage(
            BigDecimal.valueOf(20), 1L, 2L, 3L, true
        )).thenReturn(50);

        int result = productService.bulkAdjustPrices(request);

        assertEquals(50, result);
        verify(productRepository, times(1)).bulkUpdatePriceByPercentage(
            BigDecimal.valueOf(20), 1L, 2L, 3L, true
        );
    }

    @Test
    void bulkAdjustPrices_InvalidMode_ShouldThrowException() {
        com.warehouse.dto.BulkPriceUpdateRequest request = new com.warehouse.dto.BulkPriceUpdateRequest();
        request.setMode("INVALID");
        request.setDirection("INCREASE");
        request.setValue(BigDecimal.valueOf(10));

        assertThrows(WarehouseManagementException.class, () -> 
            productService.bulkAdjustPrices(request)
        );
    }

    @Test
    void bulkAdjustPrices_InvalidDirection_ShouldThrowException() {
        com.warehouse.dto.BulkPriceUpdateRequest request = new com.warehouse.dto.BulkPriceUpdateRequest();
        request.setMode("PERCENTAGE");
        request.setDirection("INVALID");
        request.setValue(BigDecimal.valueOf(10));

        assertThrows(WarehouseManagementException.class, () -> 
            productService.bulkAdjustPrices(request)
        );
    }

    @Test
    void bulkAdjustPrices_NegativeValue_ShouldThrowException() {
        com.warehouse.dto.BulkPriceUpdateRequest request = new com.warehouse.dto.BulkPriceUpdateRequest();
        request.setMode("PERCENTAGE");
        request.setDirection("INCREASE");
        request.setValue(BigDecimal.valueOf(-10));

        assertThrows(WarehouseManagementException.class, () -> 
            productService.bulkAdjustPrices(request)
        );
    }

    @Test
    void bulkAdjustPrices_PercentageTooHigh_ShouldThrowException() {
        com.warehouse.dto.BulkPriceUpdateRequest request = new com.warehouse.dto.BulkPriceUpdateRequest();
        request.setMode("PERCENTAGE");
        request.setDirection("INCREASE");
        request.setValue(BigDecimal.valueOf(1500)); // > 1000

        assertThrows(WarehouseManagementException.class, () -> 
            productService.bulkAdjustPrices(request)
        );
    }

    @Test
    void bulkAdjustPrices_NullParameters_ShouldThrowException() {
        com.warehouse.dto.BulkPriceUpdateRequest request = new com.warehouse.dto.BulkPriceUpdateRequest();
        // Missing required fields

        assertThrows(WarehouseManagementException.class, () -> 
            productService.bulkAdjustPrices(request)
        );
    }

    @Test
    void bulkAdjustPrices_WhenJPQLFails_ShouldFallbackToBatchProcessing() {
        com.warehouse.dto.BulkPriceUpdateRequest request = new com.warehouse.dto.BulkPriceUpdateRequest();
        request.setMode("PERCENTAGE");
        request.setDirection("INCREASE");
        request.setValue(BigDecimal.valueOf(10));
        request.setOnlyActive(true);

        // Simulate JPQL failure
        when(productRepository.bulkUpdatePriceByPercentage(any(), any(), any(), any(), anyBoolean()))
            .thenThrow(new RuntimeException("Database error"));

        // Mock fallback batch processing
        Product product1 = new Product();
        product1.setId(1L);
        product1.setPrice(BigDecimal.valueOf(100));

        Product product2 = new Product();
        product2.setId(2L);
        product2.setPrice(BigDecimal.valueOf(200));

        List<Product> products = Arrays.asList(product1, product2);
        when(productRepository.findByOptionalFiltersLightweight(null, null, null, true))
            .thenReturn(products);
        when(productRepository.saveAll(anyList())).thenReturn(products);

        int result = productService.bulkAdjustPrices(request);

        assertEquals(2, result);
        verify(productRepository, times(1)).findByOptionalFiltersLightweight(null, null, null, true);
        verify(productRepository, times(1)).saveAll(anyList());
    }
}
