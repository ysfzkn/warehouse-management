package com.warehouse.service;

import com.warehouse.dto.CategoryDto;
import com.warehouse.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing categories.
 */
public interface CategoryService {

    List<Category> getAllCategories();

    Page<Category> getAllCategories(Pageable pageable);

    List<Category> getAllActiveCategories();

    Page<Category> getAllActiveCategories(Pageable pageable);

    Page<Category> getTopLevelCategories(Pageable pageable);

    Optional<Category> getCategoryById(Long id);

    Category getCategoryByIdOrThrow(Long id);

    Optional<Category> getCategoryByIdWithProducts(Long id);

    Optional<Category> getCategoryByName(String name);

    Category createCategory(Category category);

    Category updateCategory(Long id, Category categoryDetails);

    void deleteCategory(Long id);

    boolean existsByName(String name);

    List<Category> createSubcategories(Long parentId, List<Category> subcategories);

    Category updateCategoryParent(Long id, Long parentId);

    List<Category> getTopLevelCategories();

    List<Category> getSubcategories(Long parentId);

    List<CategoryDto> getHierarchicalCategories();
}
