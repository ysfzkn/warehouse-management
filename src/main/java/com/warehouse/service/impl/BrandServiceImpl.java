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
        Brand saved = brandRepository.save(brand);
        logger.info("Brand created successfully with id: {}", saved.getId());
        return saved;
    }

    @Override
    public Brand updateBrand(Long id, Brand details) {
        logger.info("Updating brand with id: {}", id);
        Brand brand = getBrandByIdOrThrow(id);
        validateNameUniquenessOnUpdate(brand, details);
        brand.setName(details.getName());
        brand.setDescription(details.getDescription());
        brand.setActive(details.isActive());
        Brand saved = brandRepository.save(brand);
        logger.info("Brand updated successfully with id: {}", saved.getId());
        return saved;
    }

    @Override
    public void deleteBrand(Long id) {
        logger.info("Deleting brand with id: {}", id);
        Brand brand = getBrandByIdOrThrow(id);
        EntityValidator.validateEntityHasNoRelations(
            brand.getProducts() != null && !brand.getProducts().isEmpty(),
            EntityNames.BRAND, EntityNames.RELATION_PRODUCTS
        );
        brandRepository.delete(brand);
        logger.info("Brand deleted successfully with id: {}", id);
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
}

