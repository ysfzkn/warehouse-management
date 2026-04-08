package com.warehouse.service.impl;

import com.warehouse.dto.BulkDeleteResponse;
import com.warehouse.dto.BulkPriceUpdateRequest;
import com.warehouse.entity.Product;
import com.warehouse.entity.Category;
import com.warehouse.entity.Brand;
import com.warehouse.entity.Color;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.BrandRepository;
import com.warehouse.repository.ColorRepository;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.service.ProductService;
import com.warehouse.util.EntityValidator;
import com.warehouse.constants.BusinessMessages;
import com.warehouse.constants.EntityNames;
import com.warehouse.assistant.core.rag.ProductIndexEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of ProductService for managing products.
 */
@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ColorRepository colorRepository;
    private final StockTransferRepository stockTransferRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProductServiceImpl(ProductRepository productRepository,
                             CategoryRepository categoryRepository,
                             BrandRepository brandRepository,
                             ColorRepository colorRepository,
                             StockTransferRepository stockTransferRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.colorRepository = colorRepository;
        this.stockTransferRepository = stockTransferRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        logger.debug("Fetching all products");
        return productRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> getAllProducts(Pageable pageable) {
        logger.debug("Fetching paged products - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return productRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> getAllProducts(Pageable pageable, String search, Long categoryId, Long brandId, Long colorId) {
        logger.debug("Fetching paged products with filters - page: {}, size: {}, search: {}, categoryId: {}, brandId: {}, colorId: {}",
                pageable.getPageNumber(), pageable.getPageSize(), search, categoryId, brandId, colorId);
        String normalizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        return productRepository.findByFilters(normalizedSearch, categoryId, brandId, colorId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getAllActiveProducts() {
        logger.debug("Fetching all active products");
        return productRepository.findAllActive();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> getAllActiveProducts(Pageable pageable) {
        logger.debug("Fetching paged active products - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return productRepository.findAllActive(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id) {
        logger.debug("Fetching product by id: {}", id);
        return productRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Product getProductByIdOrThrow(Long id) {
        logger.debug("Fetching product by id or throw: {}", id);
        return productRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Product not found with id: {}", id);
                    return new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND, BusinessMessages.ID_PREFIX + id);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> getProductByIdWithStocks(Long id) {
        logger.debug("Fetching product with stocks by id: {}", id);
        return productRepository.findByIdWithStocks(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> getProductByIdWithRelations(Long id) {
        logger.debug("Fetching product with relations by id: {}", id);
        return productRepository.findByIdWithRelations(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> getProductBySku(String sku) {
        logger.debug("Fetching product by SKU: {}", sku);
        return productRepository.findBySku(sku);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(Long categoryId) {
        logger.debug("Fetching products by category id: {}", categoryId);
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> {
                    logger.warn("Category not found with id: {}", categoryId);
                    return new WarehouseManagementException(ErrorCode.CATEGORY_NOT_FOUND, BusinessMessages.ID_PREFIX + categoryId);
                });
        return productRepository.findByCategoryAndActive(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> searchProductsByName(String name) {
        logger.debug("Searching products by name: {}", name);
        return productRepository.findByNameContainingIgnoreCaseAndActive(name);
    }

    @Override
    public Product createProduct(Product product) {
        logger.info("Creating new product: {}", product.getName());
        EntityValidator.validateProductForCreation(product);

        Category category = categoryRepository.findById(product.getCategory().getId())
                .orElseThrow(() -> {
                    logger.warn("Category not found with id: {}", product.getCategory().getId());
                    return new WarehouseManagementException(ErrorCode.CATEGORY_NOT_FOUND, BusinessMessages.ID_PREFIX + product.getCategory().getId());
                });

        validateSkuUniqueness(product.getSku());

        product.setCategory(category);
        setBrandIfPresent(product);
        setColorIfPresent(product);
        if (product.getSlug() == null || product.getSlug().isBlank()) {
            product.setSlug(product.getSku().toLowerCase()
                .replace(" ", "-").replaceAll("[^a-z0-9\\-]", ""));
        }

        Product saved = productRepository.save(product);
        logger.info("Product created successfully with id: {}", saved.getId());
        eventPublisher.publishEvent(ProductIndexEvent.upsert(saved.getId()));
        return productRepository.findByIdWithRelations(saved.getId()).orElse(saved);
    }

    @Override
    public Product updateProduct(Long id, Product productDetails) {
        logger.info("Updating product with id: {}", id);
        Product product = getProductByIdOrThrow(id);

        updateCategory(product, productDetails);
        updateBrand(product, productDetails);
        updateColor(product, productDetails);
        validateSkuUniquenessOnUpdate(product, productDetails);
        updateProductFields(product, productDetails);

        Product saved = productRepository.save(product);
        logger.info("Product updated successfully with id: {}", saved.getId());
        eventPublisher.publishEvent(ProductIndexEvent.upsert(saved.getId()));
        return productRepository.findByIdWithRelations(saved.getId()).orElse(saved);
    }

    @Override
    public void deleteProduct(Long id) {
        logger.info("Deleting product with id: {}", id);
        Product product = getProductByIdOrThrow(id);
        // Önce stok ilişkilerini kontrol et
        EntityValidator.validateEntityHasNoRelations(
            !product.getStocks().isEmpty(), EntityNames.PRODUCT, EntityNames.RELATION_STOCKS
        );

        // Ardından transfer ilişkilerini kontrol et (hem aktif hem geçmiş)
        var transfersUsingProduct = stockTransferRepository.findByProduct(product);
        if (transfersUsingProduct != null && !transfersUsingProduct.isEmpty()) {
            logger.warn("Cannot delete product with id {} because it is used in {} stock transfers",
                    id, transfersUsingProduct.size());
            throw new WarehouseManagementException(
                    ErrorCode.CANNOT_DELETE_PRODUCT_WITH_TRANSFERS,
                    "Bu ürün en az bir stok transferinde kullanılmıştır. Geçmiş transfer kayıtları silinmeden ürün silinemez."
            );
        }

        productRepository.delete(product);
        logger.info("Product deleted successfully with id: {}", id);
        eventPublisher.publishEvent(ProductIndexEvent.delete(id));
    }

    @Override
    public BulkDeleteResponse deleteProducts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            logger.warn("Attempted to delete products with empty list");
            return new BulkDeleteResponse(0, 0, List.of());
        }
        
        logger.info("Deleting {} products", ids.size());
        List<BulkDeleteResponse.DeleteError> errors = new java.util.ArrayList<>();
        int successCount = 0;
        
        for (Long id : ids) {
            try {
                Product product = getProductByIdOrThrow(id);
                
                // Önce stok ilişkilerini kontrol et
                if (!product.getStocks().isEmpty()) {
                    errors.add(new BulkDeleteResponse.DeleteError(
                        id,
                        product.getName(),
                        product.getSku(),
                        ErrorCode.CANNOT_DELETE_WITH_STOCKS.getCode(),
                        "Bu ürün stok kayıtlarında kullanılmaktadır. Stok kayıtları silinmeden ürün silinemez."
                    ));
                    continue;
                }
                
                // Ardından transfer ilişkilerini kontrol et
                var transfersUsingProduct = stockTransferRepository.findByProduct(product);
                if (transfersUsingProduct != null && !transfersUsingProduct.isEmpty()) {
                    errors.add(new BulkDeleteResponse.DeleteError(
                        id,
                        product.getName(),
                        product.getSku(),
                        ErrorCode.CANNOT_DELETE_PRODUCT_WITH_TRANSFERS.getCode(),
                        "Bu ürün en az bir stok transferinde kullanılmıştır. Geçmiş transfer kayıtları silinmeden ürün silinemez."
                    ));
                    continue;
                }
                
                productRepository.delete(product);
                successCount++;
                eventPublisher.publishEvent(ProductIndexEvent.delete(id));
                logger.debug("Product deleted successfully with id: {}", id);
            } catch (WarehouseManagementException e) {
                // Domain exception'ları yakala
                Product product = null;
                try {
                    product = getProductByIdOrThrow(id);
                } catch (Exception ex) {
                    // Ürün bulunamadı
                }
                errors.add(new BulkDeleteResponse.DeleteError(
                    id,
                    product != null ? product.getName() : "Bilinmeyen Ürün",
                    product != null ? product.getSku() : "N/A",
                    e.getErrorCode().getCode(),
                    e.getMessage()
                ));
                logger.warn("Cannot delete product with id {}: {}", id, e.getMessage());
            } catch (Exception e) {
                // Diğer hatalar
                Product product = null;
                try {
                    product = getProductByIdOrThrow(id);
                } catch (Exception ex) {
                    // Ürün bulunamadı
                }
                errors.add(new BulkDeleteResponse.DeleteError(
                    id,
                    product != null ? product.getName() : "Bilinmeyen Ürün",
                    product != null ? product.getSku() : "N/A",
                    ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                    "Ürün silinirken beklenmeyen bir hata oluştu: " + e.getMessage()
                ));
                logger.error("Error deleting product {}: {}", id, e.getMessage(), e);
            }
        }
        
        logger.info("Batch delete completed: {} successful, {} errors", successCount, errors.size());
        return new BulkDeleteResponse(successCount, errors.size(), errors);
    }

    @Override
    public void deactivateProduct(Long id) {
        logger.info("Deactivating product with id: {}", id);
        updateProductStatus(id, false);
    }

    @Override
    public void activateProduct(Long id) {
        logger.info("Activating product with id: {}", id);
        updateProductStatus(id, true);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySku(String sku) {
        return productRepository.existsBySku(sku);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> filterProductsByBrandAndColor(Long brandId, Long colorId) {
        logger.debug("Filtering products by brand id: {} and color id: {}", brandId, colorId);
        Brand brand = findBrandIfPresent(brandId);
        Color color = findColorIfPresent(colorId);
        return productRepository.findActiveByBrandAndColor(brand, color);
    }

    @Override
    public int bulkAdjustPrices(BulkPriceUpdateRequest request) {
        logger.info("Starting optimized bulk price adjustment");
        validateBulkRequest(request);
        
        boolean isIncrease = "INCREASE".equalsIgnoreCase(request.getDirection());
        boolean isPercentage = "PERCENTAGE".equalsIgnoreCase(request.getMode());
        BigDecimal value = request.getValue();

        int updatedCount;
        
        // Use JPQL bulk update for maximum performance
        // This executes a single UPDATE query directly in the database
        try {
            if (isPercentage) {
                if (isIncrease) {
                    updatedCount = productRepository.bulkUpdatePriceByPercentage(
                        value, 
                        request.getCategoryId(), 
                        request.getBrandId(), 
                        request.getColorId(), 
                        request.isOnlyActive()
                    );
                } else {
                    updatedCount = productRepository.bulkUpdatePriceByPercentageDecrease(
                        value, 
                        request.getCategoryId(), 
                        request.getBrandId(), 
                        request.getColorId(), 
                        request.isOnlyActive()
                    );
                }
            } else {
                if (isIncrease) {
                    updatedCount = productRepository.bulkUpdatePriceByAmount(
                        value, 
                        request.getCategoryId(), 
                        request.getBrandId(), 
                        request.getColorId(), 
                        request.isOnlyActive()
                    );
                } else {
                    updatedCount = productRepository.bulkUpdatePriceByAmountDecrease(
                        value, 
                        request.getCategoryId(), 
                        request.getBrandId(), 
                        request.getColorId(), 
                        request.isOnlyActive()
                    );
                }
            }
            
            logger.info("Optimized bulk price adjustment completed. Updated {} products", updatedCount);
            return updatedCount;
            
        } catch (Exception e) {
            logger.error("Error during bulk price update, falling back to batch processing", e);
            return bulkAdjustPricesWithBatchProcessing(request, isIncrease, isPercentage, value);
        }
    }

    /**
     * Fallback method using batch processing for bulk price updates.
     * Used when JPQL bulk update fails.
     */
    private int bulkAdjustPricesWithBatchProcessing(BulkPriceUpdateRequest request, 
                                                     boolean isIncrease, 
                                                     boolean isPercentage, 
                                                     BigDecimal value) {
        logger.info("Using batch processing for bulk price adjustment");
        
        // Use lightweight query without EntityGraph to reduce memory footprint
        List<Product> targets = productRepository.findByOptionalFiltersLightweight(
                request.getCategoryId(), request.getBrandId(), request.getColorId(), request.isOnlyActive()
        );
        
        if (targets.isEmpty()) {
            logger.warn(BusinessMessages.NO_PRODUCTS_FOR_BULK);
            return 0;
        }

        // Process in batches to avoid memory issues with large datasets
        int batchSize = 500; // Process 500 products at a time
        int totalUpdated = 0;
        
        for (int i = 0; i < targets.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, targets.size());
            List<Product> batch = targets.subList(i, endIndex);
            
            for (Product product : batch) {
                BigDecimal current = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                BigDecimal updated = calculateNewPrice(current, value, isIncrease, isPercentage);
                product.setPrice(updated.setScale(2, RoundingMode.HALF_UP));
            }
            
            productRepository.saveAll(batch);
            totalUpdated += batch.size();
            
            logger.debug("Processed batch {}/{}, total updated: {}", 
                        (i / batchSize) + 1, 
                        (targets.size() + batchSize - 1) / batchSize, 
                        totalUpdated);
        }

        logger.info("Batch processing completed. Updated {} products", totalUpdated);
        return totalUpdated;
    }

    @Override
    @Transactional(readOnly = true)
    public Product getProductBySlug(String slug) {
        return productRepository.findBySlug(slug)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND, "Slug: " + slug));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> getAllActiveProducts(Pageable pageable, String search, Long categoryId, Long brandId, Long colorId) {
        return productRepository.findActiveByFilters(search, categoryId, brandId, colorId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> getAllActiveProductsMultiFilter(Pageable pageable, String search, Long categoryId, java.util.List<Long> brandIds, java.util.List<Long> colorIds) {
        return productRepository.findActiveByMultiFilters(search, categoryId, brandIds, colorIds, pageable);
    }

    private void validateSkuUniqueness(String sku) {
        if (productRepository.existsBySku(sku)) {
            logger.warn("SKU already exists: {}", sku);
            throw new WarehouseManagementException(ErrorCode.PRODUCT_SKU_ALREADY_EXISTS, BusinessMessages.SKU_PREFIX + sku);
        }
    }

    private void validateSkuUniquenessOnUpdate(Product product, Product productDetails) {
        if (!product.getSku().equals(productDetails.getSku()) &&
            productRepository.existsBySku(productDetails.getSku())) {
            logger.warn("SKU already exists for update: {}", productDetails.getSku());
            throw new WarehouseManagementException(ErrorCode.PRODUCT_SKU_ALREADY_EXISTS, BusinessMessages.SKU_PREFIX + productDetails.getSku());
        }
    }

    private void setBrandIfPresent(Product product) {
        if (product.getBrand() != null && product.getBrand().getId() != null) {
            Brand brand = brandRepository.findById(product.getBrand().getId())
                    .orElseThrow(() -> {
                        logger.warn("Brand not found with id: {}", product.getBrand().getId());
                        return new WarehouseManagementException(ErrorCode.BRAND_NOT_FOUND, BusinessMessages.ID_PREFIX + product.getBrand().getId());
                    });
            product.setBrand(brand);
        } else {
            product.setBrand(null);
        }
    }

    private void setColorIfPresent(Product product) {
        if (product.getColor() != null && product.getColor().getId() != null) {
            Color color = colorRepository.findById(product.getColor().getId())
                    .orElseThrow(() -> {
                        logger.warn("Color not found with id: {}", product.getColor().getId());
                        return new WarehouseManagementException(ErrorCode.COLOR_NOT_FOUND, BusinessMessages.ID_PREFIX + product.getColor().getId());
                    });
            product.setColor(color);
        } else {
            product.setColor(null);
        }
    }

    private void updateCategory(Product product, Product productDetails) {
        if (productDetails.getCategory() != null &&
            productDetails.getCategory().getId() != null &&
            !product.getCategory().getId().equals(productDetails.getCategory().getId())) {
            Category category = categoryRepository.findById(productDetails.getCategory().getId())
                    .orElseThrow(() -> {
                        logger.warn("Category not found with id: {}", productDetails.getCategory().getId());
                        return new WarehouseManagementException(ErrorCode.CATEGORY_NOT_FOUND, BusinessMessages.ID_PREFIX + productDetails.getCategory().getId());
                    });
            product.setCategory(category);
        }
    }

    private void updateBrand(Product product, Product productDetails) {
        if (productDetails.getBrand() != null && productDetails.getBrand().getId() != null) {
            if (product.getBrand() == null || !product.getBrand().getId().equals(productDetails.getBrand().getId())) {
                Brand brand = brandRepository.findById(productDetails.getBrand().getId())
                        .orElseThrow(() -> {
                            logger.warn("Brand not found with id: {}", productDetails.getBrand().getId());
                            return new WarehouseManagementException(ErrorCode.BRAND_NOT_FOUND, BusinessMessages.ID_PREFIX + productDetails.getBrand().getId());
                        });
                product.setBrand(brand);
            }
        } else {
            product.setBrand(null);
        }
    }

    private void updateColor(Product product, Product productDetails) {
        if (productDetails.getColor() != null && productDetails.getColor().getId() != null) {
            if (product.getColor() == null || !product.getColor().getId().equals(productDetails.getColor().getId())) {
                Color color = colorRepository.findById(productDetails.getColor().getId())
                        .orElseThrow(() -> {
                            logger.warn("Color not found with id: {}", productDetails.getColor().getId());
                            return new WarehouseManagementException(ErrorCode.COLOR_NOT_FOUND, BusinessMessages.ID_PREFIX + productDetails.getColor().getId());
                        });
                product.setColor(color);
            }
        } else {
            product.setColor(null);
        }
    }

    private void updateProductFields(Product product, Product productDetails) {
        product.setName(productDetails.getName());
        product.setDescription(productDetails.getDescription());
        product.setShortDescription(productDetails.getShortDescription());
        product.setSku(productDetails.getSku());
        product.setPrice(productDetails.getPrice());
        product.setSalePrice(productDetails.getSalePrice());
        product.setSaleStart(productDetails.getSaleStart());
        product.setSaleEnd(productDetails.getSaleEnd());
        product.setFeatured(productDetails.isFeatured());
        product.setNew(productDetails.isNew());
        product.setWeight(productDetails.getWeight());
        product.setDimensions(productDetails.getDimensions());
        product.setLengthCm(productDetails.getLengthCm());
        product.setWidthCm(productDetails.getWidthCm());
        product.setHeightCm(productDetails.getHeightCm());
        product.setShippingRate(productDetails.getShippingRate());
        product.setVatRate(productDetails.getVatRate());
        product.setSctRate(productDetails.getSctRate());
        product.setActive(productDetails.isActive());
        if (productDetails.getSlug() != null) product.setSlug(productDetails.getSlug());
        if (productDetails.getMetaTitle() != null) product.setMetaTitle(productDetails.getMetaTitle());
        if (productDetails.getMetaDescription() != null) product.setMetaDescription(productDetails.getMetaDescription());
    }

    private void updateProductStatus(Long id, boolean isActive) {
        Product product = getProductByIdOrThrow(id);
        product.setActive(isActive);
        productRepository.save(product);
        logger.debug("Product status updated. Id: {}, Active: {}", id, isActive);
    }

    private Brand findBrandIfPresent(Long brandId) {
        if (brandId != null) {
            return brandRepository.findById(brandId)
                    .orElseThrow(() -> {
                        logger.warn("Brand not found with id: {}", brandId);
                        return new WarehouseManagementException(ErrorCode.BRAND_NOT_FOUND, "ID: " + brandId);
                    });
        }
        return null;
    }

    private Color findColorIfPresent(Long colorId) {
        if (colorId != null) {
            return colorRepository.findById(colorId)
                    .orElseThrow(() -> {
                        logger.warn("Color not found with id: {}", colorId);
                        return new WarehouseManagementException(ErrorCode.COLOR_NOT_FOUND, "ID: " + colorId);
                    });
        }
        return null;
    }

    private BigDecimal calculateNewPrice(BigDecimal current, BigDecimal value, boolean isIncrease, boolean isPercentage) {
        if (isPercentage) {
            BigDecimal factor = value.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            BigDecimal multiplier = isIncrease ? BigDecimal.ONE.add(factor) : BigDecimal.ONE.subtract(factor);
            return current.multiply(multiplier);
        } else {
            BigDecimal delta = value;
            BigDecimal result = isIncrease ? current.add(delta) : current.subtract(delta);
            return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
        }
    }

    private void validateBulkRequest(BulkPriceUpdateRequest request) {
        if (request.getMode() == null || request.getValue() == null || request.getDirection() == null) {
            throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING, BusinessMessages.REQUIRED_MODE_VALUE_DIRECTION);
        }
        if (!"PERCENTAGE".equalsIgnoreCase(request.getMode()) && !"AMOUNT".equalsIgnoreCase(request.getMode())) {
            throw new WarehouseManagementException(ErrorCode.INVALID_VALUE, BusinessMessages.INVALID_MODE);
        }
        if (!"INCREASE".equalsIgnoreCase(request.getDirection()) && !"DECREASE".equalsIgnoreCase(request.getDirection())) {
            throw new WarehouseManagementException(ErrorCode.INVALID_VALUE, BusinessMessages.INVALID_DIRECTION);
        }
        if (request.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new WarehouseManagementException(ErrorCode.VALUE_MUST_BE_POSITIVE, BusinessMessages.VALUE_MUST_BE_POSITIVE);
        }
        if ("PERCENTAGE".equalsIgnoreCase(request.getMode()) && request.getValue().compareTo(BigDecimal.valueOf(1000)) > 0) {
            throw new WarehouseManagementException(ErrorCode.INVALID_VALUE, BusinessMessages.PERCENTAGE_TOO_HIGH);
        }
    }
}

