package com.warehouse.service.impl;

import com.warehouse.service.PhotoStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import jakarta.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * S3-compatible object storage backend.
 *
 * <p>The same code works with **MinIO** (Docker compose) in dev and with **AWS S3 /
 * Cloudflare R2 / Backblaze B2 / DigitalOcean Spaces** in prod — only the
 * endpoint + credentials change.</p>
 *
 * <p>Bucket structure:
 * <pre>
 *   warehouse-uploads/
 *     ├── transfers/{transferId}/{itemId}/{uuid}.webp
 *     ├── transfers/{transferId}/{itemId}/{uuid}_thumb.webp
 *     ├── products/{productId}/{uuid}.webp
 *     ├── products/{productId}/{uuid}_thumb.webp
 *     └── assets/{name}-{uuid}.{ext}   ← public-read (logo, banner)
 * </pre></p>
 *
 * <p>Activation: the {@code STORAGE_PROVIDER=s3} env variable. {@link LocalPhotoStorageService}
 * is conditionally disabled; since both implement the same PhotoStorageService interface,
 * the calling code does not change.</p>
 */
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3")
@Slf4j
public class S3PhotoStorageService implements PhotoStorageService {

    @Value("${storage.s3.endpoint:}")
    private String endpoint;

    @Value("${storage.s3.bucket:warehouse-uploads}")
    private String bucket;

    @Value("${storage.s3.access-key:}")
    private String accessKey;

    @Value("${storage.s3.secret-key:}")
    private String secretKey;

    @Value("${storage.s3.region:us-east-1}")
    private String region;

    @Value("${storage.s3.public-base-url:}")
    private String publicBaseUrl;

    /** Path-style addressing — MinIO + R2 + Oracle require path-style; AWS S3 also works with virtual-host. */
    @Value("${storage.s3.path-style:true}")
    private boolean pathStyle;

    /**
     * Public-read ACL support. Although Oracle Object Storage is S3-compatible, it
     * rejects this ACL with "NotImplemented". For Oracle, set this to false; make the
     * bucket public manually or use a Pre-Authenticated Request (ORACLE_CLOUD_SETUP.md).
     * MinIO/AWS/R2/B2 support it → true (default).
     */
    @Value("${storage.s3.public-acl-supported:true}")
    private boolean publicAclSupported;

    private S3Client s3;

    @PostConstruct
    void init() {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException(
                    "S3 storage aktif ama endpoint boş. STORAGE_S3_ENDPOINT env değişkenini doldurun " +
                    "(MinIO local için http://localhost:9000, prod için provider URL).");
        }
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "S3 storage aktif ama credential eksik. STORAGE_S3_ACCESS_KEY / STORAGE_S3_SECRET_KEY ayarlayın.");
        }
        s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build())
                .build();
        // Check whether the bucket exists — if not, try to create it (the MinIO init script already does this)
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("S3 storage hazır: endpoint={}, bucket={}, pathStyle={}", endpoint, bucket, pathStyle);
        } catch (NoSuchBucketException e) {
            log.warn("S3 bucket '{}' bulunamadı — oluşturulmaya çalışılıyor", bucket);
            try {
                s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("S3 bucket oluşturuldu: {}", bucket);
            } catch (Exception ce) {
                log.error("S3 bucket oluşturulamadı: {}", ce.getMessage());
            }
        } catch (Exception e) {
            log.error("S3 bucket kontrol hatası: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Public storage operations (PhotoStorageService interface)
    // ─────────────────────────────────────────────────────────────

    @Override
    public StoredPhoto storeItemPhoto(Long transferId, Long itemId,
                                       String originalFileName, String contentType,
                                       InputStream inputStream) {
        String prefix = "transfers/" + transferId + "/" + itemId;
        return storeOptimized(prefix, originalFileName, contentType, inputStream);
    }

    @Override
    public StoredPhoto storeProductImage(Long productId,
                                          String originalFileName, String contentType,
                                          InputStream inputStream) {
        String prefix = "products/" + productId;
        return storeOptimized(prefix, originalFileName, contentType, inputStream);
    }

    @Override
    public StoredPhoto storeSiteAsset(String assetName, String originalFileName,
                                       String contentType, InputStream inputStream) {
        // Separate prefix because site assets are public-read
        String ext = inferExtension(originalFileName, contentType);
        String key = "assets/" + sanitize(assetName) + "-" + shortUuid() + ext;
        try {
            byte[] bytes = inputStream.readAllBytes();
            putObject(key, contentType, bytes, true /* public */);
            log.info("S3 asset stored: {} ({} bytes)", key, bytes.length);
            return new StoredPhoto(key, key, key, contentType, bytes.length, null, null);
        } catch (IOException e) {
            throw new RuntimeException("S3 asset yüklenemedi: " + e.getMessage(), e);
        }
    }

    @Override
    public Path getSiteAssetDir() {
        // S3 has no concept of directories; by convention: an "assets/" prefix within the bucket.
        // Returns a sentinel path for backward compatibility; callers are expected not to
        // use it directly as a filesystem path.
        return Paths.get("s3://" + bucket + "/assets");
    }

    @Override
    public void deletePhotoFiles(String relativePath, String thumbnailPath) {
        try {
            if (relativePath != null && !relativePath.isBlank()) {
                s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(relativePath).build());
            }
            if (thumbnailPath != null && !thumbnailPath.isBlank()) {
                s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(thumbnailPath).build());
            }
        } catch (Exception e) {
            log.warn("S3 delete fail: {} → {}", relativePath, e.getMessage());
        }
    }

    @Override
    public InputStream openPhotoStream(String relativePath) {
        return openStream(relativePath);
    }

    @Override
    public InputStream openThumbnailStream(String thumbnailPath) {
        return openStream(thumbnailPath);
    }

    // ─── Generic document storage ─────────────────────────────────

    @Override
    public String storeDocument(String prefix, String originalFileName,
                                 String contentType, InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            String ext = inferExtension(originalFileName, contentType);
            String key = prefix + "/" + shortUuid() + ext;
            putObject(key, contentType != null ? contentType : "application/octet-stream",
                    bytes, false);
            log.info("S3 document stored: {} ({} bytes)", key, bytes.length);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("S3 document yüklenemedi: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream openDocumentStream(String key) {
        return openStream(key);
    }

    @Override
    public void deleteDocument(String key) {
        if (key == null || key.isBlank()) return;
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            log.warn("S3 document delete fail: {} → {}", key, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Internals
    // ─────────────────────────────────────────────────────────────

    /**
     * WebP-optimized image + thumbnail upload. Mimics the behavior of
     * LocalPhotoStorageService but writes to S3.
     */
    private StoredPhoto storeOptimized(String prefix, String originalFileName,
                                        String contentType, InputStream inputStream) {
        try {
            byte[] original = inputStream.readAllBytes();
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(original));
            if (img == null) {
                // Could not be parsed as an image (WebP/SVG/PDF etc. — Java ImageIO does
                // not read WebP). Upload as-is; use the main file for the thumbnail
                // (thumbnail_path is NOT NULL in the DB, and the frontend expects a thumb
                // URL in all cases anyway, for fallback behavior).
                String ext = inferExtension(originalFileName, contentType);
                String key = prefix + "/" + shortUuid() + ext;
                putObject(key, contentType, original, false);
                return new StoredPhoto(key, key, key, contentType, original.length, null, null);
                //                          ^^^ thumbnailPath = main key (same file)
            }

            String uuid = shortUuid();
            String mainKey = prefix + "/" + uuid + ".webp";
            String thumbKey = prefix + "/" + uuid + "_thumb.webp";

            byte[] mainBytes = encodeWebp(img, 0.85f);
            byte[] thumbBytes = encodeWebp(resizeMax(img, 400), 0.80f);

            putObject(mainKey, "image/webp", mainBytes, false);
            putObject(thumbKey, "image/webp", thumbBytes, false);

            log.debug("S3 image stored: {} ({}KB main, {}KB thumb)",
                    mainKey, mainBytes.length / 1024, thumbBytes.length / 1024);

            return new StoredPhoto(uuid + ".webp", mainKey, thumbKey, "image/webp",
                    mainBytes.length, img.getWidth(), img.getHeight());
        } catch (IOException e) {
            throw new RuntimeException("S3 image upload failed: " + e.getMessage(), e);
        }
    }

    private void putObject(String key, String contentType, byte[] bytes, boolean publicRead) {
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength((long) bytes.length);
        // ACL is set only on providers that support it (except Oracle Object Storage).
        // For Oracle, you need to make the bucket public manually or use a PAR.
        if (publicRead && publicAclSupported) {
            builder.acl(ObjectCannedACL.PUBLIC_READ);
        }
        s3.putObject(builder.build(), RequestBody.fromBytes(bytes));
    }

    private InputStream openStream(String key) {
        if (key == null || key.isBlank()) {
            return InputStream.nullInputStream();
        }
        try {
            return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException e) {
            log.warn("S3 object not found: {}", key);
            return InputStream.nullInputStream();
        }
    }

    // WebP encoding — works via javax.imageio if a WebP writer is present on the
    // user's system (TwelveMonkeys imageio-webp). Otherwise falls back to JPEG.
    private byte[] encodeWebp(BufferedImage img, float quality) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // Use the WebP writer if present
            var iter = ImageIO.getImageWritersByMIMEType("image/webp");
            if (iter.hasNext()) {
                var writer = iter.next();
                var param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                }
                try (var out = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(out);
                    writer.write(null, new javax.imageio.IIOImage(img, null, null), param);
                }
                writer.dispose();
                return baos.toByteArray();
            }
            // Fallback: JPEG. The JPEG writer cannot encode an alpha channel — handed
            // an ARGB image it writes an empty/garbage file (the failure that left
            // transparent product photos blank). Flatten onto white first.
            ImageIO.write(toOpaqueRgb(img), "jpg", baos);
            return baos.toByteArray();
        }
    }

    /** Composites any image with transparency onto a white opaque RGB canvas (JPEG-safe). */
    private static BufferedImage toOpaqueRgb(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_RGB && !img.getColorModel().hasAlpha()) {
            return img;
        }
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = rgb.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private BufferedImage resizeMax(BufferedImage src, int maxDim) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;
        double scale = Math.min((double) maxDim / w, (double) maxDim / h);
        int nw = (int) (w * scale);
        int nh = (int) (h * scale);
        BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        var g = scaled.createGraphics();
        // TYPE_INT_RGB defaults to black; paint white so transparent source images
        // don't get a black background once flattened.
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, nw, nh);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return scaled;
    }

    private String inferExtension(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
            if (ext.length() <= 6) return ext;
        }
        if (contentType != null) {
            if (contentType.contains("png")) return ".png";
            if (contentType.contains("webp")) return ".webp";
            if (contentType.contains("svg")) return ".svg";
            if (contentType.contains("gif")) return ".gif";
            if (contentType.contains("jpeg") || contentType.contains("jpg")) return ".jpg";
        }
        return ".bin";
    }

    private String sanitize(String s) {
        if (s == null) return "asset";
        return s.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
    }

    private String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Public URL generator — so callers can use asset URLs when sending them to the
     * admin panel. If {@code storage.s3.public-base-url} is set, it uses that
     * (for a Cloudflare R2 custom domain); otherwise the default S3 URL.
     */
    public String publicUrl(String key) {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl.replaceAll("/+$", "") + "/" + key;
        }
        return endpoint.replaceAll("/+$", "") + "/" + bucket + "/" + key;
    }
}
