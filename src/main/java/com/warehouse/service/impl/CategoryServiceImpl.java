package com.warehouse.service.impl;

import com.warehouse.dto.CategoryDto;
import com.warehouse.entity.Category;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.service.CategoryService;
import com.warehouse.util.EntityValidator;
import com.warehouse.constants.BusinessMessages;
import com.warehouse.constants.EntityNames;
import com.warehouse.util.NameUniquenessValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of CategoryService for managing categories.
 */
@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private static final Logger logger = LoggerFactory.getLogger(CategoryServiceImpl.class);

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        logger.debug("Fetching all categories");
        return categoryRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Category> getAllCategories(Pageable pageable) {
        logger.debug("Fetching paged categories - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return categoryRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> getAllActiveCategories() {
        logger.debug("Fetching all active categories");
        return categoryRepository.findAllActive();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Category> getAllActiveCategories(Pageable pageable) {
        logger.debug("Fetching paged active categories - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return categoryRepository.findAllActive(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> getCategoryById(Long id) {
        logger.debug("Fetching category by id: {}", id);
        return categoryRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Category getCategoryByIdOrThrow(Long id) {
        logger.debug("Fetching category by id or throw: {}", id);
        return categoryRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Category not found with id: {}", id);
                    return new WarehouseManagementException(ErrorCode.CATEGORY_NOT_FOUND, BusinessMessages.ID_PREFIX + id);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> getCategoryByIdWithProducts(Long id) {
        logger.debug("Fetching category with products by id: {}", id);
        return categoryRepository.findByIdWithProducts(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> getCategoryByName(String name) {
        logger.debug("Fetching category by name: {}", name);
        return categoryRepository.findByName(name);
    }

    @Override
    public Category createCategory(Category category) {
        logger.info("Creating new category: {}", category.getName());
        validateNameUniqueness(category.getName());
        Category saved = categoryRepository.save(category);
        logger.info("Category created successfully with id: {}", saved.getId());
        return saved;
    }

    @Override
    public Category updateCategory(Long id, Category categoryDetails) {
        logger.info("Updating category with id: {}", id);
        Category category = getCategoryByIdOrThrow(id);
        validateNameUniquenessOnUpdate(category, categoryDetails);
        category.setName(categoryDetails.getName());
        category.setDescription(categoryDetails.getDescription());
        Category saved = categoryRepository.save(category);
        logger.info("Category updated successfully with id: {}", saved.getId());
        return saved;
    }

    @Override
    public void deleteCategory(Long id) {
        logger.info("Deleting category with id: {}", id);
        Category category = getCategoryByIdOrThrow(id);
        EntityValidator.validateEntityHasNoRelations(
            !category.getProducts().isEmpty(), EntityNames.CATEGORY, EntityNames.RELATION_PRODUCTS
        );
        EntityValidator.validateEntityHasNoRelations(
            !category.getChildren().isEmpty(), EntityNames.CATEGORY, EntityNames.RELATION_SUBCATEGORIES
        );
        categoryRepository.delete(category);
        logger.info("Category deleted successfully with id: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return categoryRepository.existsByName(name);
    }

    @Override
    public List<Category> createSubcategories(Long parentId, List<Category> subcategories) {
        logger.info("Creating {} subcategories for parent category id: {}", subcategories.size(), parentId);
        Category parent = getCategoryByIdOrThrow(parentId);

        List<Category> createdSubcategories = new ArrayList<>();
        for (Category subcategory : subcategories) {
            subcategory.setParent(parent);
            Category created = createCategory(subcategory);
            createdSubcategories.add(created);
        }

        logger.info("Created {} subcategories successfully", createdSubcategories.size());
        return createdSubcategories;
    }

    @Override
    public Category updateCategoryParent(Long id, Long parentId) {
        logger.info("Updating parent for category id: {} to parent id: {}", id, parentId);
        Category category = getCategoryByIdOrThrow(id);

        if (parentId != null) {
            if (id.equals(parentId)) {
                logger.warn("Attempted to set category as its own parent. Category id: {}", id);
                throw new WarehouseManagementException(ErrorCode.CATEGORY_INVALID_PARENT, BusinessMessages.CATEGORY_CANNOT_BE_ITS_OWN_PARENT);
            }
            Category parent = getCategoryByIdOrThrow(parentId);
            if (isDescendant(parent, category)) {
                logger.warn("Attempted to set descendant as parent. Category id: {}, Parent id: {}", id, parentId);
                throw new WarehouseManagementException(ErrorCode.CATEGORY_INVALID_PARENT, BusinessMessages.CATEGORY_DESCENDANT_CANNOT_BE_PARENT);
            }
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        Category saved = categoryRepository.save(category);
        logger.info("Category parent updated successfully");
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> getTopLevelCategories() {
        logger.debug("Fetching top-level categories");
        return categoryRepository.findTopLevelCategories();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Category> getTopLevelCategories(Pageable pageable) {
        logger.debug("Fetching paged top-level categories - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return categoryRepository.findTopLevelCategories(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> getSubcategories(Long parentId) {
        logger.debug("Fetching subcategories for parent id: {}", parentId);
        Category parent = getCategoryByIdOrThrow(parentId);
        return new ArrayList<>(parent.getChildren());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDto> getHierarchicalCategories() {
        logger.debug("Fetching hierarchical categories");
        List<Category> allCategories = categoryRepository.findAllWithChildren();
        return buildHierarchy(allCategories);
    }

    private void validateNameUniqueness(String name) {
        NameUniquenessValidator.validateNameUniqueness(
            name,
            categoryRepository::existsByName,
            ErrorCode.CATEGORY_NAME_ALREADY_EXISTS,
            "Category"
        );
    }

    private void validateNameUniquenessOnUpdate(Category category, Category categoryDetails) {
        NameUniquenessValidator.validateNameUniquenessOnUpdate(
            category.getName(),
            categoryDetails.getName(),
            categoryRepository::existsByName,
            ErrorCode.CATEGORY_NAME_ALREADY_EXISTS,
            "Category"
        );
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

    private List<CategoryDto> buildHierarchy(List<Category> categories) {
        List<CategoryDto> rootCategories = new ArrayList<>();
        Map<Long, CategoryDto> categoryMap = new HashMap<>();

        for (Category category : categories) {
            CategoryDto dto = new CategoryDto(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.isActive(),
                0L,
                category.getParent() != null ? category.getParent().getId() : null,
                category.getParent() != null ? category.getParent().getName() : null,
                new ArrayList<>(),
                category.getCreatedAt(),
                category.getUpdatedAt()
            );
            categoryMap.put(category.getId(), dto);
        }

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

