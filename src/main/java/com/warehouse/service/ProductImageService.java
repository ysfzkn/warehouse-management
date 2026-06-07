package com.warehouse.service;

import com.warehouse.entity.ProductImage;

import java.io.InputStream;
import java.util.List;

public interface ProductImageService {

    List<ProductImage> getImagesForProduct(Long productId);

    ProductImage addImageToProduct(Long productId,
                                   String originalFileName,
                                   String contentType,
                                   InputStream inputStream,
                                   boolean primary);

    void deleteImage(Long imageId);

    void setPrimaryImage(Long imageId);

    /** Persist a new display order for a product's images (drag-and-drop). */
    void reorderImages(Long productId, List<Long> orderedImageIds);

    ProductImage getImageOrThrow(Long imageId);
}


