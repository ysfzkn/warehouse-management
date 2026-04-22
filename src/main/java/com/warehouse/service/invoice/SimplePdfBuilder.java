package com.warehouse.service.invoice;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Dependency-free minimal PDF 1.4 üretici.
 *
 * <p>İtext / PDFBox eklemek istemediğimiz durumlarda (MOCK provider, basit
 * tahsilat makbuzu vs.) işe yarar — ASCII olarak yazılan, xref tablosu
 * doğru hesaplanmış, herhangi bir PDF reader'ın açabileceği geçerli bir PDF.
 *
 * <p>Sadece tek sayfa + tek font (Helvetica) destekler. Her satır için
 * konum + metin al. Türkçe karakter desteği için WinAnsiEncoding (ISO 8859-1
 * eşdeğeri) kullanılır; tüm karakterler temel Latin-1 kümesine eşlenir.
 *
 * <p>Kullanım:
 * <pre>
 * byte[] pdf = new SimplePdfBuilder()
 *     .line(50, 800, "MOCK E-FATURA", 18)
 *     .line(50, 770, "Fatura No: EAR2026000042")
 *     .build();
 * </pre>
 */
public class SimplePdfBuilder {

    private final List<String> contentOps = new ArrayList<>();

    /** Varsayılan 12pt satır ekle. */
    public SimplePdfBuilder line(int x, int y, String text) {
        return line(x, y, text, 12);
    }

    public SimplePdfBuilder line(int x, int y, String text, int fontSize) {
        String safe = toWinAnsi(text);
        contentOps.add("BT /F1 " + fontSize + " Tf " + x + " " + y + " Td (" + safe + ") Tj ET");
        return this;
    }

    public byte[] build() {
        // ── Object'leri hazırla ──
        // Obj 1: Catalog   → Pages 2
        // Obj 2: Pages     → Kids [3], Count 1
        // Obj 3: Page      → Parent 2, MediaBox, Contents 4, Resources Font F1=5
        // Obj 4: Content stream
        // Obj 5: Font Helvetica
        String content = String.join("\n", contentOps);
        byte[] contentBytes = content.getBytes(StandardCharsets.ISO_8859_1);

        String obj1 = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n";
        String obj2 = "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n";
        String obj3 = "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                   + "/Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n";
        String obj4a = "4 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n";
        String obj4b = "\nendstream\nendobj\n";
        String obj5 = "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                   + "/Encoding /WinAnsiEncoding >>\nendobj\n";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        long[] offsets = new long[5];
        try {
            // Header
            byte[] header = "%PDF-1.4\n%âãÏÓ\n".getBytes(StandardCharsets.ISO_8859_1);
            baos.write(header);

            offsets[0] = baos.size();
            baos.write(obj1.getBytes(StandardCharsets.ISO_8859_1));

            offsets[1] = baos.size();
            baos.write(obj2.getBytes(StandardCharsets.ISO_8859_1));

            offsets[2] = baos.size();
            baos.write(obj3.getBytes(StandardCharsets.ISO_8859_1));

            offsets[3] = baos.size();
            baos.write(obj4a.getBytes(StandardCharsets.ISO_8859_1));
            baos.write(contentBytes);
            baos.write(obj4b.getBytes(StandardCharsets.ISO_8859_1));

            offsets[4] = baos.size();
            baos.write(obj5.getBytes(StandardCharsets.ISO_8859_1));

            // xref
            long xrefStart = baos.size();
            StringBuilder xref = new StringBuilder();
            xref.append("xref\n0 6\n");
            xref.append("0000000000 65535 f \n");
            for (long off : offsets) {
                xref.append(String.format("%010d 00000 n \n", off));
            }
            baos.write(xref.toString().getBytes(StandardCharsets.ISO_8859_1));

            // Trailer
            String trailer = "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n"
                          + xrefStart + "\n%%EOF\n";
            baos.write(trailer.getBytes(StandardCharsets.ISO_8859_1));

            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF build failed: " + e.getMessage(), e);
        }
    }

    /**
     * Türkçe karakter map'leme (WinAnsi/cp1252).
     * Ayrıca parens ve backslash'ı kaçış karakteriyle değiştirir.
     */
    private String toWinAnsi(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '(' -> sb.append("\\(");
                case ')' -> sb.append("\\)");
                case '\\' -> sb.append("\\\\");
                // Türkçe karakter yaklaşımı — WinAnsiEncoding'de olanlar kalır
                case 'ı' -> sb.append('i');
                case 'İ' -> sb.append('I');
                case 'ş' -> sb.append('s');
                case 'Ş' -> sb.append('S');
                case 'ğ' -> sb.append('g');
                case 'Ğ' -> sb.append('G');
                case 'ü' -> sb.append('ü');   // WinAnsi'de 0xFC
                case 'Ü' -> sb.append('Ü');
                case 'ö' -> sb.append('ö');   // WinAnsi'de 0xF6
                case 'Ö' -> sb.append('Ö');
                case 'ç' -> sb.append('ç');
                case 'Ç' -> sb.append('Ç');
                default -> {
                    if (c < 32 || c > 255) sb.append('?');
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
