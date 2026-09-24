package com.warehouse.service;

import com.warehouse.entity.Brand;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing brands.
 */
public interface BrandService {

    List<Brand> getAllBrands();

    List<Brand> getAllActiveBrands();

    /** Active brands with at least one product the storefront shows. */
    List<Brand> getStorefrontBrands();

    List<Brand> searchActiveBrands(String name);

    Optional<Brand> getBrandById(Long id);

    Brand getBrandByIdOrThrow(Long id);

    Brand createBrand(Brand brand);

    Brand updateBrand(Long id, Brand details);

    void deleteBrand(Long id);

    /**
     * Deletes multiple brands by their IDs.
     *
     * @param ids list of brand IDs to delete
     */
    void deleteBrandsBulk(List<Long> ids);
}
