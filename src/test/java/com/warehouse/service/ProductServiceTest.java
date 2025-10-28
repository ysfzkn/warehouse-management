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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private ProductService productService;

    private Product product;
    private Category category;
    private Brand brand;
    private Color color;

    @BeforeEach
    void setUp() {
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
}
