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

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalPhotoStorageService implements PhotoStorageService {

    private final PhotoStorageProperties properties;

    /**
     * Resolves the base directory path, converting relative paths to absolute paths.
     * This is important for Railway and other cloud platforms where relative paths may not work.
     */
    private Path resolveBaseDir() {
        String baseDir = properties.getBaseDir();
        Path basePath = Paths.get(baseDir);
        
        // If it's already absolute, use it as-is
        if (basePath.isAbsolute()) {
            return basePath;
        }
        
        // Otherwise, resolve relative to the current working directory or temp directory
        // For Railway/cloud platforms, use system temp directory as fallback
        try {
            Path resolved = Paths.get(System.getProperty("user.dir", System.getProperty("java.io.tmpdir"))).resolve(basePath);
            log.info("Resolved base directory: {} -> {}", baseDir, resolved.toAbsolutePath());
            return resolved.toAbsolutePath();
        } catch (Exception e) {
            log.warn("Failed to resolve base directory, using temp directory: {}", e.getMessage());
            return Paths.get(System.getProperty("java.io.tmpdir", "/tmp")).resolve(basePath).toAbsolutePath();
        }
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
            Path baseDir = resolveBaseDir().resolve(
                    String.valueOf(today.getYear()),
                    String.format("%02d", today.getMonthValue()),
                    String.valueOf(transferId));

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

            long sizeBytes = Files.size(optimizedPath);

            return new StoredPhoto(
                    optimizedFileName,
                    baseDir.toString().replace("\\", "/") + "/" + optimizedFileName,
                    baseDir.toString().replace("\\", "/") + "/" + thumbFileName,
                    resolveContentType(extension),
                    sizeBytes,
                    optimized.getWidth(),
                    optimized.getHeight()
            );
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
    public StoredPhoto storeProductImage(Long productId,
                                         String originalFileName,
                                         String contentType,
                                         InputStream inputStream) {
        try {
            byte[] originalBytes = inputStream.readAllBytes();
            if (originalBytes.length == 0) {
                throw new IllegalArgumentException("Empty image stream");
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
            // Use a separate root folder for product images to keep things organized
            Path resolvedBase = resolveBaseDir();
            Path productsBase = resolvedBase.getParent().resolve("products");
            Path baseDir = productsBase.resolve(
                    String.valueOf(today.getYear()),
                    String.format("%02d", today.getMonthValue()),
                    String.valueOf(productId)
            );

            Files.createDirectories(baseDir);

            String optimizedFileName = baseName + "_orig." + extension;
            String thumbFileName = baseName + "_thumb." + extension;

            Path optimizedPath = baseDir.resolve(optimizedFileName);
            Path thumbPath = baseDir.resolve(thumbFileName);

            writeCompressedImage(optimized, extension, optimizedPath, properties.getQuality());
            writeCompressedImage(thumbnail, extension, thumbPath, properties.getQuality());

            long sizeBytes = Files.size(optimizedPath);

            return new StoredPhoto(
                    optimizedFileName,
                    baseDir.toString().replace("\\", "/") + "/" + optimizedFileName,
                    baseDir.toString().replace("\\", "/") + "/" + thumbFileName,
                    resolveContentType(extension),
                    sizeBytes,
                    optimized.getWidth(),
                    optimized.getHeight()
            );
        } catch (IOException e) {
            log.error("Failed to store product image", e);
            throw new RuntimeException("Failed to store product image", e);
        }
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

    private InputStream openStream(String relativePath) {
        try {
            Path path = Paths.get(relativePath);
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open photo: " + relativePath, e);
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
            if (contentType.contains("jpeg")) return "jpg";
            if (contentType.contains("png")) return "png";
            if (contentType.contains("webp")) return "webp";
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

    private void writeCompressedImage(BufferedImage image, String extension, Path target, float quality) throws IOException {
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


