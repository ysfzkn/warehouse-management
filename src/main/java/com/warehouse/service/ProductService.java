package com.warehouse.service;

import com.warehouse.dto.BulkDeleteResponse;
import com.warehouse.dto.BulkPriceUpdateRequest;
import com.warehouse.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing products.
 */
public interface ProductService {

    List<Product> getAllProducts();

    Page<Product> getAllProducts(Pageable pageable);

    List<Product> getAllActiveProducts();

    Page<Product> getAllActiveProducts(Pageable pageable);

    Optional<Product> getProductById(Long id);

    Product getProductByIdOrThrow(Long id);

    Optional<Product> getProductByIdWithStocks(Long id);

    Optional<Product> getProductByIdWithRelations(Long id);

    Optional<Product> getProductBySku(String sku);

    List<Product> getProductsByCategory(Long categoryId);

    List<Product> searchProductsByName(String name);

    List<Product> filterProductsByBrandAndColor(Long brandId, Long colorId);

    Product createProduct(Product product);

    Product updateProduct(Long id, Product productDetails);

    void deleteProduct(Long id);

    BulkDeleteResponse deleteProducts(List<Long> ids);

    void deactivateProduct(Long id);

    void activateProduct(Long id);

    boolean existsBySku(String sku);

    int bulkAdjustPrices(BulkPriceUpdateRequest request);
}
