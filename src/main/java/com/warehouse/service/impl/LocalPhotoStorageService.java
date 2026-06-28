package com.warehouse.service.impl;

import com.warehouse.config.PhotoStorageProperties;
import com.warehouse.service.PhotoStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.UUID;

/**
 * Local (filesystem) storage implementation. This is the default provider.
 *
 * <p>Active when {@code storage.provider=local} (the default). To switch to S3/R2,
 * set {@code storage.provider=s3} and provide the {@code S3PhotoStorageService}
 * implementation (currently a skeleton, Phase 3 NICE-TO-HAVE).</p>
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "storage.provider", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class LocalPhotoStorageService implements PhotoStorageService {

    private final PhotoStorageProperties properties;

    /**
     * Resolves the base directory path, similar to how Excel imports work.
     * Priority:
     * 1. RAILWAY_VOLUME_MOUNT_PATH environment variable (if set, for persistent
     * storage)
     * 2. PHOTO_STORAGE_DIR environment variable (if set)
     * 3. Configured baseDir from properties (absolute or relative)
     *
     * This matches the pattern used in StockImportServiceImpl.storeFile().
     */
    private Path resolveBaseDir() {
        String baseDir = properties.getBaseDir();

        // 1) Railway persistent volume mount (highest priority in Railway env)
        String railwayVolumePath = System.getenv("RAILWAY_VOLUME_MOUNT_PATH");
        if (railwayVolumePath != null && !railwayVolumePath.trim().isEmpty()) {
            Path root = Paths.get(railwayVolumePath);

            // If the baseDir in properties is a relative path (e.g. "uploads/shipments"),
            // append only its last segment (e.g. "shipments") under the volume root.
            if (baseDir != null && !baseDir.trim().isEmpty()) {
                Path basePath = Paths.get(baseDir);
                if (!basePath.isAbsolute() && basePath.getNameCount() > 0) {
                    String lastSegment = basePath.getFileName().toString();
                    root = root.resolve(lastSegment);
                }
            }

            log.info("Using Railway volume mount path as photo base directory: {} (from RAILWAY_VOLUME_MOUNT_PATH={})",
                    root.toAbsolutePath(), railwayVolumePath);
            return root.toAbsolutePath();
        }

        // 2) Explicit PHOTO_STORAGE_DIR
        String customPhotoDir = System.getenv("PHOTO_STORAGE_DIR");
        if (customPhotoDir != null && !customPhotoDir.trim().isEmpty()) {
            Path customPath = Paths.get(customPhotoDir);
            log.info("Using custom photo storage directory from PHOTO_STORAGE_DIR: {}", customPath.toAbsolutePath());
            return customPath.toAbsolutePath();
        }

        // 3) baseDir from properties
        Path basePath = Paths.get(baseDir);

        // If it's already absolute (like /tmp/warehouse-uploads/shipments), use it
        // as-is
        if (basePath.isAbsolute()) {
            log.debug("Using absolute base directory: {}", basePath);
            return basePath;
        }

        // For relative paths (local development), resolve relative to current working
        // directory
        Path resolved = Paths.get(System.getProperty("user.dir", ".")).resolve(basePath).toAbsolutePath();
        log.info("Resolved relative base directory: {} -> {}", baseDir, resolved);
        return resolved;
    }

    @Override
    public StoredPhoto storeItemPhoto(Long transferId,
            Long itemId,
            String originalFileName,
            String contentType,
            InputStream inputStream) {
        try {
            byte[] originalBytes = inputStream.readAllBytes();
            if (originalBytes.length == 0) {
                throw new IllegalArgumentException("Empty image stream");
            }
            if (originalBytes.length > 10 * 1024 * 1024) {
                throw new IllegalArgumentException("Dosya boyutu 10MB sinirini asiyor");
            }

            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
            if (sourceImage == null) {
                throw new IllegalArgumentException("Unsupported image format");
            }

            BufferedImage optimized = resizeIfNeeded(sourceImage,
                    properties.getMaxWidth(),
                    properties.getMaxHeight());

            BufferedImage thumbnail = resizeIfNeeded(sourceImage, 320, 320);

            String extension = resolveExtension(originalFileName, contentType);
            String baseName = UUID.randomUUID().toString();

            LocalDate today = LocalDate.now();
            Path baseDir = resolveBaseDir()
                    .resolve(String.valueOf(today.getYear()))
                    .resolve(String.format("%02d", today.getMonthValue()))
                    .resolve(String.valueOf(transferId));

            try {
                Files.createDirectories(baseDir);
                log.debug("Created directory: {}", baseDir);
            } catch (IOException e) {
                log.error("Failed to create directory: {}", baseDir, e);
                throw new RuntimeException("Failed to create photo storage directory: " + baseDir, e);
            }

            String optimizedFileName = itemId + "_" + baseName + "_orig." + extension;
            String thumbFileName = itemId + "_" + baseName + "_thumb." + extension;

            Path optimizedPath = baseDir.resolve(optimizedFileName);
            Path thumbPath = baseDir.resolve(thumbFileName);

            log.debug("Writing optimized image to: {}", optimizedPath);
            writeCompressedImage(optimized, extension, optimizedPath, properties.getQuality());
            log.debug("Writing thumbnail to: {}", thumbPath);
            writeCompressedImage(thumbnail, extension, thumbPath, properties.getQuality());

            // Verify files were actually written
            if (!Files.exists(optimizedPath)) {
                log.error("Optimized image file was not created: {}", optimizedPath.toAbsolutePath());
                throw new RuntimeException("Failed to create optimized image file: " + optimizedPath.toAbsolutePath());
            }
            if (!Files.exists(thumbPath)) {
                log.error("Thumbnail file was not created: {}", thumbPath.toAbsolutePath());
                throw new RuntimeException("Failed to create thumbnail file: " + thumbPath.toAbsolutePath());
            }

            long sizeBytes = Files.size(optimizedPath);
            log.info("Successfully stored photo: optimized={} ({} bytes), thumbnail={} ({} bytes)",
                    optimizedPath.toAbsolutePath(), sizeBytes, thumbPath.toAbsolutePath(), Files.size(thumbPath));

            return new StoredPhoto(
                    optimizedFileName,
                    baseDir.toString().replace("\\", "/") + "/" + optimizedFileName,
                    baseDir.toString().replace("\\", "/") + "/" + thumbFileName,
                    resolveContentType(extension),
                    sizeBytes,
                    optimized.getWidth(),
                    optimized.getHeight());
        } catch (IOException e) {
            log.error("Failed to store item photo for transferId={}, itemId={}, baseDir={}",
                    transferId, itemId, properties.getBaseDir(), e);
            throw new RuntimeException("Failed to store item photo: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error storing item photo for transferId={}, itemId={}",
                    transferId, itemId, e);
            throw new RuntimeException("Failed to store item photo: " + e.getMessage(), e);
        }
    }

    @Override
    public StoredPhoto storeProductImage(Long productId, String originalFileName, String contentType,
            InputStream inputStream) {
        try {
            byte[] originalBytes = inputStream.readAllBytes();
            if (originalBytes.length == 0) {
                throw new IllegalArgumentException("Empty image stream");
            }
            if (originalBytes.length > 10 * 1024 * 1024) {
                throw new IllegalArgumentException("Dosya boyutu 10MB sinirini asiyor");
            }

            String extension = resolveExtension(originalFileName, contentType);
            String baseName = UUID.randomUUID().toString();

            LocalDate today = LocalDate.now();
            Path resolvedBase = resolveBaseDir();
            Path productsBase = resolvedBase.getParent().resolve("products");
            Path baseDir = productsBase
                    .resolve(String.valueOf(today.getYear()))
                    .resolve(String.format("%02d", today.getMonthValue()))
                    .resolve(String.valueOf(productId));

            Files.createDirectories(baseDir);

            String optimizedFileName = baseName + "_orig." + extension;
            String thumbFileName = baseName + "_thumb." + extension;

            Path optimizedPath = baseDir.resolve(optimizedFileName);
            Path thumbPath = baseDir.resolve(thumbFileName);

            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
            // We must be able to BOTH decode and re-encode to optimize. WebP is a
            // decode-only case here (the TwelveMonkeys reader plugin has no writer),
            // so re-encoding would silently produce an empty/broken file. In that
            // case — or when ImageIO can't read at all (AVIF, CMYK JPEG, …) — keep
            // the original bytes untouched.
            if (sourceImage != null && hasImageWriter(extension)) {
                BufferedImage optimized = resizeIfNeeded(sourceImage,
                        properties.getMaxWidth(), properties.getMaxHeight());
                BufferedImage thumbnail = resizeIfNeeded(sourceImage, 320, 320);
                writeCompressedImage(optimized, extension, optimizedPath, properties.getQuality());
                writeCompressedImage(thumbnail, extension, thumbPath, properties.getQuality());
            } else {
                log.info("Saving product image raw (decodable={}, encodable={}): {}",
                        sourceImage != null, hasImageWriter(extension), originalFileName);
                Files.write(optimizedPath, originalBytes);
                Files.write(thumbPath, originalBytes);
            }

            long sizeBytes = Files.size(optimizedPath);
            Integer width = sourceImage != null ? sourceImage.getWidth() : null;
            Integer height = sourceImage != null ? sourceImage.getHeight() : null;

            return new StoredPhoto(
                    optimizedFileName,
                    baseDir.toString().replace("\\", "/") + "/" + optimizedFileName,
                    baseDir.toString().replace("\\", "/") + "/" + thumbFileName,
                    resolveContentType(extension),
                    sizeBytes,
                    width,
                    height);
        } catch (IOException e) {
            log.error("Failed to store product image", e);
            throw new RuntimeException("Failed to store product image", e);
        }
    }

    @Override
    public StoredPhoto storeSiteAsset(String assetName, String originalFileName, String contentType,
            InputStream inputStream) {
        try {
            byte[] originalBytes = inputStream.readAllBytes();
            if (originalBytes.length == 0) {
                throw new IllegalArgumentException("Empty image stream");
            }
            if (originalBytes.length > 10 * 1024 * 1024) {
                throw new IllegalArgumentException("Dosya boyutu 10MB sinirini asiyor");
            }

            // Validate content type
            if (contentType == null || (!contentType.startsWith("image/") && !contentType.equals("application/octet-stream"))) {
                throw new IllegalArgumentException("Geçersiz dosya tipi: " + contentType);
            }

            String extension = resolveSiteAssetExtension(originalFileName, contentType);
            String baseName = UUID.randomUUID().toString();

            Path resolvedBase = resolveBaseDir();
            Path siteBase = resolvedBase.getParent().resolve("site");
            Files.createDirectories(siteBase);

            String savedFileName = assetName + "_" + baseName + "." + extension;
            Path savedPath = siteBase.resolve(savedFileName);

            // Try to optimize via ImageIO (works for JPEG/PNG)
            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
            if (sourceImage != null) {
                BufferedImage optimized = resizeIfNeeded(sourceImage,
                        properties.getMaxWidth(), properties.getMaxHeight());
                writeCompressedImage(optimized, extension, savedPath, properties.getQuality());
            } else {
                // ImageIO can't read this format (SVG, ICO, some WebP, CMYK JPEG)
                // Save raw bytes directly — still valid for browser rendering
                log.info("ImageIO cannot process format, saving raw file: {}", originalFileName);
                Files.write(savedPath, originalBytes);
            }

            long sizeBytes = Files.size(savedPath);

            return new StoredPhoto(
                    savedFileName,
                    siteBase.toString().replace("\\", "/") + "/" + savedFileName,
                    null,
                    contentType,
                    sizeBytes,
                    null,
                    null);
        } catch (IOException e) {
            log.error("Failed to store site asset: {}", assetName, e);
            throw new RuntimeException("Failed to store site asset", e);
        }
    }

    private String resolveSiteAssetExtension(String originalFileName, String contentType) {
        if (originalFileName != null && originalFileName.contains(".")) {
            String ext = originalFileName.substring(originalFileName.lastIndexOf('.') + 1).toLowerCase();
            if (ext.matches("jpe?g|png|webp|svg|ico|gif")) {
                return ext;
            }
        }
        if (contentType != null) {
            if (contentType.contains("svg")) return "svg";
            if (contentType.contains("icon") || contentType.contains("ico")) return "ico";
            if (contentType.contains("png")) return "png";
            if (contentType.contains("webp")) return "webp";
            if (contentType.contains("gif")) return "gif";
            if (contentType.contains("jpeg") || contentType.contains("jpg")) return "jpg";
        }
        return "png";
    }

    @Override
    public Path getSiteAssetDir() {
        return resolveBaseDir().getParent().resolve("site");
    }

    @Override
    public void deletePhotoFiles(String relativePath, String thumbnailPath) {
        if (relativePath != null) {
            deletePath(Paths.get(relativePath));
        }
        if (thumbnailPath != null) {
            deletePath(Paths.get(thumbnailPath));
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

    // ─── Generic document storage (PDF, XLSX, RAG docs) ───────────

    @Override
    public String storeDocument(String prefix, String originalFileName,
                                 String contentType, java.io.InputStream inputStream) {
        try {
            String ext = "";
            if (originalFileName != null) {
                int dot = originalFileName.lastIndexOf('.');
                if (dot > 0) ext = originalFileName.substring(dot);
            }
            String uuid = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String relativeKey = prefix + "/" + uuid + ext;

            // Local fs: create the prefix directory under baseDir
            Path target = resolveBaseDir().resolve(relativeKey).normalize();
            // Path traversal protection
            if (!target.startsWith(resolveBaseDir().normalize())) {
                throw new SecurityException("Invalid document path: " + relativeKey);
            }
            java.nio.file.Files.createDirectories(target.getParent());
            try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(target)) {
                inputStream.transferTo(out);
            }
            log.debug("Local document stored: {}", target);
            return relativeKey;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Local document yazılamadı: " + e.getMessage(), e);
        }
    }

    @Override
    public java.io.InputStream openDocumentStream(String key) {
        try {
            if (key == null || key.isBlank()) {
                throw new RuntimeException("Document key is null or empty");
            }
            if (key.contains("..") || key.contains("~")) {
                throw new SecurityException("Invalid document key: " + key);
            }
            Path path = resolveBaseDir().resolve(key).normalize();
            if (!path.startsWith(resolveBaseDir().normalize())) {
                throw new SecurityException("Path traversal: " + key);
            }
            if (!java.nio.file.Files.exists(path)) {
                throw new RuntimeException("Document not found: " + key);
            }
            return java.nio.file.Files.newInputStream(path);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Document açılamadı: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteDocument(String key) {
        if (key == null || key.isBlank()) return;
        try {
            Path path = resolveBaseDir().resolve(key).normalize();
            if (!path.startsWith(resolveBaseDir().normalize())) {
                log.warn("Skipping document delete (path traversal): {}", key);
                return;
            }
            java.nio.file.Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("Local document delete fail: {} → {}", key, e.getMessage());
        }
    }

    private InputStream openStream(String relativePath) {
        try {
            if (relativePath == null || relativePath.trim().isEmpty()) {
                log.error("Cannot open photo stream: path is null or empty");
                throw new RuntimeException("Photo path is null or empty");
            }

            // Security: prevent path traversal attacks
            if (relativePath.contains("..") || relativePath.contains("~")) {
                log.error("Path traversal attempt detected: {}", relativePath);
                throw new SecurityException("Invalid file path");
            }

            Path path = Paths.get(relativePath).normalize();

            // If path is not absolute, try to resolve it relative to base directory
            if (!path.isAbsolute()) {
                log.debug("Path is relative, resolving against base directory: {}", relativePath);
                Path baseDir = resolveBaseDir();
                // Extract the relative part (e.g., "2025/12/25/64_xxx_orig.jpg" from
                // "shipments/2025/12/25/64_xxx_orig.jpg")
                String pathStr = relativePath.replace("\\", "/");
                if (pathStr.contains("/")) {
                    // Find the year part (e.g., "2025") to determine the correct subdirectory
                    String[] parts = pathStr.split("/");
                    if (parts.length >= 3) {
                        // Assume format: shipments/2025/12/25/filename or 2025/12/25/filename
                        int yearIndex = -1;
                        for (int i = 0; i < parts.length; i++) {
                            if (parts[i].matches("\\d{4}")) {
                                yearIndex = i;
                                break;
                            }
                        }
                        if (yearIndex >= 0) {
                            // Reconstruct path from year onwards
                            StringBuilder relativePart = new StringBuilder();
                            for (int i = yearIndex; i < parts.length; i++) {
                                if (relativePart.length() > 0)
                                    relativePart.append("/");
                                relativePart.append(parts[i]);
                            }
                            path = baseDir.resolve(relativePart.toString());
                            log.debug("Resolved relative path to: {}", path.toAbsolutePath());
                        } else {
                            // Fallback: resolve directly
                            path = baseDir.resolve(relativePath);
                        }
                    } else {
                        path = baseDir.resolve(relativePath);
                    }
                } else {
                    path = baseDir.resolve(relativePath);
                }
            }

            // Log path resolution for debugging
            log.debug("Opening photo stream from path: {} (absolute: {}, exists: {})",
                    path.toAbsolutePath(), path.isAbsolute(), Files.exists(path));

            // Check if file exists
            if (!Files.exists(path)) {
                // Log directory contents for debugging
                Path parentDir = path.getParent();
                if (parentDir != null && Files.exists(parentDir)) {
                    try {
                        log.warn("Photo file does not exist: {} - Directory exists, listing contents:",
                                path.toAbsolutePath());
                        Files.list(parentDir).forEach(p -> {
                            try {
                                log.warn("  - {} (exists: {}, isFile: {}, size: {} bytes)",
                                        p.getFileName(), Files.exists(p), Files.isRegularFile(p),
                                        Files.isRegularFile(p) ? Files.size(p) : 0);
                            } catch (IOException e) {
                                log.warn("  - {} (error getting info: {})", p.getFileName(), e.getMessage());
                            }
                        });
                    } catch (IOException e) {
                        log.warn("Could not list directory contents: {}", e.getMessage());
                    }
                } else {
                    log.error("Photo file does not exist and parent directory also does not exist: {} (parent: {})",
                            path.toAbsolutePath(), parentDir != null ? parentDir.toAbsolutePath() : "null");
                }

                log.error("Photo file does not exist: {} (stored path: {}, absolute path: {})",
                        relativePath, relativePath, path.toAbsolutePath());
                throw new RuntimeException("Photo file does not exist: " + path.toAbsolutePath());
            }

            // Check if it's a file (not a directory)
            if (!Files.isRegularFile(path)) {
                log.error("Photo path is not a regular file: {} (absolute path: {})", relativePath,
                        path.toAbsolutePath());
                throw new RuntimeException("Photo path is not a regular file: " + path.toAbsolutePath());
            }

            return Files.newInputStream(path);
        } catch (IOException e) {
            log.error("Failed to open photo stream from path: {} - Error: {}", relativePath, e.getMessage(), e);
            throw new RuntimeException("Failed to open photo: " + relativePath + " - " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error opening photo stream from path: {} - Error: {}", relativePath, e.getMessage(),
                    e);
            throw new RuntimeException("Failed to open photo: " + relativePath + " - " + e.getMessage(), e);
        }
    }

    private void deletePath(Path path) {
        try {
            FileSystemUtils.deleteRecursively(path);
        } catch (IOException e) {
            log.warn("Failed to delete photo file: {}", path, e);
        }
    }

    private String resolveExtension(String originalFileName, String contentType) {
        if (originalFileName != null && originalFileName.contains(".")) {
            String ext = originalFileName.substring(originalFileName.lastIndexOf('.') + 1).toLowerCase();
            if (ext.matches("jpe?g|png|webp")) {
                return ext;
            }
        }
        if (contentType != null) {
            if (contentType.contains("jpeg"))
                return "jpg";
            if (contentType.contains("png"))
                return "png";
            if (contentType.contains("webp"))
                return "webp";
        }
        return "jpg";
    }

    private String resolveContentType(String extension) {
        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    private BufferedImage resizeIfNeeded(BufferedImage source, int maxWidth, int maxHeight) {
        int width = source.getWidth();
        int height = source.getHeight();

        double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
        if (scale >= 1.0) {
            return source;
        }

        int newWidth = (int) Math.round(width * scale);
        int newHeight = (int) Math.round(height * scale);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(source, 0, 0, newWidth, newHeight, null);
        } finally {
            g2d.dispose();
        }
        return resized;
    }

    /** True when ImageIO has an encoder for this file extension (e.g. jpg/png yes, webp no). */
    private static boolean hasImageWriter(String extension) {
        String format = "jpg".equalsIgnoreCase(extension) ? "jpeg" : extension.toLowerCase();
        return ImageIO.getImageWritersByFormatName(format).hasNext();
    }

    private void writeCompressedImage(BufferedImage image, String extension, Path target, float quality)
            throws IOException {
        try {
            Path parentDir = target.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
                log.debug("Created parent directory: {}", parentDir);
            }
        } catch (IOException e) {
            log.error("Failed to create parent directory for: {}", target, e);
            throw new IOException("Failed to create directory for photo: " + target.getParent(), e);
        }

        String format = extension.equalsIgnoreCase("jpg") ? "jpeg" : extension.toLowerCase();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            try {
                ImageIO.write(image, format, target.toFile());
                log.debug("Wrote image using ImageIO.write to: {}", target);
                return;
            } catch (IOException e) {
                log.error("Failed to write image to: {}", target, e);
                throw new IOException("Failed to write image file: " + target, e);
            }
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }

            writer.write(null, new IIOImage(image, null, null), param);
            ios.flush();
            byte[] imageBytes = baos.toByteArray();
            log.debug("Writing {} bytes to: {}", imageBytes.length, target);
            Files.write(target, imageBytes);
            log.debug("Successfully wrote image to: {}", target);
        } catch (IOException e) {
            log.error("Failed to write compressed image to: {}", target, e);
            throw new IOException("Failed to write compressed image file: " + target, e);
        } finally {
            writer.dispose();
        }
    }
}
