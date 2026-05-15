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
 * <p>Aynı kod hem dev'de **MinIO** (Docker compose), hem prod'da **AWS S3 /
 * Cloudflare R2 / Backblaze B2 / DigitalOcean Spaces** ile çalışır — sadece
 * endpoint + credentials değişir.</p>
 *
 * <p>Bucket yapısı:
 * <pre>
 *   warehouse-uploads/
 *     ├── transfers/{transferId}/{itemId}/{uuid}.webp
 *     ├── transfers/{transferId}/{itemId}/{uuid}_thumb.webp
 *     ├── products/{productId}/{uuid}.webp
 *     ├── products/{productId}/{uuid}_thumb.webp
 *     └── assets/{name}-{uuid}.{ext}   ← public-read (logo, banner)
 * </pre></p>
 *
 * <p>Aktivasyon: {@code STORAGE_PROVIDER=s3} env değişkeni. {@link LocalPhotoStorageService}
 * koşullu olarak devre dışı kalır; her ikisi aynı PhotoStorageService interface'ini
 * implement ettiği için çağıran kod değişmez.</p>
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

    /** Path-style addressing — MinIO + R2 + Oracle path-style ister; AWS S3 virtual-host ile de çalışır. */
    @Value("${storage.s3.path-style:true}")
    private boolean pathStyle;

    /**
     * Public-read ACL desteği. Oracle Object Storage S3-compat olsa da bu ACL'i
     * "NotImplemented" diye reddeder. Oracle için false yapın; bucket'ı manuel
     * public yapın veya Pre-Authenticated Request kullanın (ORACLE_CLOUD_SETUP.md).
     * MinIO/AWS/R2/B2 destekler → true (default).
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
        // Bucket var mı kontrol et — yoksa oluşturma try (MinIO init script bunu zaten yapar)
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
        // Site asset'ler public-read olduğu için ayrı prefix
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
        // S3'te dizin kavramı yok; konvensiyon: bucket içinde "assets/" prefix.
        // Backward compatibility için sentinel path döner; çağıranların bunu
        // doğrudan filesystem path'i gibi kullanmaması beklenir.
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
     * WebP-optimized image + thumbnail upload. LocalPhotoStorageService'in
     * davranışını taklit eder ama S3'e yazar.
     */
    private StoredPhoto storeOptimized(String prefix, String originalFileName,
                                        String contentType, InputStream inputStream) {
        try {
            byte[] original = inputStream.readAllBytes();
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(original));
            if (img == null) {
                // Image olarak parse edilemedi (WebP/SVG/PDF vb. — Java ImageIO WebP
                // okumaz). Olduğu gibi yükle; thumbnail için main dosyayı kullan
                // (DB'de thumbnail_path NOT NULL, ayrıca fallback davranış için
                // frontend her halükarda bir thumb URL'i bekler).
                String ext = inferExtension(originalFileName, contentType);
                String key = prefix + "/" + shortUuid() + ext;
                putObject(key, contentType, original, false);
                return new StoredPhoto(key, key, key, contentType, original.length, null, null);
                //                          ^^^ thumbnailPath = main key (aynı dosya)
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
        // ACL sadece destekleyen sağlayıcılarda set'lenir (Oracle Object Storage hariç).
        // Oracle için bucket'ı manuel public yapmak veya PAR kullanmak gerekir.
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

    // WebP encoding — javax.imageio kullanıcı sisteminde WebP writer'ı varsa
    // çalışır (TwelveMonkeys imageio-webp). Yoksa JPEG fallback.
    private byte[] encodeWebp(BufferedImage img, float quality) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // WebP writer varsa kullan
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
            // Fallback: JPEG
            ImageIO.write(img, "jpg", baos);
            return baos.toByteArray();
        }
    }

    private BufferedImage resizeMax(BufferedImage src, int maxDim) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;
        double scale = Math.min((double) maxDim / w, (double) maxDim / h);
        int nw = (int) (w * scale);
        int nh = (int) (h * scale);
        BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        var g = scaled.createGraphics();
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
     * Public URL üretici — caller'ların asset URL'lerini admin paneline gönderirken
     * kullanabilmesi için. {@code storage.s3.public-base-url} set'liyse onu kullanır
     * (Cloudflare R2 custom domain için), değilse default S3 URL.
     */
    public String publicUrl(String key) {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl.replaceAll("/+$", "") + "/" + key;
        }
        return endpoint.replaceAll("/+$", "") + "/" + bucket + "/" + key;
    }
}
