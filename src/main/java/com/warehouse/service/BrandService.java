package com.warehouse.service;

import com.warehouse.entity.Brand;
import com.warehouse.repository.BrandRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class BrandService {

    private final BrandRepository brandRepository;

    @Autowired
    public BrandService(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    public List<Brand> getAllBrands() {
        return brandRepository.findAll();
    }

    public List<Brand> getAllActiveBrands() {
        return brandRepository.findAllActive();
    }

    public List<Brand> searchActiveBrands(String name) {
        return brandRepository.searchActiveByName(name);
    }

    public Optional<Brand> getBrandById(Long id) {
        return brandRepository.findById(id);
    }

    public Brand createBrand(Brand brand) {
        if (brandRepository.existsByName(brand.getName())) {
            throw new RuntimeException("Brand with name '" + brand.getName() + "' already exists");
        }
        return brandRepository.save(brand);
    }

    public Brand updateBrand(Long id, Brand details) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Brand not found with id: " + id));

        if (!brand.getName().equals(details.getName()) && brandRepository.existsByName(details.getName())) {
            throw new RuntimeException("Brand with name '" + details.getName() + "' already exists");
        }

        brand.setName(details.getName());
        brand.setDescription(details.getDescription());
        brand.setActive(details.isActive());
        return brandRepository.save(brand);
    }

    public void deleteBrand(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Brand not found with id: " + id));

        if (brand.getProducts() != null && !brand.getProducts().isEmpty()) {
            throw new RuntimeException("Cannot delete brand with existing products");
        }
        brandRepository.delete(brand);
    }
}


