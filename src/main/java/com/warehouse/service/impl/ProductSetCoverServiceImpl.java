package com.warehouse.service.impl;

import com.warehouse.assistant.core.config.AssistantRuntimeConfig;
import com.warehouse.assistant.core.image.OpenAiImageEditClient;
import com.warehouse.entity.BundleItem;
import com.warehouse.entity.Product;
import com.warehouse.entity.ProductImage;
import com.warehouse.entity.ProductType;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.BundleItemRepository;
import com.warehouse.repository.ProductImageRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.service.PhotoStorageService;
import com.warehouse.service.ProductImageService;
import com.warehouse.service.ProductSetCoverService;
import com.warehouse.util.ProductImageUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProductSetCoverServiceImpl implements ProductSetCoverService {

    private static final Logger logger = LoggerFactory.getLogger(ProductSetCoverServiceImpl.class);

    private static final String ROLE_COVER_INPUT = ProductImageUtil.ROLE_COVER_INPUT;
    private static final String ROLE_AI_COVER = ProductImageUtil.ROLE_AI_COVER;

    /**
     * Default combine prompt. Overridable at runtime via the admin setting
     * {@code assistant.image.prompt}. English on purpose — image models follow
     * English instructions far more reliably. Structured as hard rules because
     * the failure mode is the model "redrawing" products and inventing text;
     * pair with {@code gpt-image-1} + {@code input_fidelity=high} for the best
     * logo/text preservation.
     */
    static final String DEFAULT_PROMPT =
            "Combine the products from the attached reference photos into ONE professional e-commerce "
            + "catalog photograph.\n\n"
            + "ABSOLUTE FIDELITY RULES — every product must remain EXACTLY as photographed:\n"
            + "- Reproduce each product exactly once, faithful to its reference photo: identical shape, "
            + "proportions, colors, materials, surface finish, buttons, knobs, handles, displays and "
            + "screen content.\n"
            + "- All printed text, logos, brand names, labels and markings must be copied letter-for-letter "
            + "exactly as they appear in the reference photos. Never invent, replace, translate, redraw or "
            + "\"improve\" any text or logo. If text is too small to read in the reference, keep it as the "
            + "same small indistinct marks — do not sharpen it into new letters or words.\n"
            + "- Do not redesign, recolor, restyle, clean up or modify any product in any way.\n\n"
            + "ADDITIONS ARE FORBIDDEN: no new text, captions, badges, stickers, energy labels, price tags, "
            + "watermarks, logos, props, decorations, plants, food, people, hands, or reflections of objects "
            + "that are not in the reference photos.\n\n"
            + "COMPOSITION: arrange the products as a balanced corner collage that fills a SQUARE frame — pin "
            + "the products to the corners/quadrants of the frame instead of lining them up in one row. For "
            + "three products use a classic three-corner layout: one in the upper-left, one in the upper-right, "
            + "and one centered along the bottom. For two products place them in opposite corners; for four use "
            + "the four corners. Distribute them evenly so no single quadrant is left large and empty. Every "
            + "product must stay at a plausible relative real-world size, fully visible, none cropped, none "
            + "overlapping another, with comfortable spacing between them.\n\n"
            + "SCENE: seamless neutral white-to-light-gray professional studio background, soft even diffused "
            + "lighting, subtle natural contact shadows under each product.\n\n"
            + "STYLE: photorealistic, sharp focus, high detail. The result must look like the original product "
            + "photos professionally arranged together in one studio shot — NOT like newly drawn or "
            + "reimagined products.";

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final BundleItemRepository bundleItemRepository;
    private final ProductImageService productImageService;
    private final PhotoStorageService photoStorageService;
    private final OpenAiImageEditClient imageEditClient;
    private final LocalSetCoverComposer localCoverComposer;
    private final AssistantRuntimeConfig runtimeConfig;
    private final TransactionTemplate transactionTemplate;

    // ─────────────────────────── cover inputs ───────────────────────────

    @Override
    @Transactional
    public ProductImage setCoverInput(Long setId, Long memberProductId,
                                      String originalFileName, String contentType, InputStream inputStream) {
        Product set = loadBundleOrThrow(setId);
        requireExistingProduct(memberProductId);
        byte[] bytes = readAll(inputStream);
        return storeCoverInput(set, memberProductId, originalFileName, contentType, bytes);
    }

    @Override
    @Transactional
    public ProductImage setCoverInputFromImage(Long setId, Long memberProductId, Long imageId) {
        Product set = loadBundleOrThrow(setId);
        requireExistingProduct(memberProductId);
        ProductImage source = productImageService.getImageOrThrow(imageId);
        if (!Objects.equals(source.getProduct().getId(), memberProductId)) {
            throw new WarehouseManagementException(ErrorCode.AI_COVER_IMAGE_NOT_OWNED);
        }
        // Copy bytes so the input survives even if the source product image is deleted.
        byte[] bytes = readPhoto(source.getRelativePath());
        // A transparent product photo stored by an older build can have a broken main
        // file (alpha image the JPEG encoder couldn't write) while its thumbnail — which
        // is flattened to RGB during resize — is fine. So if the main can't be decoded,
        // fall back to the thumbnail so the pick still works.
        if (!isLocallyDecodable(bytes) && source.getThumbnailPath() != null
                && !source.getThumbnailPath().equals(source.getRelativePath())) {
            byte[] thumb = readPhoto(source.getThumbnailPath());
            if (isLocallyDecodable(thumb)) {
                bytes = thumb;
            }
        }
        return storeCoverInput(set, memberProductId, source.getFileName(), source.getContentType(), bytes);
    }

    @Override
    @Transactional
    public void deleteCoverInput(Long setId, Long memberProductId) {
        Product set = loadBundleOrThrow(setId);
        imageRepository.findByProductAndAiRoleAndMemberProductId(set, ROLE_COVER_INPUT, memberProductId)
                .forEach(img -> productImageService.deleteImage(img.getId()));
    }

    @Override
    @Transactional
    public FillResult fillCoverInputsFromPrimaries(Long setId) {
        Product set = loadBundleOrThrow(setId);
        List<BundleItem> members = bundleItemRepository.findByBundleIdOrderBySortOrderAsc(setId);
        int filled = 0;
        List<Long> missing = new ArrayList<>();
        for (BundleItem member : members) {
            Long memberId = member.getProduct().getId();
            ProductImage best = ProductImageUtil
                    .displayCover(imageRepository.findByProductOrderBySortOrderAscIdAsc(member.getProduct()))
                    .orElse(null);
            if (best == null) {
                missing.add(memberId);
                continue;
            }
            byte[] bytes;
            try (InputStream in = photoStorageService.openPhotoStream(best.getRelativePath())) {
                bytes = in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            try {
                storeCoverInput(set, memberId, best.getFileName(), best.getContentType(), bytes);
                filled++;
            } catch (WarehouseManagementException e) {
                if (e.getErrorCode() == ErrorCode.AI_COVER_UNSUPPORTED_FORMAT) {
                    // Primary photo is in a format OpenAI can't use (e.g. AVIF) —
                    // report instead of failing the whole bulk fill.
                    missing.add(memberId);
                } else {
                    throw e;
                }
            }
        }
        return new FillResult(filled, missing);
    }

    // ─────────────────────────── generation ───────────────────────────

    /** Read-phase output: everything needed for the HTTP call, detached from the session. */
    private record Prepared(List<OpenAiImageEditClient.ImageInput> inputs, String prompt) {}

    @Override
    public ProductImage generateCover(Long setId) {
        // The active engine is configured (env/admin), not chosen by the caller, so
        // the single "Generate cover" button transparently uses DIGITAL or AI.
        if (runtimeConfig.isAiCoverMode()) {
            logger.info("Generating set cover for {} via AI (OpenAI)", setId);
            return generateCoverViaAi(setId);
        }
        logger.info("Generating set cover for {} via DIGITAL (local compositor)", setId);
        return generateCoverLocally(setId);
    }

    private ProductImage generateCoverViaAi(Long setId) {
        // Phase 1 (tx): validate and read all input bytes.
        Prepared prepared = transactionTemplate.execute(status -> prepareGeneration(setId));

        // Phase 2 (NO tx): the OpenAI call can take up to ~2 minutes — never hold
        // a database transaction/connection across it.
        byte[] png = imageEditClient.generateCover(prepared.inputs(), prepared.prompt());

        // Phase 3 (tx): replace the previous AI cover and save the new primary.
        return transactionTemplate.execute(status -> persistGeneratedCover(setId, png));
    }

    @Override
    public ProductImage generateCoverLocally(Long setId) {
        // Phase 1 (tx): collect the per-member input bytes. No OpenAI key needed.
        List<OpenAiImageEditClient.ImageInput> inputs =
                transactionTemplate.execute(status -> collectInputs(setId));

        // Phase 2 (NO tx, but fast): pure-Java compositing — no network call.
        byte[] png;
        try {
            png = localCoverComposer.compose(
                    inputs.stream().map(OpenAiImageEditClient.ImageInput::bytes).toList());
        } catch (IllegalStateException e) {
            // No input could be decoded (e.g. an exotic format ImageIO lacks a reader for).
            throw new WarehouseManagementException(ErrorCode.AI_COVER_UNSUPPORTED_FORMAT,
                    "Seçilen fotoğraflar okunamadı; lütfen JPEG/PNG/WebP bir fotoğraf seçin.");
        }

        // Phase 3 (tx): replace the previous AI cover and save the new primary.
        return transactionTemplate.execute(status -> persistGeneratedCover(setId, png));
    }

    private Prepared prepareGeneration(Long setId) {
        if (!imageEditClient.isConfigured()) {
            throw new WarehouseManagementException(ErrorCode.AI_COVER_API_KEY_MISSING);
        }
        List<OpenAiImageEditClient.ImageInput> payload = collectInputs(setId);
        String customPrompt = runtimeConfig.getImagePrompt();
        String prompt = (customPrompt != null && !customPrompt.isBlank()) ? customPrompt : DEFAULT_PROMPT;
        return new Prepared(payload, prompt);
    }

    /**
     * Loads every bundle member's COVER_INPUT photo, normalized to a format usable
     * by both the OpenAI edit API and Java's ImageIO, in member order. Throws if
     * the set has no members or any member is missing its input photo.
     */
    private List<OpenAiImageEditClient.ImageInput> collectInputs(Long setId) {
        Product set = loadBundleOrThrow(setId);
        List<BundleItem> members = bundleItemRepository.findByBundleIdOrderBySortOrderAsc(setId);
        if (members.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.AI_COVER_INPUT_MISSING);
        }

        List<ProductImage> inputs = imageRepository.findByProductAndAiRole(set, ROLE_COVER_INPUT);
        List<String> missingNames = new ArrayList<>();
        List<OpenAiImageEditClient.ImageInput> payload = new ArrayList<>();
        for (BundleItem member : members) {
            Long memberId = member.getProduct().getId();
            ProductImage input = inputs.stream()
                    .filter(img -> Objects.equals(img.getMemberProductId(), memberId))
                    .findFirst()
                    .orElse(null);
            if (input == null) {
                missingNames.add(member.getProduct().getName());
                continue;
            }
            byte[] bytes;
            try (InputStream in = photoStorageService.openPhotoStream(input.getRelativePath())) {
                bytes = in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            // Defensive re-check for inputs stored before format normalization existed.
            try {
                AiSafeImage safe = normalizeForAi(bytes, input.getFileName());
                // A WebP can pass the format sniff yet still be undecodable by ImageIO
                // (some crawled WebPs). Such an input would be SILENTLY dropped by the
                // local compositor → a collage missing that product, with no error.
                // Reject it so the catch below names the member and the UI re-encodes
                // it in the browser and retries.
                if (!isLocallyDecodable(safe.bytes())) {
                    throw new WarehouseManagementException(ErrorCode.AI_COVER_UNSUPPORTED_FORMAT);
                }
                payload.add(new OpenAiImageEditClient.ImageInput(
                        safe.bytes(), safe.contentType(), safe.fileName()));
            } catch (WarehouseManagementException e) {
                if (e.getErrorCode() == ErrorCode.AI_COVER_UNSUPPORTED_FORMAT) {
                    throw new WarehouseManagementException(ErrorCode.AI_COVER_UNSUPPORTED_FORMAT,
                            ErrorCode.AI_COVER_UNSUPPORTED_FORMAT.getMessage()
                                    + " (Üye: " + member.getProduct().getName() + ")");
                }
                throw e;
            }
        }
        if (!missingNames.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.AI_COVER_INPUT_MISSING,
                    ErrorCode.AI_COVER_INPUT_MISSING.getMessage()
                            + " Eksik: " + String.join(", ", missingNames));
        }
        return payload;
    }

    private ProductImage persistGeneratedCover(Long setId, byte[] png) {
        Product set = loadBundleOrThrow(setId);
        // Drop the previous AI cover (rows + files) before saving the new one.
        imageRepository.findByProductAndAiRole(set, ROLE_AI_COVER)
                .forEach(img -> productImageService.deleteImage(img.getId()));

        ProductImage saved = productImageService.addImageToProduct(
                setId,
                "ai-cover-" + setId + ".png",
                "image/png",
                new ByteArrayInputStream(png),
                true,
                null);
        saved.setAiRole(ROLE_AI_COVER);
        ProductImage result = imageRepository.save(saved);
        logger.info("AI set cover saved for set {} as image {} (primary)", setId, result.getId());
        return result;
    }

    // ─────────────────────────── helpers ───────────────────────────

    private Product loadBundleOrThrow(Long setId) {
        Product set = productRepository.findById(setId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND));
        if (set.getProductType() != ProductType.BUNDLE) {
            throw new WarehouseManagementException(ErrorCode.AI_COVER_NOT_A_BUNDLE);
        }
        return set;
    }

    /**
     * Cover inputs are keyed by member product id but are NOT required to be a
     * <em>currently persisted</em> bundle member: while editing a set the admin
     * may add a member and pick/upload its cover photo before re-saving the set,
     * so the bundle_item row may not exist yet. A stored input for a non-member is
     * harmless — generation only consumes inputs whose memberProductId matches an
     * actual saved member ({@code collectInputs}) and ignores the rest. So we only
     * guard against a genuinely non-existent product id here.
     */
    private void requireExistingProduct(Long memberProductId) {
        if (memberProductId == null || !productRepository.existsById(memberProductId)) {
            throw new WarehouseManagementException(ErrorCode.AI_COVER_NOT_A_MEMBER);
        }
    }

    /**
     * Stores the bytes as the member's COVER_INPUT image, replacing any previous
     * one. Built directly (not via {@code addImageToProduct}) so the gallery's
     * primary/ordering logic is never touched.
     */
    private ProductImage storeCoverInput(Product set, Long memberProductId,
                                         String fileName, String contentType, byte[] bytes) {
        // Reject/convert formats the OpenAI Images API can't ingest (only JPEG, PNG
        // and WebP are accepted) right at selection time, so the admin gets a clear
        // error now instead of a failed generation later. Crawled images are often
        // AVIF stored raw under a .jpg name, so sniff the actual bytes.
        AiSafeImage safe = normalizeForAi(bytes, fileName);
        // Cover inputs are an internal pipeline image. Transcode WebP to PNG up front:
        // ImageIO can READ WebP (reader plugin) but has no WebP WRITER, so leaving it
        // as WebP makes the storage optimizer fall back to a raw copy and risks broken
        // re-encodes downstream. PNG always round-trips (read AND write), guaranteeing
        // the tile preview renders and generation can re-read it.
        safe = transcodeWebpToPng(safe);
        // If, after transcoding, the bytes still can't be decoded server-side (some
        // crawled WebPs decode in browsers but not ImageIO), reject NOW — before we
        // delete the existing input — so the UI converts it in the browser at pick
        // time instead of storing an input that silently breaks at generation.
        if (!isLocallyDecodable(safe.bytes())) {
            throw new WarehouseManagementException(ErrorCode.AI_COVER_UNSUPPORTED_FORMAT);
        }

        imageRepository.findByProductAndAiRoleAndMemberProductId(set, ROLE_COVER_INPUT, memberProductId)
                .forEach(old -> productImageService.deleteImage(old.getId()));

        PhotoStorageService.StoredPhoto stored = photoStorageService.storeProductImage(
                set.getId(), safe.fileName(), safe.contentType(), new ByteArrayInputStream(safe.bytes()));

        ProductImage image = new ProductImage();
        image.setProduct(set);
        image.setFileName(stored.fileName());
        image.setRelativePath(stored.relativePath());
        image.setThumbnailPath(stored.thumbnailPath());
        image.setContentType(stored.contentType());
        image.setSizeBytes(stored.sizeBytes());
        image.setWidth(stored.width());
        image.setHeight(stored.height());
        image.setSortOrder(0);
        image.setPrimary(false);
        image.setAiRole(ROLE_COVER_INPUT);
        image.setMemberProductId(memberProductId);
        ProductImage saved = imageRepository.save(image);
        logger.info("AI cover input stored for set {} member {} as image {}",
                set.getId(), memberProductId, saved.getId());
        return saved;
    }

    private byte[] readAll(InputStream in) {
        try {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads a stored photo's bytes by its storage-relative path. */
    private byte[] readPhoto(String relativePath) {
        try (InputStream in = photoStorageService.openPhotoStream(relativePath)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ───────────── OpenAI-safe image normalization ─────────────

    /** Bytes guaranteed to be in a format the OpenAI Images API accepts. */
    private record AiSafeImage(byte[] bytes, String contentType, String fileName) {}

    /**
     * True when ImageIO can actually turn these bytes into a raster. The local
     * compositor relies on this; a format that only passes a magic-byte sniff
     * (e.g. an odd crawled WebP) but fails to decode would otherwise be dropped
     * from the collage without a trace.
     */
    private static boolean isLocallyDecodable(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Converts WebP bytes to PNG (PNG can be both read and written by ImageIO, WebP
     * only read). Non-WebP input is returned untouched; an undecodable WebP also
     * passes through unchanged (the storage layer will then keep it raw).
     */
    private AiSafeImage transcodeWebpToPng(AiSafeImage img) {
        if (!"webp".equals(sniffImageFormat(img.bytes()))) {
            return img;
        }
        String pngName = stripExtension(img.fileName()) + ".png";
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(img.bytes()));
            if (decoded != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(decoded, "png", out);
                return new AiSafeImage(out.toByteArray(), "image/png", pngName);
            }
        } catch (Exception e) {
            logger.warn("WebP→PNG transcode via ImageIO failed: {}", e.getMessage());
        }
        // The pure-Java reader couldn't decode this WebP (some crawled CDN WebPs).
        // Fall back to the `dwebp` CLI tool (libwebp-tools), which handles them.
        byte[] viaTool = webpToPngViaDwebp(img.bytes());
        if (viaTool != null) {
            logger.info("WebP→PNG transcode succeeded via dwebp ({} bytes)", viaTool.length);
            return new AiSafeImage(viaTool, "image/png", pngName);
        }
        return img;
    }

    /**
     * Decodes WebP bytes to PNG using the {@code dwebp} command-line tool, a fallback
     * for WebPs the in-JVM reader can't handle. Returns null if the tool is absent or
     * fails (callers then fall back to their existing behavior). Pure subprocess — no
     * JNI — so it works on the Alpine runtime where native ImageIO plugins can't load.
     */
    private byte[] webpToPngViaDwebp(byte[] webpBytes) {
        java.nio.file.Path in = null;
        java.nio.file.Path out = null;
        try {
            in = java.nio.file.Files.createTempFile("cover-in-", ".webp");
            out = java.nio.file.Files.createTempFile("cover-out-", ".png");
            java.nio.file.Files.write(in, webpBytes);
            Process p = new ProcessBuilder("dwebp", in.toString(), "-o", out.toString())
                    .redirectErrorStream(true)
                    .start();
            if (!p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            if (p.exitValue() != 0) {
                return null;
            }
            byte[] png = java.nio.file.Files.readAllBytes(out);
            return png.length > 0 ? png : null;
        } catch (Exception e) {
            logger.warn("dwebp WebP→PNG conversion unavailable/failed: {}", e.getMessage());
            return null;
        } finally {
            deleteQuietly(in);
            deleteQuietly(out);
        }
    }

    private static void deleteQuietly(java.nio.file.Path path) {
        if (path == null) return;
        try {
            java.nio.file.Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort temp cleanup
        }
    }

    /**
     * Ensures the bytes are JPEG, PNG or WebP (the only formats the OpenAI edits
     * endpoint accepts). The declared content type / extension is ignored — stored
     * files can be raw AVIF under a {@code .jpg} name (web-crawled images) and
     * OpenAI sniffs the real content. Decodable other formats (GIF, BMP, ...) are
     * re-encoded to PNG; undecodable ones (AVIF/HEIC) are rejected.
     */
    private AiSafeImage normalizeForAi(byte[] bytes, String fileName) {
        String base = stripExtension(fileName);
        switch (sniffImageFormat(bytes)) {
            case "jpeg":
                return new AiSafeImage(bytes, "image/jpeg", base + ".jpg");
            case "png":
                return new AiSafeImage(bytes, "image/png", base + ".png");
            case "webp":
                return new AiSafeImage(bytes, "image/webp", base + ".webp");
            default:
                try {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (img != null) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        ImageIO.write(img, "png", out);
                        return new AiSafeImage(out.toByteArray(), "image/png", base + ".png");
                    }
                } catch (IOException ignored) {
                    // fall through to the unsupported-format error
                }
                throw new WarehouseManagementException(ErrorCode.AI_COVER_UNSUPPORTED_FORMAT);
        }
    }

    /** Detects the actual image format from magic bytes. */
    private static String sniffImageFormat(byte[] b) {
        if (b == null || b.length < 12) return "unknown";
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpeg";
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        return "unknown";
    }

    private static String stripExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) return "cover-input";
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
