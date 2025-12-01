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
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND));

        List<ProductImage> existing = imageRepository.findByProductOrderBySortOrderAscIdAsc(product);
        int nextSortOrder = existing.stream()
                .map(ProductImage::getSortOrder)
                .max(Integer::compareTo)
                .orElse(-1) + 1;

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
        image.setSortOrder(nextSortOrder);

        if (primary || existing.isEmpty()) {
            // Unset previous primary if needed
            existing.stream()
                    .filter(ProductImage::isPrimary)
                    .forEach(img -> img.setPrimary(false));
            image.setPrimary(true);
        } else {
            image.setPrimary(false);
        }

        existing.forEach(imageRepository::save);
        ProductImage saved = imageRepository.save(image);
        logger.info("Product image stored for product {} with id {}", productId, saved.getId());
        return saved;
    }

    @Override
    public void deleteImage(Long imageId) {
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND));
        photoStorageService.deletePhotoFiles(image.getRelativePath(), image.getThumbnailPath());
        imageRepository.delete(image);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductImage getImageOrThrow(Long imageId) {
        return imageRepository.findById(imageId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND));
    }
}


