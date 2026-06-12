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

    Page<Product> getAllProducts(Pageable pageable, String search, Long categoryId, Long brandId, Long colorId);

    Page<Product> getAllProducts(Pageable pageable, String search, Long categoryId, Long brandId, Long colorId,
                                 com.warehouse.entity.ProductType productType);

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

    /** Color variants linked to a group, excluding the given product (for the admin edit form). */
    List<com.warehouse.dto.ProductDto.VariantSiblingDto> getVariantSiblings(Long variantGroupId, Long excludeProductId);

    /** Active products in a color-variant group (for the storefront swatch row). */
    List<Product> getActiveVariantSiblings(Long variantGroupId);

    Product createProduct(Product product);

    Product updateProduct(Long id, Product productDetails);

    void deleteProduct(Long id);

    BulkDeleteResponse deleteProducts(List<Long> ids);

    void deactivateProduct(Long id);

    void activateProduct(Long id);

    boolean existsBySku(String sku);

    int bulkAdjustPrices(BulkPriceUpdateRequest request);

    // E-commerce storefront methods
    Product getProductBySlug(String slug);

    Page<Product> getAllActiveProducts(Pageable pageable, String search, Long categoryId, Long brandId, Long colorId);

    Page<Product> getAllActiveProducts(Pageable pageable, String search, Long categoryId, Long brandId, Long colorId,
                                       com.warehouse.entity.ProductType productType);

    Page<Product> getAllActiveProductsMultiFilter(Pageable pageable, String search, Long categoryId, List<Long> brandIds, List<Long> colorIds);
}
