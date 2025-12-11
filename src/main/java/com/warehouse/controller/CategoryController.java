package com.warehouse.controller;

import com.warehouse.entity.Category;
import com.warehouse.service.CategoryService;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.dto.CategoryDto;
import com.warehouse.service.AdminSecurityService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import com.warehouse.dto.PagedResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryRepository categoryRepository;
    private final AdminSecurityService adminSecurityService;

    @Autowired
    public CategoryController(CategoryService categoryService, CategoryRepository categoryRepository, AdminSecurityService adminSecurityService) {
        this.categoryService = categoryService;
        this.categoryRepository = categoryRepository;
        this.adminSecurityService = adminSecurityService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<CategoryDto>> getAllCategories() {
        List<Category> categories = categoryService.getAllCategories();
        return ResponseEntity.ok(categories.stream().map(this::toDtoShallow).toList());
    }

    @GetMapping("/with-counts")
    public ResponseEntity<List<CategoryDto>> getCategoriesWithProductCounts() {
        var counts = categoryRepository.fetchCategoryProductCounts();
        var categories = categoryService.getAllCategories();
        var map = new java.util.HashMap<Long, Long>();
        counts.forEach(c -> map.put(c.getCategoryId(), c.getProductCount()));
        var result = new java.util.ArrayList<CategoryDto>();
        for (var c : categories) {
            var dto = new CategoryDto(
                c.getId(),
                c.getName(),
                c.getDescription(),
                c.isActive(),
                map.getOrDefault(c.getId(), 0L),
                c.getParent() != null ? c.getParent().getId() : null,
                c.getParent() != null ? c.getParent().getName() : null,
                new java.util.ArrayList<>(),
                new java.util.ArrayList<>(),
                c.getCreatedAt(),
                c.getUpdatedAt()
            );
            result.add(dto);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/hierarchical")
    public ResponseEntity<List<CategoryDto>> getHierarchicalCategories() {
        List<CategoryDto> hierarchicalCategories = categoryService.getHierarchicalCategories();
        return ResponseEntity.ok(hierarchicalCategories);
    }

    @GetMapping("/top-level")
    public ResponseEntity<?> getTopLevelCategories(
            @RequestParam(required = false, defaultValue = "name") String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDir) {
        
        // Fetch product counts
        var counts = categoryRepository.fetchCategoryProductCounts();
        var countMap = new java.util.HashMap<Long, Long>();
        counts.forEach(c -> countMap.put(c.getCategoryId(), c.getProductCount()));

        // Get sorted categories from service
        List<Category> sortedCategories = categoryService.getTopLevelCategoriesSorted(sortBy, sortDir);
        Map<Long, List<CategoryDto>> childrenMap = loadSubcategoriesForParents(sortedCategories, countMap);
        
        // Build DTOs
        var result = sortedCategories.stream()
            .map(cat -> buildCategoryDto(cat, countMap, childrenMap.getOrDefault(cat.getId(), List.of())))
            .toList();
        
        // Return with metadata for frontend compatibility
        PagedResponse<CategoryDto> response = new PagedResponse<>(
                result,
                0,
                result.size(),
                (long) result.size(),
                1,
                true,
                true
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{parentId}/subcategories")
    public ResponseEntity<List<CategoryDto>> getSubcategories(@PathVariable Long parentId) {
        var counts = categoryRepository.fetchCategoryProductCounts();
        var countMap = new java.util.HashMap<Long, Long>();
        counts.forEach(c -> countMap.put(c.getCategoryId(), c.getProductCount()));

        List<Category> subcategories = categoryService.getSubcategories(parentId);

        var result = subcategories.stream()
            .map(cat -> {
                Long productCount = countMap.getOrDefault(cat.getId(), 0L);

                var dto = new CategoryDto();
                dto.setId(cat.getId());
                dto.setName(cat.getName());
                dto.setDescription(cat.getDescription());
                dto.setActive(cat.isActive());
                dto.setProductCount(productCount);
                dto.setParentId(cat.getParent() != null ? cat.getParent().getId() : null);
                dto.setParentName(cat.getParent() != null ? cat.getParent().getName() : null);
                dto.setChildren(new java.util.ArrayList<>());
                dto.setCreatedAt(cat.getCreatedAt());
                dto.setUpdatedAt(cat.getUpdatedAt());
                return dto;
            })
            .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/active")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CategoryDto>> getAllActiveCategories() {
        List<Category> categories = categoryService.getAllActiveCategories();
        return ResponseEntity.ok(categories.stream().map(this::toDtoShallow).toList());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<CategoryDto> getCategoryById(@PathVariable Long id) {
        return categoryService.getCategoryById(id)
                .map(category -> ResponseEntity.ok(toDtoShallow(category)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/with-products")
    @Transactional(readOnly = true)
    public ResponseEntity<CategoryDto> getCategoryByIdWithProducts(@PathVariable Long id) {
        return categoryService.getCategoryByIdWithProducts(id)
                .map(category -> {
                    var dto = toDtoShallow(category);
                    dto.setProductCount((long) (category.getProducts() != null ? category.getProducts().size() : 0));
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Category> createCategory(@Valid @RequestBody Category category) {
        // Ensure this is always a main category (no parent)
        category.setParent(null);
        Category createdCategory = categoryService.createCategory(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCategory);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<Category>> createSubcategories(@RequestParam Long parentId, @Valid @RequestBody List<Category> subcategories) {
        List<Category> createdSubcategories = categoryService.createSubcategories(parentId, subcategories);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSubcategories);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Category> updateCategory(@PathVariable Long id, @Valid @RequestBody Category category) {
        Category updatedCategory = categoryService.updateCategory(id, category);
        return ResponseEntity.ok(updatedCategory);
    }

    @PutMapping("/{id}/parent")
    public ResponseEntity<Category> updateCategoryParent(@PathVariable Long id, @RequestParam(required = false) Long parentId) {
        Category updatedCategory = categoryService.updateCategoryParent(id, parentId);
        return ResponseEntity.ok(updatedCategory);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id,
                                               @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    private CategoryDto toDtoShallow(Category c) {
        var dto = new CategoryDto();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setDescription(c.getDescription());
        dto.setActive(c.isActive());
        dto.setParentId(c.getParent() != null ? c.getParent().getId() : null);
        dto.setParentName(c.getParent() != null ? c.getParent().getName() : null);
        dto.setChildren(new java.util.ArrayList<>());
        dto.setSubcategories(new java.util.ArrayList<>());
        dto.setProductCount(0L);
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());
        return dto;
    }

    private Map<Long, List<CategoryDto>> loadSubcategoriesForParents(List<Category> parents, Map<Long, Long> countMap) {
        Map<Long, List<CategoryDto>> childrenMap = new HashMap<>();
        if (parents == null || parents.isEmpty()) {
            return childrenMap;
        }
        List<Long> parentIds = parents.stream().map(Category::getId).toList();
        List<Category> subcategories = categoryRepository.findByParentIdIn(parentIds);
        for (Category sub : subcategories) {
            if (sub.getParent() == null) {
                continue;
            }
            Long parentId = sub.getParent().getId();
            childrenMap.computeIfAbsent(parentId, id -> new ArrayList<>())
                    .add(buildCategoryDto(sub, countMap, List.of()));
        }
        return childrenMap;
    }

    private CategoryDto buildCategoryDto(Category category, Map<Long, Long> countMap, List<CategoryDto> children) {
        var dto = new CategoryDto();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDescription(category.getDescription());
        dto.setActive(category.isActive());
        dto.setProductCount(countMap.getOrDefault(category.getId(), 0L));
        dto.setParentId(category.getParent() != null ? category.getParent().getId() : null);
        dto.setParentName(category.getParent() != null ? category.getParent().getName() : null);
        List<CategoryDto> childList = children != null ? children : new ArrayList<>();
        dto.setChildren(childList);
        dto.setSubcategories(childList);
        dto.setCreatedAt(category.getCreatedAt());
        dto.setUpdatedAt(category.getUpdatedAt());
        return dto;
    }
}
