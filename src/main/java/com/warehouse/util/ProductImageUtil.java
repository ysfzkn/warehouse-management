package com.warehouse.util;

import com.warehouse.entity.ProductImage;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Shared rules for which product images are displayable and which one is the
 * cover. With the AI set-cover feature, {@code product_images} now holds three
 * kinds of rows: gallery images (displayable; the AI-generated cover is one of
 * them), showcase slot images ({@code slot != null}) and hidden per-member AI
 * cover inputs ({@code ai_role = 'COVER_INPUT'}). Every cover/thumbnail
 * resolution must go through these helpers so hidden rows never leak out.
 */
public final class ProductImageUtil {

    public static final String ROLE_COVER_INPUT = "COVER_INPUT";
    public static final String ROLE_AI_COVER = "AI_COVER";

    private ProductImageUtil() {
    }

    /** Gallery image: not a showcase slot, not a hidden AI cover input. */
    public static boolean isGalleryImage(ProductImage img) {
        return img.getSlot() == null && !ROLE_COVER_INPUT.equals(img.getAiRole());
    }

    /**
     * The image to display as the product's cover: the primary gallery image,
     * falling back to the gallery image with the lowest sort order.
     */
    public static Optional<ProductImage> displayCover(Collection<ProductImage> images) {
        if (images == null || images.isEmpty()) {
            return Optional.empty();
        }
        return images.stream()
                .filter(ProductImageUtil::isGalleryImage)
                .filter(ProductImage::isPrimary)
                .findFirst()
                .or(() -> images.stream()
                        .filter(ProductImageUtil::isGalleryImage)
                        .min(Comparator.comparingInt(
                                img -> img.getSortOrder() == null ? Integer.MAX_VALUE : img.getSortOrder())));
    }
}
