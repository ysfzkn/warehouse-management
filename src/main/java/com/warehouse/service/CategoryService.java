package com.warehouse.service;

import com.warehouse.dto.CategoryDto;
import com.warehouse.entity.Category;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.util.EntityValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
        EntityValidator.validateEntityHasNoRelations(
            !category.getChildren().isEmpty(), "Category", "subcategories"
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

    public List<Category> createSubcategories(Long parentId, List<Category> subcategories) {
        Category parent = getCategoryByIdOrThrow(parentId);

        List<Category> createdSubcategories = new ArrayList<>();
        for (Category subcategory : subcategories) {
            subcategory.setParent(parent);
            Category created = createCategory(subcategory);
            createdSubcategories.add(created);
        }

        return createdSubcategories;
    }

    public Category updateCategoryParent(Long id, Long parentId) {
        Category category = getCategoryByIdOrThrow(id);

        // Prevent circular references
        if (parentId != null) {
            if (id.equals(parentId)) {
                throw new WarehouseManagementException(ErrorCode.CATEGORY_INVALID_PARENT, "Category cannot be its own parent");
            }
            Category parent = getCategoryByIdOrThrow(parentId);
            // Check if the parent is a descendant of this category
            if (isDescendant(parent, category)) {
                throw new WarehouseManagementException(ErrorCode.CATEGORY_INVALID_PARENT, "Cannot set a descendant as parent");
            }
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        return categoryRepository.save(category);
    }

    private boolean isDescendant(Category potentialDescendant, Category ancestor) {
        Category current = potentialDescendant;
        while (current != null) {
            if (current.getId().equals(ancestor.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<Category> getTopLevelCategories() {
        return categoryRepository.findTopLevelCategories();
    }

    @Transactional(readOnly = true)
    public List<Category> getSubcategories(Long parentId) {
        Category parent = getCategoryByIdOrThrow(parentId);
        return new ArrayList<>(parent.getChildren());
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getHierarchicalCategories() {
        List<Category> allCategories = categoryRepository.findAllWithChildren();
        return buildHierarchy(allCategories);
    }

    private List<CategoryDto> buildHierarchy(List<Category> categories) {
        List<CategoryDto> rootCategories = new ArrayList<>();
        java.util.Map<Long, CategoryDto> categoryMap = new java.util.HashMap<>();

        // Create DTOs for all categories
        for (Category category : categories) {
            CategoryDto dto = new CategoryDto(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.isActive(),
                0L, // productCount will be set later if needed
                category.getParent() != null ? category.getParent().getId() : null,
                category.getParent() != null ? category.getParent().getName() : null,
                new ArrayList<>(),
                category.getCreatedAt(),
                category.getUpdatedAt()
            );
            categoryMap.put(category.getId(), dto);
        }

        // Build hierarchy
        for (Category category : categories) {
            CategoryDto dto = categoryMap.get(category.getId());
            if (category.getParent() == null) {
                rootCategories.add(dto);
            } else {
                CategoryDto parentDto = categoryMap.get(category.getParent().getId());
                if (parentDto != null) {
                    parentDto.getChildren().add(dto);
                }
            }
        }

        return rootCategories;
    }
}
