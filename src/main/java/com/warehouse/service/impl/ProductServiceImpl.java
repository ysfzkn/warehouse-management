package com.warehouse.service.impl;

import com.warehouse.dto.BulkDeleteResponse;
import com.warehouse.dto.BulkPriceUpdateRequest;
import com.warehouse.entity.Product;
import com.warehouse.entity.ProductType;
import com.warehouse.entity.BundleItem;
import com.warehouse.entity.Category;
import com.warehouse.entity.Brand;
import com.warehouse.entity.Color;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.entity.VariantGroup;
import com.warehouse.dto.ProductDto;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.BrandRepository;
import com.warehouse.repository.ColorRepository;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.repository.VariantGroupRepository;
import com.warehouse.service.ProductService;
import com.warehouse.util.EntityValidator;
import com.warehouse.constants.BusinessMessages;
import com.warehouse.constants.EntityNames;
import com.warehouse.assistant.core.rag.ProductIndexEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation of ProductService for managing products.
 */
@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ColorRepository colorRepository;
    private final StockTransferRepository stockTransferRepository;
    private final VariantGroupRepository variantGroupRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProductServiceImpl(ProductRepository productRepository,
                             CategoryRepository categoryRepository,
                             BrandRepository brandRepository,
                             ColorRepository colorRepository,
                             StockTransferRepository stockTransferRepository,
                             VariantGroupRepository variantGroupRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.colorRepository = colorRepository;
        this.stockTransferRepository = stockTransferRepository;
        this.variantGroupRepository = variantGroupRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        logger.debug("Fetching all products");
        return productRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> getAllProducts(Pageable pageable) {
        logger.debug("Fetching paged products - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return productRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> getAllProducts(Pageable pageable, String search, Long categoryId, Long brandId, Long colorId) {
        logger.debug("Fetching paged products with filters - page: {}, size: {}, search: {}, categoryId: {}, brandId: {}, colorId: {}",
                pageable.getPageNumber(), pageable.getPageSize(), search, categoryId, brandId, colorId);
        String normalizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        return productRepository.findByFilters(normalizedSearch, categoryId, brandId, colorId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> getAllProducts(Pageable pageable, String search, Long categoryId, Long brandId, Long colorId,
                                        ProductType productType) {
        String normalizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        return productRepository.findByFilters(normalizedSearch, categoryId, brandId, colorId, productType, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getAllActiveProducts() {
        logger.debug("Fetching all active products");
        return productRepository.findAllActive();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> getAllActiveProducts(Pageable pageable) {
        logger.debug("Fetching paged active products - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return productRepository.findAllActive(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id) {
        logger.debug("Fetching product by id: {}", id);
        return productRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Product getProductByIdOrThrow(Long id) {
        logger.debug("Fetching product by id or throw: {}", id);
        return productRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Product not found with id: {}", id);
                    return new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND, BusinessMessages.ID_PREFIX + id);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> getProductByIdWithStocks(Long id) {
        logger.debug("Fetching product with stocks by id: {}", id);
        return productRepository.findByIdWithStocks(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> getProductByIdWithRelations(Long id) {
        logger.debug("Fetching product with relations by id: {}", id);
        return productRepository.findByIdWithRelations(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> getProductBySku(String sku) {
        logger.debug("Fetching product by SKU: {}", sku);
        return productRepository.findBySku(sku);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(Long categoryId) {
        logger.debug("Fetching products by category id: {}", categoryId);
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> {
                    logger.warn("Category not found with id: {}", categoryId);
                    return new WarehouseManagementException(ErrorCode.CATEGORY_NOT_FOUND, BusinessMessages.ID_PREFIX + categoryId);
                });
        return productRepository.findByCategoryAndActive(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> searchProductsByName(String name) {
        logger.debug("Searching products by name: {}", name);
        return productRepository.findByNameContainingIgnoreCaseAndActive(name);
    }

    @Override
    public Product createProduct(Product product) {
        logger.info("Creating new product: {}", product.getName());
        EntityValidator.validateProductForCreation(product);

        Category category = categoryRepository.findById(product.getCategory().getId())
                .orElseThrow(() -> {
                    logger.warn("Category not found with id: {}", product.getCategory().getId());
                    return new WarehouseManagementException(ErrorCode.CATEGORY_NOT_FOUND, BusinessMessages.ID_PREFIX + product.getCategory().getId());
                });

        validateSkuUniqueness(product.getSku());

        product.setCategory(category);
        setBrandIfPresent(product);
        setColorIfPresent(product);
        // Always store a URL-safe slug (sanitize even an admin-supplied one) so links never break.
        String slugBase = (product.getSlug() != null && !product.getSlug().isBlank())
                ? product.getSlug()
                : (product.getName() != null ? product.getName() : product.getSku());
        product.setSlug(safeSlug(slugBase, product.getSku()));
        validateSlugUniqueness(product.getSlug(), null);
        applyBundleMembers(product, product.getProductType(), product.getBundleMemberRefs());
        applyVariantGroup(product, product.getVariantSiblingIds());

        Product saved = productRepository.save(product);
        logger.info("Product created successfully with id: {}", saved.getId());
        eventPublisher.publishEvent(ProductIndexEvent.upsert(saved.getId()));
        Product result = productRepository.findByIdWithRelations(saved.getId()).orElse(saved);
        touchBundleItems(result);
        return result;
    }

    @Override
    public Product updateProduct(Long id, Product productDetails) {
        logger.info("Updating product with id: {}", id);
        Product product = getProductByIdOrThrow(id);

        updateCategory(product, productDetails);
        updateBrand(product, productDetails);
        updateColor(product, productDetails);
        validateSkuUniquenessOnUpdate(product, productDetails);
        updateProductFields(product, productDetails);
        applyBundleMembers(product, productDetails.getProductType(), productDetails.getBundleMemberRefs());
        applyVariantGroup(product, productDetails.getVariantSiblingIds());

        Product saved = productRepository.save(product);
        logger.info("Product updated successfully with id: {}", saved.getId());
        eventPublisher.publishEvent(ProductIndexEvent.upsert(saved.getId()));
        Product result = productRepository.findByIdWithRelations(saved.getId()).orElse(saved);
        touchBundleItems(result);
        return result;
    }

    /**
     * Rebuilds a bundle's member lines from the admin editor input.
     * For SIMPLE products this clears any stray members. For BUNDLE products it
     * validates and replaces the {@code bundle_items} (orphanRemoval handles deletes).
     */
    private void applyBundleMembers(Product bundle, ProductType type, List<Map<String, Object>> refs) {
        bundle.setProductType(type != null ? type : ProductType.SIMPLE);

        if (bundle.getBundleItems() == null) {
            bundle.setBundleItems(new ArrayList<>());
        }
        List<BundleItem> current = bundle.getBundleItems();

        if (bundle.getProductType() != ProductType.BUNDLE) {
            current.clear(); // ensure a converted-back product keeps no members
            return;
        }

        if (refs == null || refs.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.INVALID_VALUE, "Bir set en az bir üye ürün içermelidir.");
        }

        // Reconcile the existing rows IN PLACE: update unchanged members, add only genuinely
        // new ones, delete removed ones. Re-creating every row (clear + addAll) would try to
        // INSERT a duplicate (bundle_id, product_id) before the old row is deleted → uq violation.
        Map<Long, BundleItem> existingByProduct = new HashMap<>();
        for (BundleItem bi : current) {
            if (bi.getProduct() != null) existingByProduct.put(bi.getProduct().getId(), bi);
        }

        Set<Long> desiredIds = new HashSet<>();
        int order = 0;
        for (Map<String, Object> ref : refs) {
            if (ref == null) continue;
            Long memberId = toLongOrNull(ref.get("productId"));
            if (memberId == null) continue;
            if (memberId.equals(bundle.getId())) {
                throw new WarehouseManagementException(ErrorCode.INVALID_VALUE, "Bir set kendisini üye olarak içeremez.");
            }
            if (!desiredIds.add(memberId)) continue; // de-duplicate
            int qty = Math.max(1, toIntOrDefault(ref.get("quantity"), 1));
            boolean gift = toBool(ref.get("isGift"));

            BundleItem existing = existingByProduct.get(memberId);
            if (existing != null) {
                existing.setQuantity(qty);
                existing.setSortOrder(order++);
                existing.setGift(gift);
            } else {
                Product member = productRepository.findById(memberId)
                        .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND, BusinessMessages.ID_PREFIX + memberId));
                if (member.getProductType() == ProductType.BUNDLE) {
                    throw new WarehouseManagementException(ErrorCode.INVALID_VALUE,
                            "İç içe set oluşturulamaz: \"" + member.getName() + "\" bir set ürünüdür.");
                }
                BundleItem created = new BundleItem();
                created.setBundle(bundle);
                created.setProduct(member);
                created.setQuantity(qty);
                created.setSortOrder(order++);
                created.setGift(gift);
                current.add(created);
            }
        }

        if (desiredIds.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.INVALID_VALUE, "Set için geçerli üye ürün bulunamadı.");
        }

        // Remove members no longer present (orphanRemoval deletes their rows).
        current.removeIf(bi -> bi.getProduct() == null || !desiredIds.contains(bi.getProduct().getId()));
    }

    /** Force-initialize a bundle's members + images before the tx closes (for the response DTO). */
    private void touchBundleItems(Product product) {
        if (product != null && product.getProductType() == ProductType.BUNDLE) {
            if (product.getBundleItems() != null) {
                for (BundleItem bi : product.getBundleItems()) {
                    if (bi.getProduct() != null) {
                        bi.getProduct().getName(); // trigger lazy load within the transaction
                    }
                }
            }
            if (product.getImages() != null) {
                product.getImages().size(); // initialize images so toDto can build primaryImageUrl
            }
        }
    }

    private static Long toLongOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int toIntOrDefault(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean toBool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(v.toString().trim()) || "1".equals(v.toString().trim());
    }

    /**
     * Reconciles this product's color-variant group from the admin form's sibling picker.
     * After the call, the product's variant group is exactly {@code {product} ∪ siblingIds}:
     * a shared group id is reused if one already exists among the members (else a new group is
     * minted), every member is pointed at it, and any product that used to be in the product's
     * previous group but is no longer selected leaves it. Groups left with fewer than two
     * members are emptied (a lone color is not a variant). Symmetric: editing either side of a
     * pair keeps both linked. The product itself is saved by the caller; siblings and dropped
     * members are saved here.
     */
    private void applyVariantGroup(Product product, List<Long> siblingIds) {
        Set<Long> requested = new java.util.LinkedHashSet<>();
        if (siblingIds != null) {
            for (Long sid : siblingIds) {
                if (sid == null) continue;
                if (product.getId() != null && sid.equals(product.getId())) continue; // can't be its own variant
                requested.add(sid);
            }
        }

        Long previousGroupId = product.getVariantGroupId();
        Set<Long> groupsToCollapse = new java.util.LinkedHashSet<>();
        if (previousGroupId != null) groupsToCollapse.add(previousGroupId);

        // No siblings selected → the product leaves any group it was in.
        if (requested.isEmpty()) {
            product.setVariantGroupId(null);
            collapseSmallGroups(groupsToCollapse, null);
            return;
        }

        List<Product> siblings = productRepository.findAllById(requested);
        if (siblings.size() != requested.size()) {
            throw new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND,
                    "Renk varyantı olarak seçilen bazı ürünler bulunamadı.");
        }

        // Target group: reuse the product's group, else a sibling's, else mint a fresh one.
        Long groupId = previousGroupId;
        if (groupId == null) {
            for (Product s : siblings) {
                if (s.getVariantGroupId() != null) { groupId = s.getVariantGroupId(); break; }
            }
        }
        if (groupId == null) {
            groupId = variantGroupRepository.save(new VariantGroup()).getId();
        }

        // Final membership = product + selected siblings.
        Set<Long> desired = new HashSet<>(requested);
        if (product.getId() != null) desired.add(product.getId());

        List<Product> toSave = new ArrayList<>();

        // Anyone in the product's previous group who is no longer selected leaves the group.
        if (previousGroupId != null) {
            for (Product m : productRepository.findByVariantGroupId(previousGroupId)) {
                if (m.getId().equals(product.getId())) continue; // handled via product itself
                if (!desired.contains(m.getId())) {
                    m.setVariantGroupId(null);
                    toSave.add(m);
                }
            }
        }

        // Pull each selected sibling into the target group (remember its old group to tidy up).
        for (Product s : siblings) {
            if (s.getVariantGroupId() != null && !s.getVariantGroupId().equals(groupId)) {
                groupsToCollapse.add(s.getVariantGroupId());
            }
            s.setVariantGroupId(groupId);
            toSave.add(s);
        }
        product.setVariantGroupId(groupId);

        if (!toSave.isEmpty()) productRepository.saveAll(toSave);

        groupsToCollapse.remove(groupId);
        collapseSmallGroups(groupsToCollapse, null);
    }

    /**
     * Empties any of the given groups that have fewer than two members (a single-color group is
     * meaningless). Members are matched on their in-memory variant group id so the not-yet-flushed
     * product that just left is not miscounted.
     */
    private void collapseSmallGroups(Set<Long> groupIds, Long ignore) {
        for (Long gid : groupIds) {
            if (gid == null || gid.equals(ignore)) continue;
            List<Product> members = productRepository.findByVariantGroupId(gid).stream()
                    .filter(m -> gid.equals(m.getVariantGroupId()))
                    .collect(java.util.stream.Collectors.toList());
            if (members.size() < 2) {
                members.forEach(m -> m.setVariantGroupId(null));
                if (!members.isEmpty()) productRepository.saveAll(members);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto.VariantSiblingDto> getVariantSiblings(Long variantGroupId, Long excludeProductId) {
        if (variantGroupId == null) return List.of();
        return productRepository.findByVariantGroupId(variantGroupId).stream()
                .filter(p -> excludeProductId == null || !p.getId().equals(excludeProductId))
                .sorted(java.util.Comparator.comparing(Product::getId))
                .map(p -> {
                    ProductDto.VariantSiblingDto d = new ProductDto.VariantSiblingDto();
                    d.id = p.getId();
                    d.name = p.getName();
                    d.sku = p.getSku();
                    d.active = p.isActive();
                    if (p.getColor() != null) {
                        d.colorName = p.getColor().getName();
                        d.colorHexCode = p.getColor().getHexCode();
                    }
                    return d;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getActiveVariantSiblings(Long variantGroupId) {
        if (variantGroupId == null) return List.of();
        return productRepository.findByVariantGroupIdAndIsActiveTrueOrderByIdAsc(variantGroupId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getActiveVariantSiblingsByGroups(java.util.Collection<Long> variantGroupIds) {
        if (variantGroupIds == null || variantGroupIds.isEmpty()) return List.of();
        return productRepository.findByVariantGroupIdInAndIsActiveTrue(variantGroupIds);
    }

    @Override
    public void deleteProduct(Long id) {
        logger.info("Deleting product with id: {}", id);
        Product product = getProductByIdOrThrow(id);
        // First check stock relations
        EntityValidator.validateEntityHasNoRelations(
            !product.getStocks().isEmpty(), EntityNames.PRODUCT, EntityNames.RELATION_STOCKS
        );

        // Then check transfer relations (both active and historical)
        var transfersUsingProduct = stockTransferRepository.findByProduct(product);
        if (transfersUsingProduct != null && !transfersUsingProduct.isEmpty()) {
            logger.warn("Cannot delete product with id {} because it is used in {} stock transfers",
                    id, transfersUsingProduct.size());
            throw new WarehouseManagementException(
                    ErrorCode.CANNOT_DELETE_PRODUCT_WITH_TRANSFERS,
                    "Bu ürün en az bir stok transferinde kullanılmıştır. Geçmiş transfer kayıtları silinmeden ürün silinemez."
            );
        }

        Long variantGroupId = product.getVariantGroupId();
        productRepository.delete(product);
        // FK is ON DELETE SET NULL, so the group survives — empty it if only one color is left.
        if (variantGroupId != null) collapseSmallGroups(java.util.Set.of(variantGroupId), null);
        logger.info("Product deleted successfully with id: {}", id);
        eventPublisher.publishEvent(ProductIndexEvent.delete(id));
    }

    @Override
    public BulkDeleteResponse deleteProducts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            logger.warn("Attempted to delete products with empty list");
            return new BulkDeleteResponse(0, 0, List.of());
        }
        
        logger.info("Deleting {} products", ids.size());
        List<BulkDeleteResponse.DeleteError> errors = new java.util.ArrayList<>();
        int successCount = 0;
        
        for (Long id : ids) {
            try {
                Product product = getProductByIdOrThrow(id);
                
                // First check stock relations
                if (!product.getStocks().isEmpty()) {
                    errors.add(new BulkDeleteResponse.DeleteError(
                        id,
                        product.getName(),
                        product.getSku(),
                        ErrorCode.CANNOT_DELETE_WITH_STOCKS.getCode(),
                        "Bu ürün stok kayıtlarında kullanılmaktadır. Stok kayıtları silinmeden ürün silinemez."
                    ));
                    continue;
                }
                
                // Then check transfer relations
                var transfersUsingProduct = stockTransferRepository.findByProduct(product);
                if (transfersUsingProduct != null && !transfersUsingProduct.isEmpty()) {
                    errors.add(new BulkDeleteResponse.DeleteError(
                        id,
                        product.getName(),
                        product.getSku(),
                        ErrorCode.CANNOT_DELETE_PRODUCT_WITH_TRANSFERS.getCode(),
                        "Bu ürün en az bir stok transferinde kullanılmıştır. Geçmiş transfer kayıtları silinmeden ürün silinemez."
                    ));
                    continue;
                }
                
                Long variantGroupId = product.getVariantGroupId();
                productRepository.delete(product);
                if (variantGroupId != null) collapseSmallGroups(java.util.Set.of(variantGroupId), null);
                successCount++;
                eventPublisher.publishEvent(ProductIndexEvent.delete(id));
                logger.debug("Product deleted successfully with id: {}", id);
            } catch (WarehouseManagementException e) {
                // Catch domain exceptions
                Product product = null;
                try {
                    product = getProductByIdOrThrow(id);
                } catch (Exception ex) {
                    // Product not found
                }
                errors.add(new BulkDeleteResponse.DeleteError(
                    id,
                    product != null ? product.getName() : "Bilinmeyen Ürün",
                    product != null ? product.getSku() : "N/A",
                    e.getErrorCode().getCode(),
                    e.getMessage()
                ));
                logger.warn("Cannot delete product with id {}: {}", id, e.getMessage());
            } catch (Exception e) {
                // Other errors
                Product product = null;
                try {
                    product = getProductByIdOrThrow(id);
                } catch (Exception ex) {
                    // Product not found
                }
                errors.add(new BulkDeleteResponse.DeleteError(
                    id,
                    product != null ? product.getName() : "Bilinmeyen Ürün",
                    product != null ? product.getSku() : "N/A",
                    ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                    "Ürün silinirken beklenmeyen bir hata oluştu: " + e.getMessage()
                ));
                logger.error("Error deleting product {}: {}", id, e.getMessage(), e);
            }
        }
        
        logger.info("Batch delete completed: {} successful, {} errors", successCount, errors.size());
        return new BulkDeleteResponse(successCount, errors.size(), errors);
    }

    @Override
    public void deactivateProduct(Long id) {
        logger.info("Deactivating product with id: {}", id);
        updateProductStatus(id, false);
    }

    @Override
    public void activateProduct(Long id) {
        logger.info("Activating product with id: {}", id);
        updateProductStatus(id, true);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySku(String sku) {
        return productRepository.existsBySku(sku);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> filterProductsByBrandAndColor(Long brandId, Long colorId) {
        logger.debug("Filtering products by brand id: {} and color id: {}", brandId, colorId);
        Brand brand = findBrandIfPresent(brandId);
        Color color = findColorIfPresent(colorId);
        return productRepository.findActiveByBrandAndColor(brand, color);
    }

    @Override
    public int bulkAdjustPrices(BulkPriceUpdateRequest request) {
        logger.info("Starting optimized bulk price adjustment");
        validateBulkRequest(request);
        
        boolean isIncrease = "INCREASE".equalsIgnoreCase(request.getDirection());
        boolean isPercentage = "PERCENTAGE".equalsIgnoreCase(request.getMode());
        BigDecimal value = request.getValue();

        int updatedCount;
        
        // Use JPQL bulk update for maximum performance
        // This executes a single UPDATE query directly in the database
        try {
            if (isPercentage) {
                if (isIncrease) {
                    updatedCount = productRepository.bulkUpdatePriceByPercentage(
                        value, 
                        request.getCategoryId(), 
                        request.getBrandId(), 
                        request.getColorId(), 
                        request.isOnlyActive()
                    );
                } else {
                    updatedCount = productRepository.bulkUpdatePriceByPercentageDecrease(
                        value, 
                        request.getCategoryId(), 
                        request.getBrandId(), 
                        request.getColorId(), 
                        request.isOnlyActive()
                    );
                }
            } else {
                if (isIncrease) {
                    updatedCount = productRepository.bulkUpdatePriceByAmount(
                        value, 
                        request.getCategoryId(), 
                        request.getBrandId(), 
                        request.getColorId(), 
                        request.isOnlyActive()
                    );
                } else {
                    updatedCount = productRepository.bulkUpdatePriceByAmountDecrease(
                        value, 
                        request.getCategoryId(), 
                        request.getBrandId(), 
                        request.getColorId(), 
                        request.isOnlyActive()
                    );
                }
            }
            
            logger.info("Optimized bulk price adjustment completed. Updated {} products", updatedCount);
            return updatedCount;
            
        } catch (Exception e) {
            logger.error("Error during bulk price update, falling back to batch processing", e);
            return bulkAdjustPricesWithBatchProcessing(request, isIncrease, isPercentage, value);
        }
    }

    /**
     * Fallback method using batch processing for bulk price updates.
     * Used when JPQL bulk update fails.
     */
    private int bulkAdjustPricesWithBatchProcessing(BulkPriceUpdateRequest request, 
                                                     boolean isIncrease, 
                                                     boolean isPercentage, 
                                                     BigDecimal value) {
        logger.info("Using batch processing for bulk price adjustment");
        
        // Use lightweight query without EntityGraph to reduce memory footprint
        List<Product> targets = productRepository.findByOptionalFiltersLightweight(
                request.getCategoryId(), request.getBrandId(), request.getColorId(), request.isOnlyActive()
        );
        
        if (targets.isEmpty()) {
            logger.warn(BusinessMessages.NO_PRODUCTS_FOR_BULK);
            return 0;
        }

        // Process in batches to avoid memory issues with large datasets
        int batchSize = 500; // Process 500 products at a time
        int totalUpdated = 0;
        
        for (int i = 0; i < targets.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, targets.size());
            List<Product> batch = targets.subList(i, endIndex);
            
            for (Product product : batch) {
                BigDecimal current = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                BigDecimal updated = calculateNewPrice(current, value, isIncrease, isPercentage);
                product.setPrice(updated.setScale(2, RoundingMode.HALF_UP));
            }
            
            productRepository.saveAll(batch);
            totalUpdated += batch.size();
            
            logger.debug("Processed batch {}/{}, total updated: {}", 
                        (i / batchSize) + 1, 
                        (targets.size() + batchSize - 1) / batchSize, 
                        totalUpdated);
        }

        logger.info("Batch processing completed. Updated {} products", totalUpdated);
        return totalUpdated;
    }

    @Override
    @Transactional(readOnly = true)
    public Product getProductBySlug(String slug) {
        Optional<Product> bySlug = productRepository.findBySlug(slug);
        if (bySlug.isPresent()) return bySlug.get();

        // Fallback: legacy/broken links where the path collapsed to a leading numeric id
        // (e.g. "/urun/32" because the real slug contained an unsafe character). Resolve by id.
        if (slug != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)").matcher(slug);
            if (m.find()) {
                try {
                    Long id = Long.parseLong(m.group(1));
                    Optional<Product> byId = productRepository.findByIdWithRelations(id);
                    if (byId.isPresent()) return byId.get();
                } catch (NumberFormatException ignored) {
                    // not a usable id — fall through
                }
            }
        }
        throw new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND, "Slug: " + slug);
    }

    /** Build a URL-safe slug from {@code base}; fall back to the SKU, then "urun". */
    private static String safeSlug(String base, String sku) {
        String s = slugify(base);
        if (s == null || s.isBlank()) s = slugify(sku);
        if (s == null || s.isBlank()) s = "urun";
        return s;
    }

    /**
     * Lowercases, transliterates Turkish characters and strips everything that is not
     * {@code [a-z0-9-]} so the slug is always safe to place in a URL path.
     */
    private static String slugify(String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase(java.util.Locale.forLanguageTag("tr"));
        s = s.replace('ç', 'c').replace('ğ', 'g').replace('ı', 'i')
             .replace('ö', 'o').replace('ş', 's').replace('ü', 'u')
             .replace('â', 'a').replace('î', 'i').replace('û', 'u');
        s = s.replaceAll("[^a-z0-9]+", "-");          // unsafe runs → single dash
        s = s.replaceAll("(^-+)|(-+$)", "");           // trim leading/trailing dashes
        return s;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> getAllActiveProducts(Pageable pageable, String search, Long categoryId, Long brandId, Long colorId) {
        return productRepository.findActiveByFilters(search, categoryId, brandId, colorId, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> getAllActiveProducts(Pageable pageable, String search, Long categoryId, Long brandId, Long colorId,
                                              ProductType productType) {
        return productRepository.findActiveByFilters(search, categoryId, brandId, colorId, productType, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> getAllActiveProductsMultiFilter(Pageable pageable, String search, Long categoryId, java.util.List<Long> brandIds, java.util.List<Long> colorIds) {
        return productRepository.findActiveByMultiFilters(search, categoryId, brandIds, colorIds, pageable);
    }

    private void validateSkuUniqueness(String sku) {
        if (productRepository.existsBySku(sku)) {
            logger.warn("SKU already exists: {}", sku);
            throw new WarehouseManagementException(ErrorCode.PRODUCT_SKU_ALREADY_EXISTS, BusinessMessages.SKU_PREFIX + sku);
        }
    }

    /**
     * The products.slug column has a unique index; the slug is derived from the
     * product name, so a duplicate slug means "a product/set with this name already
     * exists". Caught here proactively so the admin gets a clear Turkish message
     * instead of a raw data-integrity error.
     */
    private void validateSlugUniqueness(String slug, Long ownId) {
        productRepository.findBySlug(slug)
                .filter(existing -> ownId == null || !existing.getId().equals(ownId))
                .ifPresent(existing -> {
                    logger.warn("Slug already exists: {} (product id {})", slug, existing.getId());
                    throw new WarehouseManagementException(ErrorCode.PRODUCT_NAME_ALREADY_EXISTS,
                            ErrorCode.PRODUCT_NAME_ALREADY_EXISTS.getMessage()
                                    + " (Mevcut kayıt: \"" + existing.getName() + "\")");
                });
    }

    private void validateSkuUniquenessOnUpdate(Product product, Product productDetails) {
        if (!product.getSku().equals(productDetails.getSku()) &&
            productRepository.existsBySku(productDetails.getSku())) {
            logger.warn("SKU already exists for update: {}", productDetails.getSku());
            throw new WarehouseManagementException(ErrorCode.PRODUCT_SKU_ALREADY_EXISTS, BusinessMessages.SKU_PREFIX + productDetails.getSku());
        }
    }

    private void setBrandIfPresent(Product product) {
        if (product.getBrand() != null && product.getBrand().getId() != null) {
            Brand brand = brandRepository.findById(product.getBrand().getId())
                    .orElseThrow(() -> {
                        logger.warn("Brand not found with id: {}", product.getBrand().getId());
                        return new WarehouseManagementException(ErrorCode.BRAND_NOT_FOUND, BusinessMessages.ID_PREFIX + product.getBrand().getId());
                    });
            product.setBrand(brand);
        } else {
            product.setBrand(null);
        }
    }

    private void setColorIfPresent(Product product) {
        if (product.getColor() != null && product.getColor().getId() != null) {
            Color color = colorRepository.findById(product.getColor().getId())
                    .orElseThrow(() -> {
                        logger.warn("Color not found with id: {}", product.getColor().getId());
                        return new WarehouseManagementException(ErrorCode.COLOR_NOT_FOUND, BusinessMessages.ID_PREFIX + product.getColor().getId());
                    });
            product.setColor(color);
        } else {
            product.setColor(null);
        }
    }

    private void updateCategory(Product product, Product productDetails) {
        if (productDetails.getCategory() != null &&
            productDetails.getCategory().getId() != null &&
            !product.getCategory().getId().equals(productDetails.getCategory().getId())) {
            Category category = categoryRepository.findById(productDetails.getCategory().getId())
                    .orElseThrow(() -> {
                        logger.warn("Category not found with id: {}", productDetails.getCategory().getId());
                        return new WarehouseManagementException(ErrorCode.CATEGORY_NOT_FOUND, BusinessMessages.ID_PREFIX + productDetails.getCategory().getId());
                    });
            product.setCategory(category);
        }
    }

    private void updateBrand(Product product, Product productDetails) {
        if (productDetails.getBrand() != null && productDetails.getBrand().getId() != null) {
            if (product.getBrand() == null || !product.getBrand().getId().equals(productDetails.getBrand().getId())) {
                Brand brand = brandRepository.findById(productDetails.getBrand().getId())
                        .orElseThrow(() -> {
                            logger.warn("Brand not found with id: {}", productDetails.getBrand().getId());
                            return new WarehouseManagementException(ErrorCode.BRAND_NOT_FOUND, BusinessMessages.ID_PREFIX + productDetails.getBrand().getId());
                        });
                product.setBrand(brand);
            }
        } else {
            product.setBrand(null);
        }
    }

    private void updateColor(Product product, Product productDetails) {
        if (productDetails.getColor() != null && productDetails.getColor().getId() != null) {
            if (product.getColor() == null || !product.getColor().getId().equals(productDetails.getColor().getId())) {
                Color color = colorRepository.findById(productDetails.getColor().getId())
                        .orElseThrow(() -> {
                            logger.warn("Color not found with id: {}", productDetails.getColor().getId());
                            return new WarehouseManagementException(ErrorCode.COLOR_NOT_FOUND, BusinessMessages.ID_PREFIX + productDetails.getColor().getId());
                        });
                product.setColor(color);
            }
        } else {
            product.setColor(null);
        }
    }

    private void updateProductFields(Product product, Product productDetails) {
        product.setName(productDetails.getName());
        product.setDescription(productDetails.getDescription());
        product.setShortDescription(productDetails.getShortDescription());
        product.setSku(productDetails.getSku());
        product.setPrice(productDetails.getPrice());
        product.setSalePrice(productDetails.getSalePrice());
        product.setSaleStart(productDetails.getSaleStart());
        product.setSaleEnd(productDetails.getSaleEnd());
        product.setFeatured(productDetails.isFeatured());
        product.setNew(productDetails.isNew());
        product.setWeight(productDetails.getWeight());
        product.setDimensions(productDetails.getDimensions());
        product.setLengthCm(productDetails.getLengthCm());
        product.setWidthCm(productDetails.getWidthCm());
        product.setHeightCm(productDetails.getHeightCm());
        product.setShippingRate(productDetails.getShippingRate());
        product.setVatRate(productDetails.getVatRate());
        product.setSctRate(productDetails.getSctRate());
        product.setActive(productDetails.isActive());
        if (productDetails.getSlug() != null) {
            String newSlug = safeSlug(productDetails.getSlug(), product.getSku());
            validateSlugUniqueness(newSlug, product.getId());
            product.setSlug(newSlug);
        }
        if (productDetails.getMetaTitle() != null) product.setMetaTitle(productDetails.getMetaTitle());
        if (productDetails.getMetaDescription() != null) product.setMetaDescription(productDetails.getMetaDescription());
        // Null-guarded: ProductForm always sends these (empty list/blank = clear),
        // while leaner payloads (e.g. the set editor) omit them — omitting must not
        // wipe existing values. Without these lines crawled specs were silently
        // dropped on every product update.
        if (productDetails.getTechnicalSpecs() != null) product.setTechnicalSpecs(productDetails.getTechnicalSpecs());
        if (productDetails.getWarrantyMonths() != null) product.setWarrantyMonths(productDetails.getWarrantyMonths());
        if (productDetails.getWarrantyText() != null) product.setWarrantyText(productDetails.getWarrantyText());
    }

    private void updateProductStatus(Long id, boolean isActive) {
        Product product = getProductByIdOrThrow(id);
        product.setActive(isActive);
        productRepository.save(product);
        logger.debug("Product status updated. Id: {}, Active: {}", id, isActive);
    }

    private Brand findBrandIfPresent(Long brandId) {
        if (brandId != null) {
            return brandRepository.findById(brandId)
                    .orElseThrow(() -> {
                        logger.warn("Brand not found with id: {}", brandId);
                        return new WarehouseManagementException(ErrorCode.BRAND_NOT_FOUND, "ID: " + brandId);
                    });
        }
        return null;
    }

    private Color findColorIfPresent(Long colorId) {
        if (colorId != null) {
            return colorRepository.findById(colorId)
                    .orElseThrow(() -> {
                        logger.warn("Color not found with id: {}", colorId);
                        return new WarehouseManagementException(ErrorCode.COLOR_NOT_FOUND, "ID: " + colorId);
                    });
        }
        return null;
    }

    private BigDecimal calculateNewPrice(BigDecimal current, BigDecimal value, boolean isIncrease, boolean isPercentage) {
        if (isPercentage) {
            BigDecimal factor = value.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            BigDecimal multiplier = isIncrease ? BigDecimal.ONE.add(factor) : BigDecimal.ONE.subtract(factor);
            return current.multiply(multiplier);
        } else {
            BigDecimal delta = value;
            BigDecimal result = isIncrease ? current.add(delta) : current.subtract(delta);
            return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
        }
    }

    private void validateBulkRequest(BulkPriceUpdateRequest request) {
        if (request.getMode() == null || request.getValue() == null || request.getDirection() == null) {
            throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING, BusinessMessages.REQUIRED_MODE_VALUE_DIRECTION);
        }
        if (!"PERCENTAGE".equalsIgnoreCase(request.getMode()) && !"AMOUNT".equalsIgnoreCase(request.getMode())) {
            throw new WarehouseManagementException(ErrorCode.INVALID_VALUE, BusinessMessages.INVALID_MODE);
        }
        if (!"INCREASE".equalsIgnoreCase(request.getDirection()) && !"DECREASE".equalsIgnoreCase(request.getDirection())) {
            throw new WarehouseManagementException(ErrorCode.INVALID_VALUE, BusinessMessages.INVALID_DIRECTION);
        }
        if (request.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new WarehouseManagementException(ErrorCode.VALUE_MUST_BE_POSITIVE, BusinessMessages.VALUE_MUST_BE_POSITIVE);
        }
        if ("PERCENTAGE".equalsIgnoreCase(request.getMode()) && request.getValue().compareTo(BigDecimal.valueOf(1000)) > 0) {
            throw new WarehouseManagementException(ErrorCode.INVALID_VALUE, BusinessMessages.PERCENTAGE_TOO_HIGH);
        }
    }
}

