package com.warehouse.service;

import java.io.InputStream;

public interface PhotoStorageService {

    record StoredPhoto(
            String fileName,
            String relativePath,
            String thumbnailPath,
            String contentType,
            long sizeBytes,
            Integer width,
            Integer height
    ) {}

    /**
     * Stores the given image stream for a specific stock transfer item.
     *
     * @param transferId stock transfer id (for directory partitioning)
     * @param itemId     stock transfer item id
     * @param originalFileName original filename (used only for extension)
     * @param contentType mime type
     * @param inputStream image content
     * @return metadata of the stored optimized image + thumbnail
     */
    StoredPhoto storeItemPhoto(Long transferId,
                               Long itemId,
                               String originalFileName,
                               String contentType,
                               InputStream inputStream);

    /**
     * Stores the given image stream for a specific product.
     *
     * @param productId product id (for directory partitioning)
     * @param originalFileName original filename (used only for extension)
     * @param contentType mime type
     * @param inputStream image content
     * @return metadata of the stored optimized image + thumbnail
     */
    StoredPhoto storeProductImage(Long productId,
                                  String originalFileName,
                                  String contentType,
                                  InputStream inputStream);

    /**
     * Deletes the photo files for a given relative path and thumbnail path.
     */
    void deletePhotoFiles(String relativePath, String thumbnailPath);

    /**
     * Resolves an InputStream for the given relative path (optimized image).
     */
    InputStream openPhotoStream(String relativePath);

    /**
     * Resolves an InputStream for the given thumbnail path.
     */
    InputStream openThumbnailStream(String thumbnailPath);
}


