package com.warehouse.service;

import com.warehouse.entity.Category;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.util.EntityValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Category> getAllActiveCategories() {
        return categoryRepository.findAllActive();
    }

    @Transactional(readOnly = true)
    public Optional<Category> getCategoryById(Long id) {
        return categoryRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Category getCategoryByIdOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.CATEGORY_NOT_FOUND, "ID: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<Category> getCategoryByIdWithProducts(Long id) {
        return categoryRepository.findByIdWithProducts(id);
    }

    @Transactional(readOnly = true)
    public Optional<Category> getCategoryByName(String name) {
        return categoryRepository.findByName(name);
    }

    public Category createCategory(Category category) {
        checkNameDuplication(category.getName());
        return categoryRepository.save(category);
    }

    public Category updateCategory(Long id, Category categoryDetails) {
        Category category = getCategoryByIdOrThrow(id);
        checkNameDuplicationOnUpdate(category, categoryDetails);
        category.setName(categoryDetails.getName());
        category.setDescription(categoryDetails.getDescription());
        return categoryRepository.save(category);
    }

    public void deleteCategory(Long id) {
        Category category = getCategoryByIdOrThrow(id);
        EntityValidator.validateEntityHasNoRelations(
            !category.getProducts().isEmpty(), "Category", "products"
        );
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return categoryRepository.existsByName(name);
    }

    private void checkNameDuplication(String name) {
        if (categoryRepository.existsByName(name)) {
            throw new WarehouseManagementException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS, "Name: " + name);
        }
    }

    private void checkNameDuplicationOnUpdate(Category category, Category categoryDetails) {
        if (!category.getName().equals(categoryDetails.getName()) &&
            categoryRepository.existsByName(categoryDetails.getName())) {
            throw new WarehouseManagementException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS, "Name: " + categoryDetails.getName());
        }
    }
}
