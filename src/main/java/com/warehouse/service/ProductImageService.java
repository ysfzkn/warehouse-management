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

    /**
     * Add an image to a product, optionally pinned to a triple-set slot.
     *
     * @param slot {@code null} for a normal gallery image, or {@code 1}/{@code 2}/{@code 3}
     *             for a dedicated set slot. When a slot is given, any existing image in
     *             that slot for the product is replaced, and the gallery's ordering and
     *             primary-image flags are left untouched.
     */
    ProductImage addImageToProduct(Long productId,
                                   String originalFileName,
                                   String contentType,
                                   InputStream inputStream,
                                   boolean primary,
                                   Integer slot);

    void deleteImage(Long imageId);

    void setPrimaryImage(Long imageId);

    /** Persist a new display order for a product's images (drag-and-drop). */
    void reorderImages(Long productId, List<Long> orderedImageIds);

    ProductImage getImageOrThrow(Long imageId);
}


