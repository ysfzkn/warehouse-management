package com.warehouse.service.impl;

import com.warehouse.entity.Product;
import com.warehouse.entity.ProductImage;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.ProductImageRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.service.PhotoStorageService;
import com.warehouse.service.ProductImageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductImageServiceImpl implements ProductImageService {

    private static final Logger logger = LoggerFactory.getLogger(ProductImageServiceImpl.class);

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final PhotoStorageService photoStorageService;

    @Override
    @Transactional(readOnly = true)
    public List<ProductImage> getImagesForProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND));
        return imageRepository.findByProductOrderBySortOrderAscIdAsc(product);
    }

    @Override
    public ProductImage addImageToProduct(Long productId,
                                          String originalFileName,
                                          String contentType,
                                          InputStream inputStream,
                                          boolean primary) {
        return addImageToProduct(productId, originalFileName, contentType, inputStream, primary, null);
    }

    @Override
    public ProductImage addImageToProduct(Long productId,
                                          String originalFileName,
                                          String contentType,
                                          InputStream inputStream,
                                          boolean primary,
                                          Integer slot) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND));

        if (slot != null && (slot < 1 || slot > 3)) {
            throw new WarehouseManagementException(ErrorCode.INVALID_VALUE);
        }

        List<ProductImage> all = imageRepository.findByProductOrderBySortOrderAscIdAsc(product);
        // Gallery ordering/primary logic considers only non-slot images so the
        // triple-set slots never affect the main gallery.
        List<ProductImage> gallery = all.stream()
                .filter(img -> img.getSlot() == null)
                .collect(java.util.stream.Collectors.toList());

        PhotoStorageService.StoredPhoto stored = photoStorageService.storeProductImage(
                product.getId(),
                originalFileName,
                contentType,
                inputStream
        );

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setFileName(stored.fileName());
        image.setRelativePath(stored.relativePath());
        image.setThumbnailPath(stored.thumbnailPath());
        image.setContentType(stored.contentType());
        image.setSizeBytes(stored.sizeBytes());
        image.setWidth(stored.width());
        image.setHeight(stored.height());

        if (slot != null) {
            // Triple-set slot: replace any existing image in this slot, never touch
            // the gallery's primary/order.
            all.stream()
                    .filter(img -> slot.equals(img.getSlot()))
                    .forEach(existingSlot -> deleteImage(existingSlot.getId()));
            image.setSlot(slot);
            image.setSortOrder(slot);
            image.setPrimary(false);
            ProductImage savedSlot = imageRepository.save(image);
            logger.info("Product set slot {} image stored for product {} with id {}", slot, productId, savedSlot.getId());
            return savedSlot;
        }

        int nextSortOrder = gallery.stream()
                .map(ProductImage::getSortOrder)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
        image.setSortOrder(nextSortOrder);

        if (primary || gallery.isEmpty()) {
            // Unset previous primary if needed
            gallery.stream()
                    .filter(ProductImage::isPrimary)
                    .forEach(img -> img.setPrimary(false));
            image.setPrimary(true);
        } else {
            image.setPrimary(false);
        }

        gallery.forEach(imageRepository::save);
        ProductImage saved = imageRepository.save(image);
        logger.info("Product image stored for product {} with id {}", productId, saved.getId());
        return saved;
    }

    @Override
    public void deleteImage(Long imageId) {
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND));
        boolean wasPrimary = image.isPrimary();
        var product = image.getProduct();
        photoStorageService.deletePhotoFiles(image.getRelativePath(), image.getThumbnailPath());
        imageRepository.delete(image);

        // If we removed the primary image, promote the next one (by sort order) so
        // the product never ends up without a primary → broken first image on store.
        if (wasPrimary && product != null) {
            List<ProductImage> remaining = imageRepository.findByProductOrderBySortOrderAscIdAsc(product);
            if (!remaining.isEmpty() && remaining.stream().noneMatch(ProductImage::isPrimary)) {
                remaining.get(0).setPrimary(true);
                imageRepository.save(remaining.get(0));
            }
        }
    }

    @Override
    public void setPrimaryImage(Long imageId) {
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND));
        // Get all images for the same product and update primary flags
        List<ProductImage> allImages = imageRepository.findByProductOrderBySortOrderAscIdAsc(image.getProduct());
        for (ProductImage img : allImages) {
            img.setPrimary(img.getId().equals(imageId));
        }
        imageRepository.saveAll(allImages);
    }

    @Override
    public void reorderImages(Long productId, List<Long> orderedImageIds) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND));
        List<ProductImage> images = imageRepository.findByProductOrderBySortOrderAscIdAsc(product);
        java.util.Map<Long, ProductImage> byId = new java.util.HashMap<>();
        for (ProductImage img : images) byId.put(img.getId(), img);

        int order = 0;
        // Apply the requested order for known ids first.
        for (Long id : orderedImageIds) {
            ProductImage img = byId.remove(id);
            if (img != null) img.setSortOrder(order++);
        }
        // Any images not included in the payload keep a stable order at the end.
        for (ProductImage img : byId.values()) img.setSortOrder(order++);

        imageRepository.saveAll(images);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductImage getImageOrThrow(Long imageId) {
        return imageRepository.findById(imageId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND));
    }
}


