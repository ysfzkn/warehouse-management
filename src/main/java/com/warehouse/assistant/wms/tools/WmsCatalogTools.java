package com.warehouse.assistant.wms.tools;

import com.warehouse.dto.CategoryDto;
import com.warehouse.dto.PagedResponse;
import com.warehouse.dto.ProductDto;
import com.warehouse.dto.WarehouseResponse;
import com.warehouse.entity.Brand;
import com.warehouse.entity.Color;
import com.warehouse.enums.WarehouseType;
import com.warehouse.service.BrandService;
import com.warehouse.service.CategoryService;
import com.warehouse.service.ColorService;
import com.warehouse.service.ProductService;
import com.warehouse.service.StockService;
import com.warehouse.service.WarehouseService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Read-only catalog tools for the WMS assistant (safe by default).
 */
@Component
public class WmsCatalogTools {

    private final WarehouseService warehouseService;
    private final ProductService productService;
    private final StockService stockService;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final ColorService colorService;

    public WmsCatalogTools(WarehouseService warehouseService,
                           ProductService productService,
                           StockService stockService,
                           CategoryService categoryService,
                           BrandService brandService,
                           ColorService colorService) {
        this.warehouseService = warehouseService;
        this.productService = productService;
        this.stockService = stockService;
        this.categoryService = categoryService;
        this.brandService = brandService;
        this.colorService = colorService;
    }

    @Tool(description = "List all warehouses (id, name, location, type) for filtering and navigation.")
    public List<WarehouseResponse> listWarehouses() {
        return warehouseService.getAllWarehouses();
    }

    @Tool(description = "List Emanet Depo warehouses (id, name, location, type). Use this before customer-based emanet stock queries; if there are multiple emanet depolar, you should ask the user which one to use.")
    public List<WarehouseResponse> listEmanetWarehouses() {
        return warehouseService.getAllWarehouses().stream()
                .filter(w -> {
                    if (w == null) return false;
                    if (w.getWarehouseType() == WarehouseType.EMANET_DEPO) return true;
                    String name = w.getName() != null ? w.getName().toLowerCase() : "";
                    return name.contains("emanet");
                })
                .toList();
    }

    @Tool(description = "List all categories (id, name, parent) for filtering and navigation.")
    public List<CategoryDto> listCategories() {
        return categoryService.getAllCategories()
                .stream()
                .map(c -> {
                    CategoryDto dto = new CategoryDto();
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
                })
                .toList();
    }

    @Tool(description = "List all brands for product filtering.")
    public List<Brand> listBrands() {
        return brandService.getAllBrands();
    }

    @Tool(description = "Search brands by name. Use this to resolve the correct brand id before filtering by brand.")
    public List<Brand> searchBrands(String name) {
        if (name == null || name.isBlank()) return List.of();
        return brandService.searchActiveBrands(name.trim());
    }

    @Tool(description = "List all colors for product filtering.")
    public List<Color> listColors() {
        return colorService.getAllColors();
    }

    @Tool(description = "Search products by query text. The query matches against product NAME, SKU, "
            + "CATEGORY name, and BRAND name — so 'televizyon' finds products in the Televizyon category, "
            + "'Samsung' finds all Samsung products, and exact SKU matches also work. Returns up to 50 "
            + "lightweight product records with total quantity across all warehouses.")
    public List<ProductDto> searchProducts(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        // Use the filter-aware query so name/SKU/category/brand are all searched.
        Pageable pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<com.warehouse.entity.Product> page =
                productService.getAllProducts(pageable, query.trim(), null, null, null);
        List<com.warehouse.entity.Product> products = page.getContent();
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> ids = products.stream().map(com.warehouse.entity.Product::getId).toList();
        Map<Long, Long> totals = stockService.getTotalQuantitiesByProductIds(ids);
        return products.stream().map(p -> {
            ProductDto dto = new ProductDto();
            dto.id = p.getId();
            dto.name = p.getName();
            dto.sku = p.getSku();
            dto.description = p.getDescription();
            dto.price = p.getPrice();
            dto.active = p.isActive();
            dto.totalQuantity = totals.getOrDefault(p.getId(), 0L);
            return dto;
        }).toList();
    }

    @Tool(description = "Get a product by its SKU. Returns lightweight product info plus total quantity.")
    public ProductDto getProductBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            return null;
        }
        return productService.getProductBySku(sku.trim())
                .map(p -> {
                    ProductDto dto = new ProductDto();
                    dto.id = p.getId();
                    dto.name = p.getName();
                    dto.sku = p.getSku();
                    dto.description = p.getDescription();
                    dto.price = p.getPrice();
                    dto.active = p.isActive();
                    dto.totalQuantity = stockService.getTotalQuantityByProduct(p.getId());
                    return dto;
                })
                .orElse(null);
    }

    @Tool(description = "List products by brand id with pagination. Use this to confirm that products exist for a brand (independent from stock).")
    public PagedResponse<ProductDto> listProductsByBrand(Long brandId, Integer page, Integer size) {
        if (brandId == null) {
            return new PagedResponse<>(List.of(), 0, 0, 0L, 0, true, true);
        }
        int safePage = page != null && page >= 0 ? page : 0;
        int safeSize = size != null ? Math.min(Math.max(size, 1), 100) : 20;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<com.warehouse.entity.Product> p = productService.getAllProducts(pageable, null, null, brandId, null);
        List<ProductDto> content = p.getContent().stream().map(prod -> {
            ProductDto dto = new ProductDto();
            dto.id = prod.getId();
            dto.name = prod.getName();
            dto.sku = prod.getSku();
            dto.active = prod.isActive();
            return dto;
        }).toList();
        return new PagedResponse<>(
                content,
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages(),
                p.isFirst(),
                p.isLast()
        );
    }
}
