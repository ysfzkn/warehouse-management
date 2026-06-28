package com.warehouse.service;

import com.warehouse.entity.ProductImage;

import java.io.InputStream;
import java.util.List;

/**
 * AI-generated cover photo pipeline for product sets (bundles).
 * <p>
 * The admin selects exactly one input photo per bundle member (uploaded or copied
 * from the member product's own gallery); these are stored on the set as hidden
 * {@code COVER_INPUT} images. Generation sends all inputs to the OpenAI Images
 * edit API, which combines them into a single catalog photo saved as the set's
 * primary gallery image (marked {@code AI_COVER}).
 */
public interface ProductSetCoverService {

    /** Result of bulk-filling inputs from member primaries. */
    record FillResult(int filled, List<Long> missingMemberIds) {}

    /** Upload (or replace) the cover input photo for one bundle member. */
    ProductImage setCoverInput(Long setId, Long memberProductId,
                               String originalFileName, String contentType, InputStream inputStream);

    /** Copy an existing image of the member product as that member's cover input. */
    ProductImage setCoverInputFromImage(Long setId, Long memberProductId, Long imageId);

    /** Remove the cover input photo of one bundle member. */
    void deleteCoverInput(Long setId, Long memberProductId);

    /**
     * Fill every member's cover input from that member's primary (or first) gallery
     * image, overwriting existing selections. Members without any usable photo are
     * reported back instead of failing the whole operation.
     */
    FillResult fillCoverInputsFromPrimaries(Long setId);

    /**
     * Generate the AI cover from the per-member inputs, delete any previous AI
     * cover, store the new image and make it the set's primary gallery image.
     * Synchronous; the OpenAI call may take 30–90 seconds.
     */
    ProductImage generateCover(Long setId);

    /**
     * Build the cover locally by compositing the per-member input photos into one
     * collage with Java 2D — no AI, no API key, no network — then store it as the
     * set's primary gallery image exactly like {@link #generateCover(Long)}.
     * Effectively instant and free; products keep their own backgrounds/lighting.
     */
    ProductImage generateCoverLocally(Long setId);
}
