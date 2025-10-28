package com.warehouse.service;

import com.warehouse.entity.Brand;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.BrandRepository;
import com.warehouse.util.EntityValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class BrandService {

    private final BrandRepository brandRepository;

    public BrandService(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    @Transactional(readOnly = true)
    public List<Brand> getAllBrands() {
        return brandRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Brand> getAllActiveBrands() {
        return brandRepository.findAllActive();
    }

    @Transactional(readOnly = true)
    public List<Brand> searchActiveBrands(String name) {
        return brandRepository.searchActiveByName(name);
    }

    @Transactional(readOnly = true)
    public Optional<Brand> getBrandById(Long id) {
        return brandRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Brand getBrandByIdOrThrow(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.BRAND_NOT_FOUND, "ID: " + id));
    }

    public Brand createBrand(Brand brand) {
        checkNameDuplication(brand.getName());
        return brandRepository.save(brand);
    }

    public Brand updateBrand(Long id, Brand details) {
        Brand brand = getBrandByIdOrThrow(id);
        checkNameDuplicationOnUpdate(brand, details);
        brand.setName(details.getName());
        brand.setDescription(details.getDescription());
        brand.setActive(details.isActive());
        return brandRepository.save(brand);
    }

    public void deleteBrand(Long id) {
        Brand brand = getBrandByIdOrThrow(id);
        EntityValidator.validateEntityHasNoRelations(
            brand.getProducts() != null && !brand.getProducts().isEmpty(), 
            "Brand", "products"
        );
        brandRepository.delete(brand);
    }

    private void checkNameDuplication(String name) {
        if (brandRepository.existsByName(name)) {
            throw new WarehouseManagementException(ErrorCode.BRAND_NAME_ALREADY_EXISTS, "Name: " + name);
        }
    }

    private void checkNameDuplicationOnUpdate(Brand brand, Brand details) {
        if (!brand.getName().equals(details.getName()) && 
            brandRepository.existsByName(details.getName())) {
            throw new WarehouseManagementException(ErrorCode.BRAND_NAME_ALREADY_EXISTS, "Name: " + details.getName());
        }
    }
}


