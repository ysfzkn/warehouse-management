package com.warehouse.controller.store;

import com.warehouse.dto.store.StoreCategoryDto;
import com.warehouse.entity.Category;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/store/categories")
@Transactional(readOnly = true)
public class StoreCategoryController {

    private final CategoryService categoryService;
    private final CategoryRepository categoryRepository;

    public StoreCategoryController(CategoryService categoryService,
                                   CategoryRepository categoryRepository) {
        this.categoryService = categoryService;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/tree")
    public ResponseEntity<List<StoreCategoryDto>> getCategoryTree() {
        Map<Long, Long> counts = storefrontProductCounts();
        List<Category> rootCategories = categoryService.getAllRootCategories();
        List<StoreCategoryDto> tree = rootCategories.stream()
            .filter(c -> c.isActive() && c.isShowInMenu())
            .map(c -> toCategoryTreeDto(c, counts))
            .collect(Collectors.toList());
        return ResponseEntity.ok(tree);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<StoreCategoryDto> getCategoryBySlug(@PathVariable String slug) {
        Category category = categoryService.getCategoryBySlug(slug);
        return ResponseEntity.ok(toCategoryTreeDto(category, storefrontProductCounts()));
    }

    /**
     * Kategori başına vitrinde görünen ürün sayısı, tek sorguda.
     *
     * <p>Kategori başına ayrı ayrı sayılsaydı ana sayfa otuz küsur sorgu açardı;
     * hepsi bir kerede alınıp haritadan okunuyor.</p>
     */
    private Map<Long, Long> storefrontProductCounts() {
        return categoryRepository.fetchStorefrontCategoryProductCounts().stream()
            .collect(Collectors.toMap(
                CategoryRepository.CategoryProductCount::getCategoryId,
                CategoryRepository.CategoryProductCount::getProductCount,
                (a, b) -> a));
    }

    private StoreCategoryDto toCategoryTreeDto(Category category, Map<Long, Long> counts) {
        List<StoreCategoryDto> children = List.of();
        if (category.getChildren() != null) {
            try {
                children = category.getChildren().stream()
                    .filter(c -> c.isActive() && c.isShowInMenu())
                    .map(c -> toCategoryTreeDto(c, counts))
                    .collect(Collectors.toList());
            } catch (Exception ignored) {}
        }

        // Üst kategorinin sayısı çocuklarınınkini de kapsıyor: mağaza listesi üst
        // kategoriye tıklandığında alt kategori ürünlerini de gösteriyor
        // (findActiveByFilters: "c.id = :categoryId OR cp.id = :categoryId").
        long productCount = counts.getOrDefault(category.getId(), 0L)
            + children.stream().mapToLong(StoreCategoryDto::getProductCount).sum();

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
            .parentName(category.getParent() != null ? category.getParent().getName() : null)
            .children(children)
            .productCount(productCount)
            .build();
    }
}
