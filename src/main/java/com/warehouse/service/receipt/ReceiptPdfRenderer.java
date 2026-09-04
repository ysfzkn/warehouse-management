package com.warehouse.service.receipt;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

/**
 * Turns the receipt template's HTML into a PDF.
 *
 * <p>One template produces both outputs — the browser gets the HTML for {@code Ctrl+P},
 * the archive gets the PDF — so the printed page and the stored copy cannot drift apart.
 *
 * <p>Two details that are easy to get wrong and expensive to discover later:
 *
 * <ul>
 *   <li><b>XHTML.</b> openhtmltopdf parses with a strict XML reader and rejects the
 *       HTML5 that Thymeleaf emits (unclosed {@code <br>}, bare attributes). Rather than
 *       hand-writing XHTML in the template and hoping nobody breaks it, the output is run
 *       through jsoup and re-serialised as XML. jsoup is already a dependency and its
 *       parser is far more forgiving than the one that would otherwise throw at render
 *       time — on a template edit, in production.</li>
 *   <li><b>Fonts.</b> PDFBox's built-in Helvetica is WinAnsi and has no
 *       {@code ı ğ ş İ Ğ Ş}. A receipt for "Işık Mobilya" would print as "Isik" or as
 *       empty boxes, on the one document that exists to be signed as a legal record. The
 *       bundled Noto Sans is registered here, and the Alpine runtime image ships no fonts
 *       at all, so this is the only reason Turkish renders correctly.</li>
 * </ul>
 */
@Component
public class ReceiptPdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(ReceiptPdfRenderer.class);

    private static final String FONT_FAMILY = "Noto Sans";
    private static final String FONT_REGULAR = "fonts/NotoSans-Regular.ttf";
    private static final String FONT_BOLD = "fonts/NotoSans-Bold.ttf";

    static {
        // openhtmltopdf logs every CSS property it does not implement at INFO, which for
        // one receipt is dozens of lines of noise about unsupported shorthands.
        XRLog.setLevel(XRLog.CSS_PARSE, Level.WARNING);
        XRLog.setLevel(XRLog.EXCEPTION, Level.WARNING);
        XRLog.setLevel(XRLog.LAYOUT, Level.WARNING);
        XRLog.setLevel(XRLog.RENDER, Level.WARNING);
        XRLog.setLevel(XRLog.MATCH, Level.WARNING);
        XRLog.setLevel(XRLog.GENERAL, Level.WARNING);
    }

    /**
     * @param html markup produced by the Thymeleaf template; need not be well-formed XML
     * @return the rendered PDF
     */
    public byte[] render(String html) {
        String xhtml = toXhtml(html);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerFonts(builder);
            // No base URI and no external resources: everything the page needs (the logo)
            // is inlined as a data URI. A template that reached out to a URL would turn
            // PDF generation into an outbound request from inside the network.
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Makbuz PDF'i oluşturulamadı: " + e.getMessage(), e);
        }
    }

    private void registerFonts(PdfRendererBuilder builder) {
        builder.useFont(() -> openFont(FONT_REGULAR), FONT_FAMILY, 400,
                PdfRendererBuilder.FontStyle.NORMAL, true);
        builder.useFont(() -> openFont(FONT_BOLD), FONT_FAMILY, 700,
                PdfRendererBuilder.FontStyle.NORMAL, true);
    }

    private InputStream openFont(String path) {
        try {
            return new ClassPathResource(path).getInputStream();
        } catch (IOException e) {
            // Without the font the PDF would silently print Turkish as blank boxes, which
            // is worse than a clear failure on a document meant to be signed.
            throw new IllegalStateException(
                    "Makbuz fontu bulunamadı: " + path + ". Türkçe karakterler basılamaz.", e);
        }
    }

    /** Reparses the markup and re-serialises it as XML so the PDF renderer accepts it. */
    private String toXhtml(String html) {
        Document document = Jsoup.parse(html);
        document.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .prettyPrint(false)
                .charset(java.nio.charset.StandardCharsets.UTF_8);
        return document.html();
    }
}
