package com.warehouse.security;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Content-based validation for uploaded files.
 *
 * <p>Checking {@code MultipartFile.getContentType()} proves nothing: it is a header
 * the client writes. A request declaring {@code image/svg+xml} previously produced a
 * stored {@code .svg} that was served back with that same content type from the site
 * origin — a scriptable document, i.e. stored XSS. And a {@code .html} disguised as
 * {@code image/png} was equally accepted.</p>
 *
 * <p>So the file is identified by its magic bytes, the extension is rewritten to
 * match what the bytes actually are, and the content type served later is derived
 * from that verdict rather than from anything the uploader said. SVG is rejected
 * outright: there is no way to serve attacker-supplied SVG from the application
 * origin safely.</p>
 */
public final class UploadValidator {

    private UploadValidator() {}

    /** Image formats the storefront and admin panel actually need. */
    public enum ImageType {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        GIF("gif", "image/gif"),
        WEBP("webp", "image/webp"),
        /** Favicons are commonly uploaded as .ico; the format cannot carry script. */
        ICO("ico", "image/x-icon");

        public final String extension;
        public final String contentType;

        ImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }
    }

    /** Document formats accepted by the stock import and assistant knowledge base. */
    private static final Set<String> DOCUMENT_EXTENSIONS =
            Set.of("xlsx", "xls", "csv", "pdf", "docx", "txt", "md");

    private static final Map<String, String> DOCUMENT_CONTENT_TYPES = Map.of(
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "xls", "application/vnd.ms-excel",
            "csv", "text/csv",
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "txt", "text/plain",
            "md", "text/plain");

    public static class InvalidUploadException extends RuntimeException {
        public InvalidUploadException(String message) {
            super(message);
        }
    }

    /**
     * Verifies that the bytes really are one of the supported raster image formats.
     *
     * @param maxBytes hard size ceiling; the servlet multipart limit is a global
     *                 backstop but per-endpoint limits keep a single review photo
     *                 from consuming the whole 20 MB budget.
     * @return the detected type — use {@link ImageType#extension} for the stored
     *         file name and {@link ImageType#contentType} when serving it back
     */
    public static ImageType validateImage(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("Dosya boş.");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidUploadException(
                    "Dosya çok büyük. En fazla " + (maxBytes / (1024 * 1024)) + " MB yükleyebilirsiniz.");
        }
        byte[] header = readHeader(file);
        ImageType type = detectImage(header);
        if (type == null) {
            throw new InvalidUploadException(
                    "Sadece JPG, PNG, GIF veya WEBP görseli yükleyebilirsiniz. (SVG kabul edilmez.)");
        }
        return type;
    }

    /**
     * Validates a non-image document by extension allowlist plus a magic-byte check
     * for the formats that have a reliable signature.
     *
     * @return the canonical, lower-case extension to store the file under
     */
    public static String validateDocument(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("Dosya boş.");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidUploadException(
                    "Dosya çok büyük. En fazla " + (maxBytes / (1024 * 1024)) + " MB yükleyebilirsiniz.");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!DOCUMENT_EXTENSIONS.contains(ext)) {
            throw new InvalidUploadException("Desteklenmeyen dosya türü: " + (ext.isEmpty() ? "?" : ext));
        }
        byte[] header = readHeader(file);
        // An HTML/script payload renamed to .csv or .txt would still be served as
        // text/* from our origin, so reject anything that starts like a document
        // the browser would parse as markup.
        if (looksLikeMarkup(header)) {
            throw new InvalidUploadException("Dosya içeriği güvenli değil.");
        }
        if ("pdf".equals(ext) && !startsWith(header, new byte[]{0x25, 0x50, 0x44, 0x46})) { // %PDF
            throw new InvalidUploadException("Geçerli bir PDF dosyası değil.");
        }
        if (("xlsx".equals(ext) || "docx".equals(ext))
                && !startsWith(header, new byte[]{0x50, 0x4B})) { // PK zip container
            throw new InvalidUploadException("Geçerli bir Office dosyası değil.");
        }
        return ext;
    }

    /**
     * A scan or photograph of a signed paper document — an image or a PDF.
     *
     * <p>Warehouse staff upload whatever their phone or the office scanner produced, so
     * both families have to be accepted at one endpoint. The verdict still comes from the
     * bytes: a {@code .pdf} that is really HTML would otherwise be stored and later served
     * as {@code application/pdf} from our own origin.</p>
     *
     * @return the canonical extension and the content type to serve the file with
     */
    public static ScannedDocument validateScan(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("Dosya boş.");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidUploadException(
                    "Dosya çok büyük. En fazla " + (maxBytes / (1024 * 1024)) + " MB yükleyebilirsiniz.");
        }
        byte[] header = readHeader(file);
        ImageType image = detectImage(header);
        if (image != null) {
            return new ScannedDocument(image.extension, image.contentType);
        }
        if (startsWith(header, new byte[]{0x25, 0x50, 0x44, 0x46})) { // %PDF
            return new ScannedDocument("pdf", "application/pdf");
        }
        throw new InvalidUploadException(
                "Sadece JPG, PNG, WEBP görseli veya PDF yükleyebilirsiniz.");
    }

    /** Result of {@link #validateScan}: what the bytes actually are. */
    public record ScannedDocument(String extension, String contentType) {
        public boolean isPdf() {
            return "pdf".equals(extension);
        }
    }

    /**
     * Identifies an image from its leading bytes, ignoring whatever the file is called.
     *
     * <p>Needed wherever a stored file has to be re-encoded rather than merely streamed:
     * the site logo, for instance, is stored under a {@code .png} path but the bytes are
     * often a JPEG the browser sniffed its way through. Embedding those bytes under a
     * {@code data:image/png} URI produces a PDF with no logo at all.</p>
     *
     * @return the detected type, or null when the bytes are not a supported image
     */
    public static ImageType detectImageType(byte[] header) {
        return detectImage(header);
    }

    /** Content type to serve a stored document with. Never echoes the uploader's header. */
    public static String documentContentType(String extension) {
        return DOCUMENT_CONTENT_TYPES.getOrDefault(
                extension == null ? "" : extension.toLowerCase(Locale.ROOT),
                "application/octet-stream");
    }

    /**
     * Maps a stored key/filename back to a safe content type for serving. Anything
     * unrecognised becomes {@code application/octet-stream} so the browser downloads
     * it instead of rendering it.
     */
    public static String safeContentTypeFor(String storageKeyOrFilename) {
        String ext = extensionOf(storageKeyOrFilename);
        for (ImageType type : ImageType.values()) {
            if (type.extension.equals(ext)) return type.contentType;
        }
        if ("jpeg".equals(ext)) return ImageType.JPEG.contentType;
        return DOCUMENT_CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    /** Strips any directory component and returns the lower-case extension without the dot. */
    public static String extensionOf(String filename) {
        if (filename == null) return "";
        String name = filename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,8}") ? ext : "";
    }

    private static byte[] readHeader(MultipartFile file) {
        try (var in = file.getInputStream()) {
            byte[] buf = new byte[64];
            int read = in.readNBytes(buf, 0, buf.length);
            if (read < buf.length) {
                byte[] exact = new byte[Math.max(read, 0)];
                System.arraycopy(buf, 0, exact, 0, Math.max(read, 0));
                return exact;
            }
            return buf;
        } catch (IOException e) {
            throw new InvalidUploadException("Dosya okunamadı.");
        }
    }

    private static ImageType detectImage(byte[] h) {
        if (startsWith(h, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) return ImageType.JPEG;
        if (startsWith(h, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) return ImageType.PNG;
        if (startsWith(h, "GIF87a".getBytes()) || startsWith(h, "GIF89a".getBytes())) return ImageType.GIF;
        // WEBP: "RIFF" .... "WEBP"
        if (startsWith(h, "RIFF".getBytes()) && h.length >= 12
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return ImageType.WEBP;
        }
        // ICO: reserved(0) + type(1 = icon)
        if (startsWith(h, new byte[]{0x00, 0x00, 0x01, 0x00})) return ImageType.ICO;
        return null;
    }

    private static boolean looksLikeMarkup(byte[] header) {
        String start = new String(header).trim().toLowerCase(Locale.ROOT);
        return start.startsWith("<!doctype") || start.startsWith("<html") || start.startsWith("<?xml")
                || start.startsWith("<svg") || start.startsWith("<script");
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data == null || data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }
}
