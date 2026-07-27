package com.warehouse.service.impl;

import com.warehouse.entity.Brand;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.BrandRepository;
import com.warehouse.service.BrandService;
import com.warehouse.util.EntityValidator;
import com.warehouse.constants.BusinessMessages;
import com.warehouse.constants.EntityNames;
import com.warehouse.util.NameUniquenessValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of BrandService for managing brands.
 */
@Service
@Transactional
public class BrandServiceImpl implements BrandService {

    private static final Logger logger = LoggerFactory.getLogger(BrandServiceImpl.class);

    private final BrandRepository brandRepository;

    public BrandServiceImpl(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Brand> getAllBrands() {
        logger.debug("Fetching all brands");
        return brandRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Brand> getAllActiveBrands() {
        logger.debug("Fetching all active brands");
        return brandRepository.findAllActive();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Brand> searchActiveBrands(String name) {
        logger.debug("Searching active brands by name: {}", name);
        return brandRepository.searchActiveByName(name);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Brand> getBrandById(Long id) {
        logger.debug("Fetching brand by id: {}", id);
        return brandRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Brand getBrandByIdOrThrow(Long id) {
        logger.debug("Fetching brand by id or throw: {}", id);
        return brandRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Brand not found with id: {}", id);
                    return new WarehouseManagementException(ErrorCode.BRAND_NOT_FOUND, BusinessMessages.ID_PREFIX + id);
                });
    }

    @Override
    public Brand createBrand(Brand brand) {
        logger.info("Creating new brand: {}", brand.getName());
        validateNameUniqueness(brand.getName());
        if (brand.getSlug() == null || brand.getSlug().isBlank()) {
            brand.setSlug(generateUniqueSlug(brand.getName(), null));
        }
        Brand saved = brandRepository.save(brand);
        logger.info("Brand created successfully with id: {}", saved.getId());
        return saved;
    }

    @Override
    public Brand updateBrand(Long id, Brand details) {
        logger.info("Updating brand with id: {}", id);
        Brand brand = getBrandByIdOrThrow(id);
        validateNameUniquenessOnUpdate(brand, details);
        boolean nameChanged = !brand.getName().equals(details.getName());
        brand.setName(details.getName());
        brand.setDescription(details.getDescription());
        brand.setActive(details.isActive());
        if (nameChanged || brand.getSlug() == null || brand.getSlug().isBlank()) {
            brand.setSlug(generateUniqueSlug(details.getName(), brand.getSlug()));
        }
        Brand saved = brandRepository.save(brand);
        logger.info("Brand updated successfully with id: {}", saved.getId());
        return saved;
    }

    @Override
    public void deleteBrand(Long id) {
        logger.info("Deleting brand with id: {}", id);
        Brand brand = getBrandByIdOrThrow(id);
        if (brand.getProducts() != null && !brand.getProducts().isEmpty()) {
            int total = brand.getProducts().size();
            var productNames = brand.getProducts().stream()
                    .map(p -> {
                        String name = p.getName() != null ? p.getName() : "İsimsiz ürün";
                        String sku = p.getSku() != null ? p.getSku() : "-";
                        return name + " (SKU: " + sku + ")";
                    })
                    .toList();
            logger.warn("Cannot delete brand with id={} because it is used by {} products: {}", id, total, productNames);
            throw new WarehouseManagementException(
                    ErrorCode.CANNOT_DELETE_WITH_PRODUCTS,
                    (Object) productNames.toArray(new String[0])
            );
        }
        brandRepository.delete(brand);
        logger.info("Brand deleted successfully with id: {}", id);
    }

    @Override
    public void deleteBrandsBulk(List<Long> ids) {
        logger.info("Bulk deleting brands with ids: {}", ids);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            deleteBrand(id);
        }
        logger.info("Bulk brand delete completed for {} items", ids.size());
    }

    private void validateNameUniqueness(String name) {
        NameUniquenessValidator.validateNameUniqueness(
            name,
            brandRepository::existsByName,
            ErrorCode.BRAND_NAME_ALREADY_EXISTS,
            "Brand"
        );
    }

    private void validateNameUniquenessOnUpdate(Brand brand, Brand details) {
        NameUniquenessValidator.validateNameUniquenessOnUpdate(
            brand.getName(),
            details.getName(),
            brandRepository::existsByName,
            ErrorCode.BRAND_NAME_ALREADY_EXISTS,
            "Brand"
        );
    }

    /**
     * Generates a URL-safe slug from the brand name, ensuring uniqueness against
     * the {@code idx_brands_slug} unique index. {@code currentSlug} is the brand's
     * existing slug on update (excluded from the collision check) or {@code null}
     * on create.
     */
    private String generateUniqueSlug(String name, String currentSlug) {
        String base = generateSlug(name);
        String candidate = base;
        int suffix = 2;
        while (!candidate.equals(currentSlug) && brandRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String generateSlug(String name) {
        if (name == null) return "brand-" + System.currentTimeMillis();
        String slug = name.toLowerCase()
            .replace("ı", "i").replace("ğ", "g").replace("ü", "u")
            .replace("ş", "s").replace("ö", "o").replace("ç", "c")
            .replace("İ", "i").replace("Ğ", "g").replace("Ü", "u")
            .replace("Ş", "s").replace("Ö", "o").replace("Ç", "c")
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        return slug.isBlank() ? "brand-" + System.currentTimeMillis() : slug;
    }
}

