package com.warehouse.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Upload validation used to trust {@code Content-Type}, a header the client writes.
 * These tests pin the byte-level checks that replaced it.
 */
class UploadValidatorTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};

    /**
     * The original attack: declare {@code image/svg+xml}, get an .svg stored and served
     * back from the site origin as a scriptable document.
     */
    @Test
    void rejectsSvgEvenWhenTheHeaderClaimsItIsAnImage() {
        MockMultipartFile svg = new MockMultipartFile("file", "logo.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes());
        assertThatThrownBy(() -> UploadValidator.validateImage(svg, 1024 * 1024))
                .isInstanceOf(UploadValidator.InvalidUploadException.class);
    }

    @Test
    void rejectsHtmlDisguisedAsPng() {
        MockMultipartFile html = new MockMultipartFile("file", "photo.png", "image/png",
                "<!DOCTYPE html><html><script>alert(1)</script></html>".getBytes());
        assertThatThrownBy(() -> UploadValidator.validateImage(html, 1024 * 1024))
                .isInstanceOf(UploadValidator.InvalidUploadException.class);
    }

    @Test
    void acceptsRealImagesAndNamesThemFromTheirBytes() {
        MockMultipartFile png = new MockMultipartFile("file", "whatever.txt", "text/plain", PNG);
        UploadValidator.ImageType type = UploadValidator.validateImage(png, 1024 * 1024);
        assertThat(type).isEqualTo(UploadValidator.ImageType.PNG);
        assertThat(type.extension).isEqualTo("png");
        assertThat(type.contentType).isEqualTo("image/png");

        MockMultipartFile jpeg = new MockMultipartFile("file", "x.png", "image/png", JPEG);
        assertThat(UploadValidator.validateImage(jpeg, 1024 * 1024))
                .isEqualTo(UploadValidator.ImageType.JPEG);
    }

    @Test
    void enforcesTheSizeCeiling() {
        MockMultipartFile big = new MockMultipartFile("file", "big.png", "image/png", new byte[2048]);
        assertThatThrownBy(() -> UploadValidator.validateImage(big, 1024))
                .isInstanceOf(UploadValidator.InvalidUploadException.class)
                .hasMessageContaining("büyük");
    }

    @Test
    void documentUploadsAreLimitedToKnownFormats() {
        MockMultipartFile exe = new MockMultipartFile("file", "payload.exe",
                "application/octet-stream", new byte[]{'M', 'Z'});
        assertThatThrownBy(() -> UploadValidator.validateDocument(exe, 1024 * 1024))
                .isInstanceOf(UploadValidator.InvalidUploadException.class);

        MockMultipartFile fakePdf = new MockMultipartFile("file", "invoice.pdf",
                "application/pdf", "not really a pdf".getBytes());
        assertThatThrownBy(() -> UploadValidator.validateDocument(fakePdf, 1024 * 1024))
                .isInstanceOf(UploadValidator.InvalidUploadException.class);

        MockMultipartFile csvWithHtml = new MockMultipartFile("file", "data.csv",
                "text/csv", "<html><script>alert(1)</script></html>".getBytes());
        assertThatThrownBy(() -> UploadValidator.validateDocument(csvWithHtml, 1024 * 1024))
                .isInstanceOf(UploadValidator.InvalidUploadException.class);

        MockMultipartFile realPdf = new MockMultipartFile("file", "invoice.pdf",
                "application/pdf", "%PDF-1.7 body".getBytes());
        assertThat(UploadValidator.validateDocument(realPdf, 1024 * 1024)).isEqualTo("pdf");
    }

    /** A stored key must never map back to a type the browser will execute. */
    @Test
    void servedContentTypeIsNeverActive() {
        assertThat(UploadValidator.safeContentTypeFor("reviews/1/abc.png")).isEqualTo("image/png");
        assertThat(UploadValidator.safeContentTypeFor("reviews/1/abc.svg"))
                .isEqualTo("application/octet-stream");
        assertThat(UploadValidator.safeContentTypeFor("reviews/1/abc.html"))
                .isEqualTo("application/octet-stream");
        assertThat(UploadValidator.safeContentTypeFor(null)).isEqualTo("application/octet-stream");
    }

    @Test
    void extensionParsingIgnoresPathTraversalAttempts() {
        assertThat(UploadValidator.extensionOf("../../etc/passwd")).isEmpty();
        assertThat(UploadValidator.extensionOf("..\\..\\windows\\win.ini")).isEqualTo("ini");
        assertThat(UploadValidator.extensionOf("photo.PNG")).isEqualTo("png");
        assertThat(UploadValidator.extensionOf("no-extension")).isEmpty();
    }
}
