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

    ProductImage getImageOrThrow(Long imageId);
}


