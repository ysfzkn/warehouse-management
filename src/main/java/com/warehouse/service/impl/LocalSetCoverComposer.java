package com.warehouse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a product-set cover by compositing the member photos onto one white
 * canvas with pure Java 2D — no AI, no network, free and effectively instant.
 *
 * <p>Each product photo is first trimmed of its uniform (white / transparent)
 * border so the appliance fills its cell, then scaled to fit and centered inside
 * a quadrant of a balanced "corner collage" layout (the same look as a studio
 * catalog set photo: e.g. three appliances pinned to three corners of the frame).
 *
 * <p>This is intentionally deterministic and faithful — it never repaints or
 * reinvents a product, so logos, text and colors are preserved pixel-for-pixel.
 * The trade-off vs. the OpenAI path is that products keep their own backgrounds
 * and lighting rather than being re-lit into a single seamless scene.
 */
@Component
public class LocalSetCoverComposer {

    private static final Logger logger = LoggerFactory.getLogger(LocalSetCoverComposer.class);

    /** Output is a square catalog image, matching the AI cover dimensions. */
    private static final int CANVAS = 1024;
    /** Empty frame around the whole collage. */
    private static final int MARGIN = 56;
    /** Gap between neighboring cells. */
    private static final int GUTTER = 36;
    /** Padding inside each cell so products don't touch their cell edges. */
    private static final double CELL_PADDING_RATIO = 0.05;
    /** Per-channel distance from the sampled border color still counted as background. */
    private static final int BG_TOLERANCE = 22;
    /** Alpha at or below this is treated as fully transparent (background). */
    private static final int ALPHA_FLOOR = 16;

    private static final Color BACKGROUND = Color.WHITE;

    /**
     * Composites the given encoded images (JPEG/PNG/WebP bytes) into one PNG.
     *
     * @param imageBytes member photos in member order; undecodable entries are skipped
     * @return PNG bytes of the composed cover
     * @throws IllegalStateException if none of the inputs could be decoded
     */
    public byte[] compose(List<byte[]> imageBytes) {
        List<BufferedImage> products = new ArrayList<>();
        for (byte[] bytes : imageBytes) {
            BufferedImage decoded = decode(bytes);
            if (decoded != null) {
                products.add(trimBackground(decoded));
            }
        }
        if (products.isEmpty()) {
            throw new IllegalStateException("No decodable product images to compose");
        }
        // Never silently drop a member: if any input failed to decode, the collage
        // would be missing a product. Callers validate decodability up front, so this
        // is a guard against ever producing a partial cover without surfacing it.
        if (products.size() != imageBytes.size()) {
            throw new IllegalStateException(
                    "Only " + products.size() + " of " + imageBytes.size() + " product images could be decoded");
        }

        BufferedImage canvas = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, CANVAS, CANVAS);

            List<Rectangle> cells = layout(products.size());
            for (int i = 0; i < products.size() && i < cells.size(); i++) {
                drawFitted(g, products.get(i), cells.get(i));
            }
        } finally {
            g.dispose();
        }

        byte[] png = toPng(canvas);
        logger.info("Local set cover composed from {} product photo(s), {} bytes", products.size(), png.length);
        return png;
    }

    // ─────────────────────────── layout ───────────────────────────

    /**
     * Cell rectangles for {@code count} products inside the inner (margined) area.
     * Small counts get hand-tuned "corner" arrangements; larger counts fall back
     * to a balanced grid.
     */
    private List<Rectangle> layout(int count) {
        int x0 = MARGIN;
        int y0 = MARGIN;
        int w = CANVAS - 2 * MARGIN;
        int h = CANVAS - 2 * MARGIN;
        List<Rectangle> cells = new ArrayList<>();

        switch (count) {
            case 1:
                cells.add(new Rectangle(x0, y0, w, h));
                break;
            case 2: {
                int colW = (w - GUTTER) / 2;
                cells.add(new Rectangle(x0, y0, colW, h));
                cells.add(new Rectangle(x0 + colW + GUTTER, y0, w - colW - GUTTER, h));
                break;
            }
            case 3: {
                // Two on top, one centered & wider along the bottom — the classic
                // three-corner appliance set look.
                int colW = (w - GUTTER) / 2;
                int rowH = (h - GUTTER) / 2;
                cells.add(new Rectangle(x0, y0, colW, rowH));
                cells.add(new Rectangle(x0 + colW + GUTTER, y0, w - colW - GUTTER, rowH));
                int bottomW = (int) Math.round(w * 0.66);
                int bottomX = x0 + (w - bottomW) / 2;
                cells.add(new Rectangle(bottomX, y0 + rowH + GUTTER, bottomW, h - rowH - GUTTER));
                break;
            }
            case 4: {
                int colW = (w - GUTTER) / 2;
                int rowH = (h - GUTTER) / 2;
                int rightW = w - colW - GUTTER;
                int botH = h - rowH - GUTTER;
                cells.add(new Rectangle(x0, y0, colW, rowH));
                cells.add(new Rectangle(x0 + colW + GUTTER, y0, rightW, rowH));
                cells.add(new Rectangle(x0, y0 + rowH + GUTTER, colW, botH));
                cells.add(new Rectangle(x0 + colW + GUTTER, y0 + rowH + GUTTER, rightW, botH));
                break;
            }
            default:
                cells.addAll(grid(count, x0, y0, w, h));
                break;
        }
        return cells;
    }

    /** Even rows×cols grid that holds at least {@code count} cells. */
    private List<Rectangle> grid(int count, int x0, int y0, int w, int h) {
        int cols = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil((double) count / cols);
        int cellW = (w - (cols - 1) * GUTTER) / cols;
        int cellH = (h - (rows - 1) * GUTTER) / rows;
        List<Rectangle> cells = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int r = i / cols;
            int c = i % cols;
            cells.add(new Rectangle(x0 + c * (cellW + GUTTER), y0 + r * (cellH + GUTTER), cellW, cellH));
        }
        return cells;
    }

    // ─────────────────────────── drawing ───────────────────────────

    /** Scales the image to fit the cell (preserving aspect) and centers it. */
    private void drawFitted(Graphics2D g, BufferedImage img, Rectangle cell) {
        int pad = (int) Math.round(Math.min(cell.width, cell.height) * CELL_PADDING_RATIO);
        int availW = Math.max(1, cell.width - 2 * pad);
        int availH = Math.max(1, cell.height - 2 * pad);
        double scale = Math.min((double) availW / img.getWidth(), (double) availH / img.getHeight());
        int drawW = Math.max(1, (int) Math.round(img.getWidth() * scale));
        int drawH = Math.max(1, (int) Math.round(img.getHeight() * scale));
        int drawX = cell.x + (cell.width - drawW) / 2;
        int drawY = cell.y + (cell.height - drawH) / 2;
        g.drawImage(img, drawX, drawY, drawW, drawH, null);
    }

    // ─────────────────────── background trimming ───────────────────────

    /**
     * Crops the uniform border (white studio background or transparency) from the
     * edges of the image. Only trims from the outside in — it never eats into the
     * product interior — so even a white appliance on white is kept intact, since
     * its edge/shadow differs from the pure border color.
     */
    private BufferedImage trimBackground(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        if (width < 4 || height < 4) {
            return img;
        }
        int corner = img.getRGB(0, 0);
        boolean bgTransparent = ((corner >>> 24) & 0xFF) <= ALPHA_FLOOR;

        int top = 0;
        while (top < height - 1 && rowIsBackground(img, top, width, corner, bgTransparent)) {
            top++;
        }
        int bottom = height - 1;
        while (bottom > top && rowIsBackground(img, bottom, width, corner, bgTransparent)) {
            bottom--;
        }
        int left = 0;
        while (left < width - 1 && colIsBackground(img, left, top, bottom, corner, bgTransparent)) {
            left++;
        }
        int right = width - 1;
        while (right > left && colIsBackground(img, right, top, bottom, corner, bgTransparent)) {
            right--;
        }

        int cropW = right - left + 1;
        int cropH = bottom - top + 1;
        if (cropW <= 0 || cropH <= 0 || (cropW == width && cropH == height)) {
            return img;
        }
        return img.getSubimage(left, top, cropW, cropH);
    }

    private boolean rowIsBackground(BufferedImage img, int y, int width, int bg, boolean bgTransparent) {
        for (int x = 0; x < width; x++) {
            if (!isBackground(img.getRGB(x, y), bg, bgTransparent)) {
                return false;
            }
        }
        return true;
    }

    private boolean colIsBackground(BufferedImage img, int x, int top, int bottom, int bg, boolean bgTransparent) {
        for (int y = top; y <= bottom; y++) {
            if (!isBackground(img.getRGB(x, y), bg, bgTransparent)) {
                return false;
            }
        }
        return true;
    }

    private boolean isBackground(int argb, int bg, boolean bgTransparent) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha <= ALPHA_FLOOR) {
            return true; // transparent pixels are always background
        }
        if (bgTransparent) {
            return false; // opaque pixel on a transparent-bordered image = content
        }
        int dr = Math.abs(((argb >> 16) & 0xFF) - ((bg >> 16) & 0xFF));
        int dg = Math.abs(((argb >> 8) & 0xFF) - ((bg >> 8) & 0xFF));
        int db = Math.abs((argb & 0xFF) - (bg & 0xFF));
        return dr <= BG_TOLERANCE && dg <= BG_TOLERANCE && db <= BG_TOLERANCE;
    }

    // ─────────────────────────── codecs ───────────────────────────

    private BufferedImage decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            // Catch RuntimeExceptions too: some WebP rasters throw ArrayIndexOutOfBounds
            // ("Coordinate out of bounds!") rather than returning null.
            logger.warn("Skipping undecodable cover input ({} bytes): {}", bytes.length, e.getMessage());
            return null;
        }
    }

    private byte[] toPng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
