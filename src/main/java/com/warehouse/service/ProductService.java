package com.warehouse.service;

import com.warehouse.entity.Product;
import com.warehouse.dto.BulkPriceUpdateRequest;
import com.warehouse.entity.Category;
import com.warehouse.entity.Brand;
import com.warehouse.entity.Color;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.BrandRepository;
import com.warehouse.repository.ColorRepository;
import com.warehouse.util.EntityValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ColorRepository colorRepository;

    public ProductService(ProductRepository productRepository, 
                         CategoryRepository categoryRepository,
                         BrandRepository brandRepository, 
                         ColorRepository colorRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.colorRepository = colorRepository;
    }

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Product> getAllActiveProducts() {
        return productRepository.findAllActive();
    }

    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Product getProductByIdOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND, "ID: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<Product> getProductByIdWithStocks(Long id) {
        return productRepository.findByIdWithStocks(id);
    }

    @Transactional(readOnly = true)
    public Optional<Product> getProductBySku(String sku) {
        return productRepository.findBySku(sku);
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.CATEGORY_NOT_FOUND, "ID: " + categoryId));
        return productRepository.findByCategoryAndActive(category);
    }

    @Transactional(readOnly = true)
    public List<Product> searchProductsByName(String name) {
        return productRepository.findByNameContainingIgnoreCaseAndActive(name);
    }

    public Product createProduct(Product product) {
        EntityValidator.validateProductForCreation(product);
        
        Category category = categoryRepository.findById(product.getCategory().getId())
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.CATEGORY_NOT_FOUND, "ID: " + product.getCategory().getId()));

        checkSkuDuplication(product.getSku());
        
        product.setCategory(category);
        setBrandIfPresent(product);
        setColorIfPresent(product);
        
        return productRepository.save(product);
    }

    public Product updateProduct(Long id, Product productDetails) {
        Product product = getProductByIdOrThrow(id);
        
        updateCategory(product, productDetails);
        updateBrand(product, productDetails);
        updateColor(product, productDetails);
        checkSkuDuplicationOnUpdate(product, productDetails);
        updateProductFields(product, productDetails);
        
        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        Product product = getProductByIdOrThrow(id);
        EntityValidator.validateEntityHasNoRelations(
            !product.getStocks().isEmpty(), "Product", "stocks"
        );
        productRepository.delete(product);
    }

    public void deactivateProduct(Long id) {
        updateProductStatus(id, false);
    }

    public void activateProduct(Long id) {
        updateProductStatus(id, true);
    }

    @Transactional(readOnly = true)
    public boolean existsBySku(String sku) {
        return productRepository.existsBySku(sku);
    }

    @Transactional(readOnly = true)
    public List<Product> filterProductsByBrandAndColor(Long brandId, Long colorId) {
        Brand brand = findBrandIfPresent(brandId);
        Color color = findColorIfPresent(colorId);
        return productRepository.findActiveByBrandAndColor(brand, color);
    }

    private void checkSkuDuplication(String sku) {
        if (productRepository.existsBySku(sku)) {
            throw new WarehouseManagementException(ErrorCode.PRODUCT_SKU_ALREADY_EXISTS, "SKU: " + sku);
        }
    }

    private void checkSkuDuplicationOnUpdate(Product product, Product productDetails) {
        if (!product.getSku().equals(productDetails.getSku()) && 
            productRepository.existsBySku(productDetails.getSku())) {
            throw new WarehouseManagementException(ErrorCode.PRODUCT_SKU_ALREADY_EXISTS, "SKU: " + productDetails.getSku());
        }
    }

    private void setBrandIfPresent(Product product) {
        if (product.getBrand() != null && product.getBrand().getId() != null) {
            Brand brand = brandRepository.findById(product.getBrand().getId())
                    .orElseThrow(() -> new WarehouseManagementException(ErrorCode.BRAND_NOT_FOUND, "ID: " + product.getBrand().getId()));
            product.setBrand(brand);
        } else {
            product.setBrand(null);
        }
    }

    private void setColorIfPresent(Product product) {
        if (product.getColor() != null && product.getColor().getId() != null) {
            Color color = colorRepository.findById(product.getColor().getId())
                    .orElseThrow(() -> new WarehouseManagementException(ErrorCode.COLOR_NOT_FOUND, "ID: " + product.getColor().getId()));
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
                    .orElseThrow(() -> new WarehouseManagementException(ErrorCode.CATEGORY_NOT_FOUND, "ID: " + productDetails.getCategory().getId()));
            product.setCategory(category);
        }
    }

    private void updateBrand(Product product, Product productDetails) {
        if (productDetails.getBrand() != null && productDetails.getBrand().getId() != null) {
            if (product.getBrand() == null || !product.getBrand().getId().equals(productDetails.getBrand().getId())) {
                Brand brand = brandRepository.findById(productDetails.getBrand().getId())
                        .orElseThrow(() -> new WarehouseManagementException(ErrorCode.BRAND_NOT_FOUND, "ID: " + productDetails.getBrand().getId()));
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
                        .orElseThrow(() -> new WarehouseManagementException(ErrorCode.COLOR_NOT_FOUND, "ID: " + productDetails.getColor().getId()));
                product.setColor(color);
            }
        } else {
            product.setColor(null);
        }
    }

    private void updateProductFields(Product product, Product productDetails) {
        product.setName(productDetails.getName());
        product.setDescription(productDetails.getDescription());
        product.setSku(productDetails.getSku());
        product.setPrice(productDetails.getPrice());
        product.setWeight(productDetails.getWeight());
        product.setDimensions(productDetails.getDimensions());
        product.setLengthCm(productDetails.getLengthCm());
        product.setWidthCm(productDetails.getWidthCm());
        product.setHeightCm(productDetails.getHeightCm());
        product.setShippingRate(productDetails.getShippingRate());
        product.setVatRate(productDetails.getVatRate());
        product.setSctRate(productDetails.getSctRate());
        product.setActive(productDetails.isActive());
    }

    private void updateProductStatus(Long id, boolean isActive) {
        Product product = getProductByIdOrThrow(id);
        product.setActive(isActive);
        productRepository.save(product);
    }

    private Brand findBrandIfPresent(Long brandId) {
        if (brandId != null) {
            return brandRepository.findById(brandId)
                    .orElseThrow(() -> new WarehouseManagementException(ErrorCode.BRAND_NOT_FOUND, "ID: " + brandId));
        }
        return null;
    }

    private Color findColorIfPresent(Long colorId) {
        if (colorId != null) {
            return colorRepository.findById(colorId)
                    .orElseThrow(() -> new WarehouseManagementException(ErrorCode.COLOR_NOT_FOUND, "ID: " + colorId));
        }
        return null;
    }

    public int bulkAdjustPrices(BulkPriceUpdateRequest request) {
        validateBulkRequest(request);
        List<Product> targets = productRepository.findByOptionalFilters(
                request.getCategoryId(), request.getBrandId(), request.getColorId(), request.isOnlyActive()
        );
        if (targets.isEmpty()) {
            return 0;
        }
        boolean isIncrease = "INCREASE".equalsIgnoreCase(request.getDirection());
        boolean isPercentage = "PERCENTAGE".equalsIgnoreCase(request.getMode());
        BigDecimal value = request.getValue();

        for (Product p : targets) {
            BigDecimal current = p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO;
            BigDecimal updated;
            if (isPercentage) {
                BigDecimal factor = value.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
                BigDecimal multiplier = isIncrease ? BigDecimal.ONE.add(factor) : BigDecimal.ONE.subtract(factor);
                updated = current.multiply(multiplier);
            } else {
                BigDecimal delta = value;
                updated = isIncrease ? current.add(delta) : current.subtract(delta);
            }
            if (updated.compareTo(BigDecimal.ZERO) < 0) {
                updated = BigDecimal.ZERO;
            }
            p.setPrice(updated.setScale(2, RoundingMode.HALF_UP));
        }
        productRepository.saveAll(targets);
        return targets.size();
    }

    private void validateBulkRequest(BulkPriceUpdateRequest request) {
        if (request.getMode() == null || request.getValue() == null || request.getDirection() == null) {
            throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING, "Mode, value and direction are required");
        }
        if (!"PERCENTAGE".equalsIgnoreCase(request.getMode()) && !"AMOUNT".equalsIgnoreCase(request.getMode())) {
            throw new WarehouseManagementException(ErrorCode.INVALID_VALUE, "Invalid mode");
        }
        if (!"INCREASE".equalsIgnoreCase(request.getDirection()) && !"DECREASE".equalsIgnoreCase(request.getDirection())) {
            throw new WarehouseManagementException(ErrorCode.INVALID_VALUE, "Invalid direction");
        }
        if (request.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new WarehouseManagementException(ErrorCode.VALUE_MUST_BE_POSITIVE, "Value must be positive");
        }
        if ("PERCENTAGE".equalsIgnoreCase(request.getMode()) && request.getValue().compareTo(BigDecimal.valueOf(1000)) > 0) {
            throw new WarehouseManagementException(ErrorCode.INVALID_VALUE, "Percentage is unrealistically high");
        }
    }
}
