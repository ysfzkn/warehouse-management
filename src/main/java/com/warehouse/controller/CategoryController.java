package com.warehouse.controller;

import com.warehouse.entity.Category;
import com.warehouse.service.CategoryService;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.dto.CategoryDto;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryRepository categoryRepository;

    @Autowired
    public CategoryController(CategoryService categoryService, CategoryRepository categoryRepository) {
        this.categoryService = categoryService;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories() {
        List<Category> categories = categoryService.getAllCategories();
        return ResponseEntity.ok(categories);
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
    public ResponseEntity<List<CategoryDto>> getTopLevelCategories() {
        var counts = categoryRepository.fetchCategoryProductCounts();
        var countMap = new java.util.HashMap<Long, Long>();
        counts.forEach(c -> countMap.put(c.getCategoryId(), c.getProductCount()));

        List<Category> topLevelCategories = categoryService.getTopLevelCategories();

        var result = topLevelCategories.stream()
            .map(cat -> {
                Long productCount = countMap.getOrDefault(cat.getId(), 0L);

                var dto = new CategoryDto();
                dto.setId(cat.getId());
                dto.setName(cat.getName());
                dto.setDescription(cat.getDescription());
                dto.setActive(cat.isActive());
                dto.setProductCount(productCount);
                dto.setParentId(null);
                dto.setParentName(null);
                dto.setChildren(new java.util.ArrayList<>());
                dto.setCreatedAt(cat.getCreatedAt());
                dto.setUpdatedAt(cat.getUpdatedAt());
                return dto;
            })
            .toList();

        return ResponseEntity.ok(result);
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
    public ResponseEntity<List<Category>> getAllActiveCategories() {
        List<Category> categories = categoryService.getAllActiveCategories();
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Category> getCategoryById(@PathVariable Long id) {
        return categoryService.getCategoryById(id)
                .map(category -> ResponseEntity.ok(category))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/with-products")
    public ResponseEntity<Category> getCategoryByIdWithProducts(@PathVariable Long id) {
        return categoryService.getCategoryByIdWithProducts(id)
                .map(category -> ResponseEntity.ok(category))
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
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
