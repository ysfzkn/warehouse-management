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
     * Stores the given image stream as a site asset (logo, favicon, etc.).
     *
     * @param assetName     logical name (e.g. "logo", "favicon")
     * @param originalFileName original filename (used only for extension)
     * @param contentType   mime type
     * @param inputStream   image content
     * @return metadata of the stored optimized image + thumbnail
     */
    StoredPhoto storeSiteAsset(String assetName,
                               String originalFileName,
                               String contentType,
                               InputStream inputStream);

    /** Returns the resolved site assets directory path */
    java.nio.file.Path getSiteAssetDir();

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

    // ─────────────────────────────────────────────────────────────────────
    // Generic document storage (PDF invoices, XLSX imports, RAG documents)
    // Unlike the photo paths, it performs no image optimization; it stores the
    // binary file as-is. All file writes must go through this interface —
    // filesystem in local dev, Railway/S3 bucket in prod.
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Generic file storage. For non-image files or files that require no
     * processing (PDF, XLSX, TXT, DOCX, etc.).
     *
     * @param prefix             logical folder prefix, e.g. {@code "invoices/123"},
     *                           {@code "imports/45"}, {@code "assistant-documents/9"}
     * @param originalFileName   actual file name (used to derive the extension)
     * @param contentType        mime type
     * @param inputStream        binary content
     * @return the stored key — this string is persisted in the DB and can later
     *         be reopened via {@link #openDocumentStream(String)}.
     */
    String storeDocument(String prefix,
                         String originalFileName,
                         String contentType,
                         InputStream inputStream);

    /**
     * Opens an InputStream from a document key. The caller is responsible for closing it.
     * Throws {@link RuntimeException} for a non-existent key.
     */
    InputStream openDocumentStream(String key);

    /**
     * Deletes the document. A non-existent key is silently ignored (idempotent delete).
     */
    void deleteDocument(String key);
}


