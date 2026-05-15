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
    // Photo path'lerinden farklı olarak resim optimizasyonu yapmaz; binary
    // dosyayı olduğu gibi kaydeder. Tüm dosya yazımı bu interface üzerinden
    // geçmeli — local dev'de filesystem, prod'da Railway/S3 bucket.
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Generic dosya kaydı. Resim olmayan ya da işlem gerektirmeyen
     * dosyalar için (PDF, XLSX, TXT, DOCX vb.).
     *
     * @param prefix             logical klasör prefix'i, örn. {@code "invoices/123"},
     *                           {@code "imports/45"}, {@code "assistant-documents/9"}
     * @param originalFileName   gerçek dosya adı (uzantı çıkarımı için)
     * @param contentType        mime type
     * @param inputStream        binary içerik
     * @return depolanan key — DB'de bu string saklanacak; daha sonra
     *         {@link #openDocumentStream(String)} ile geri açılır.
     */
    String storeDocument(String prefix,
                         String originalFileName,
                         String contentType,
                         InputStream inputStream);

    /**
     * Document key'den InputStream açar. Caller close etmekle yükümlü.
     * Var olmayan key için {@link RuntimeException} fırlatır.
     */
    InputStream openDocumentStream(String key);

    /**
     * Document'i siler. Var olmayan key sessizce yutulur (idempotent delete).
     */
    void deleteDocument(String key);
}


