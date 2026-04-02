package com.warehouse.controller.store;

import com.warehouse.dto.store.StoreCategoryDto;
import com.warehouse.entity.Category;
import com.warehouse.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/store/categories")
public class StoreCategoryController {

    private final CategoryService categoryService;

    public StoreCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/tree")
    public ResponseEntity<List<StoreCategoryDto>> getCategoryTree() {
        List<Category> rootCategories = categoryService.getAllRootCategories();
        List<StoreCategoryDto> tree = rootCategories.stream()
            .filter(c -> c.isActive() && c.isShowInMenu())
            .map(this::toCategoryTreeDto)
            .collect(Collectors.toList());
        return ResponseEntity.ok(tree);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<StoreCategoryDto> getCategoryBySlug(@PathVariable String slug) {
        Category category = categoryService.getCategoryBySlug(slug);
        return ResponseEntity.ok(toCategoryTreeDto(category));
    }

    private StoreCategoryDto toCategoryTreeDto(Category category) {
        List<StoreCategoryDto> children = List.of();
        if (category.getChildren() != null) {
            try {
                children = category.getChildren().stream()
                    .filter(c -> c.isActive() && c.isShowInMenu())
                    .map(this::toCategoryTreeDto)
                    .collect(Collectors.toList());
            } catch (Exception ignored) {}
        }

        return StoreCategoryDto.builder()
            .id(category.getId())
            .slug(category.getSlug())
            .name(category.getName())
            .description(category.getDescription())
            .imageUrl(category.getImageUrl())
            .metaTitle(category.getMetaTitle())
            .metaDescription(category.getMetaDescription())
            .sortOrder(category.getSortOrder())
            .parentId(category.getParent() != null ? category.getParent().getId() : null)
            .parentSlug(category.getParent() != null ? category.getParent().getSlug() : null)
            .children(children)
            .build();
    }
}
