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

    List<Brand> searchActiveBrands(String name);

    Optional<Brand> getBrandById(Long id);

    Brand getBrandByIdOrThrow(Long id);

    Brand createBrand(Brand brand);

    Brand updateBrand(Long id, Brand details);

    void deleteBrand(Long id);
}
