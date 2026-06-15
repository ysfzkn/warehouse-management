package com.warehouse.service.impl;

import com.warehouse.service.EmailService;
import com.warehouse.service.PhotoStorageService;
import com.warehouse.service.SiteSettingService;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final SiteSettingService settingService;
    private final PhotoStorageService photoStorageService;

    @Value("${app.mail.from:noreply@example.com}")
    private String fromEmail;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.base-url:http://localhost:3000}")
    private String baseUrl;

    public EmailServiceImpl(JavaMailSender mailSender, SiteSettingService settingService, PhotoStorageService photoStorageService) {
        this.mailSender = mailSender;
        this.settingService = settingService;
        this.photoStorageService = photoStorageService;
    }

    @Override
    public boolean isEnabled() { return enabled; }

    private String getSiteName() {
        try { String n = settingService.getSetting("site_name"); return (n != null && !n.isEmpty()) ? n : "Mağaza"; } catch (Exception e) { return "Mağaza"; }
    }

    /** Get logo as base64 data URI for inline email embedding */
    private String getLogoBase64() {
        try {
            String logoPath = settingService.getSetting("site_logo");
            if (logoPath == null || logoPath.isEmpty()) return null;
            try (java.io.InputStream is = photoStorageService.openPhotoStream(logoPath)) {
                byte[] bytes = is.readAllBytes();
                String ext = logoPath.toLowerCase();
                String mime = ext.endsWith(".png") ? "image/png" : ext.endsWith(".svg") ? "image/svg+xml" : "image/jpeg";
                return "data:" + mime + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes);
            }
        } catch (Exception e) {
            log.debug("Could not load logo for email: {}", e.getMessage());
            return null;
        }
    }

    private String buildHeader(String title) {
        String siteName = getSiteName();
        String logoBase64 = getLogoBase64();
        String logoHtml = logoBase64 != null
                ? "<img src=\"" + logoBase64 + "\" alt=\"" + siteName + "\" style=\"max-height:44px;margin-bottom:12px;\" /><br/>"
                : "";
        return """
            <div style="font-family:'Segoe UI',Arial,sans-serif;max-width:600px;margin:0 auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,0.08);">
                <div style="background:linear-gradient(135deg,#2563eb,#1e40af);text-align:center;padding:32px 20px;">
                    %s
                    <h2 style="color:#fff;margin:0;font-size:22px;">%s</h2>
                </div>
                <div style="padding:32px 28px;">
            """.formatted(logoHtml, title);
    }

    private String buildFooter() {
        String siteName = getSiteName();
        return """
                </div>
                <div style="background:#f8fafc;border-top:1px solid #e2e8f0;padding:20px 28px;text-align:center;">
                    <p style="color:#94a3b8;font-size:12px;margin:0;">Bu e-postayı siz talep etmediyseniz lütfen dikkate almayın.</p>
                    <p style="color:#94a3b8;font-size:11px;margin:8px 0 0;">&copy; %s</p>
                </div>
            </div>
            """.formatted(siteName);
    }

    @Override
    @Async
    public void sendEmailVerification(String toEmail, String firstName, String verificationToken) {
        if (!enabled) {
            log.info("Email disabled — verification token for {}: {}", toEmail, verificationToken);
            return;
        }
        String verifyUrl = baseUrl + "/hesap-dogrula?token=" + verificationToken;
        String subject = "E-posta Adresinizi Doğrulayın — " + getSiteName();
        String html = buildHeader("Hesap Aktivasyonu")
                + """
                    <p style="color:#334155;font-size:15px;">Merhaba <strong>%s</strong>,</p>
                    <p style="color:#475569;font-size:14px;line-height:1.6;">
                        %s ailesine hoş geldiniz! Hesabınızı etkinleştirmek için aşağıdaki butona tıklayın:
                    </p>
                    <div style="text-align:center;margin:28px 0;">
                        <a href="%s" style="background:linear-gradient(135deg,#2563eb,#1e40af);color:#fff;padding:14px 36px;border-radius:8px;text-decoration:none;font-weight:600;font-size:15px;display:inline-block;box-shadow:0 4px 12px rgba(37,99,235,0.3);">
                            Hesabımı Doğrula
                        </a>
                    </div>
                    <p style="color:#64748b;font-size:13px;">Bu bağlantı 24 saat geçerlidir.</p>
                    <p style="color:#94a3b8;font-size:12px;">Butona tıklayamıyorsanız aşağıdaki bağlantıyı tarayıcınıza yapıştırın:</p>
                    <p style="color:#94a3b8;font-size:11px;word-break:break-all;background:#f1f5f9;padding:10px;border-radius:6px;">%s</p>
                """.formatted(firstName, getSiteName(), verifyUrl, verifyUrl)
                + buildFooter();

        sendHtml(toEmail, subject, html);
    }

    @Override
    @Async
    public void sendPasswordReset(String toEmail, String firstName, String resetToken) {
        if (!enabled) {
            log.info("Email disabled — password reset token for {}: {}", toEmail, resetToken);
            return;
        }
        String resetUrl = baseUrl + "/sifre-sifirla?token=" + resetToken;
        String subject = "Şifre Sıfırlama Talebi — " + getSiteName();
        String html = buildHeader("Şifre Sıfırlama")
                + """
                    <p style="color:#334155;font-size:15px;">Merhaba <strong>%s</strong>,</p>
                    <p style="color:#475569;font-size:14px;line-height:1.6;">
                        Şifrenizi sıfırlamak için bir talep aldık. Aşağıdaki butona tıklayarak yeni şifrenizi belirleyebilirsiniz:
                    </p>
                    <div style="text-align:center;margin:28px 0;">
                        <a href="%s" style="background:linear-gradient(135deg,#f59e0b,#d97706);color:#fff;padding:14px 36px;border-radius:8px;text-decoration:none;font-weight:600;font-size:15px;display:inline-block;box-shadow:0 4px 12px rgba(245,158,11,0.3);">
                            Şifremi Sıfırla
                        </a>
                    </div>
                    <p style="color:#64748b;font-size:13px;">Bu bağlantı 1 saat geçerlidir. Eğer bu talebi siz yapmadıysanız bu e-postayı görmezden gelebilirsiniz.</p>
                """.formatted(firstName, resetUrl)
                + buildFooter();

        sendHtml(toEmail, subject, html);
    }

    @Override
    @Async
    public void sendBackInStockNotification(String toEmail, String productName, String productUrl,
                                             String productImage, String price) {
        if (!enabled) {
            log.info("Email disabled — back-in-stock notification for {} product={}", toEmail, productName);
            return;
        }
        String subject = "Beklediğiniz ürün stoklarda! — " + getSiteName();
        String html = buildHeader("Beklediğiniz Ürün Stoklarda!")
                + """
                    <p style="color:#334155;font-size:15px;">Merhaba,</p>
                    <p style="color:#475569;font-size:14px;line-height:1.6;">
                        Sizi bilgilendirmemizi istediğiniz ürün <strong>stoklarımıza geri geldi</strong>! Fiyat güncel olabilir, hızlıca kontrol etmenizi öneririz.
                    </p>
                    <div style="border:1px solid #e2e8f0;border-radius:12px;padding:16px;margin:24px 0;background:#fafafa;">
                        %s
                        <div style="color:#0f172a;font-size:16px;font-weight:700;margin-bottom:4px;">%s</div>
                        %s
                    </div>
                    <div style="text-align:center;margin:28px 0;">
                        <a href="%s" style="background:linear-gradient(135deg,#10b981,#059669);color:#fff;padding:14px 36px;border-radius:8px;text-decoration:none;font-weight:600;font-size:15px;display:inline-block;box-shadow:0 4px 12px rgba(16,185,129,0.3);">
                            Şimdi İncele
                        </a>
                    </div>
                    <div style="background:#fefce8;border:1px solid #fde047;border-radius:8px;padding:12px 16px;margin:16px 0;">
                        <p style="color:#713f12;font-size:13px;margin:0;">
                            <strong>⚡ Hızlı Olun:</strong> Stoklar sınırlı, başkalarının önüne geçmek için hemen sipariş verin.
                        </p>
                    </div>
                """.formatted(
                    productImage != null && !productImage.isBlank()
                        ? String.format("<img src=\"%s\" alt=\"%s\" style=\"width:100%%;max-width:320px;height:auto;border-radius:8px;margin-bottom:12px;display:block;\" />",
                            productImage, escapeHtmlAttr(productName))
                        : "",
                    escapeHtmlAttr(productName),
                    price != null && !price.isBlank()
                        ? String.format("<div style=\"color:#2563eb;font-size:18px;font-weight:700;\">%s</div>", escapeHtmlAttr(price))
                        : "",
                    productUrl
                )
                + buildFooter();

        sendHtml(toEmail, subject, html);
    }

    @Override
    @Async
    public void sendAbandonedCartReminder(String toEmail, String firstName, int itemCount, String itemsHtml, String cartTotal) {
        if (!enabled) {
            log.info("Email disabled — abandoned cart reminder for {} ({} items)", toEmail, itemCount);
            return;
        }
        String cartUrl = baseUrl + "/sepet";
        String subject = "Sepetinizde bıraktığınız ürünler — " + getSiteName();
        String html = buildHeader("Sepetinizi Unuttunuz Mu?")
                + """
                    <p style="color:#334155;font-size:15px;">Merhaba <strong>%s</strong>,</p>
                    <p style="color:#475569;font-size:14px;line-height:1.6;">
                        Sepetinizde <strong>%d ürün</strong> sizi bekliyor. Aşağıdaki butona tıklayarak alışverişinizi kaldığınız yerden tamamlayabilirsiniz:
                    </p>
                    %s
                    <div style="background:#f8fafc;border-radius:8px;padding:14px 18px;margin:20px 0;text-align:right;">
                        <span style="color:#64748b;font-size:13px;">Sepet Toplamı: </span>
                        <strong style="color:#0f172a;font-size:16px;">%s</strong>
                    </div>
                    <div style="text-align:center;margin:28px 0;">
                        <a href="%s" style="background:linear-gradient(135deg,#ef4444,#dc2626);color:#fff;padding:14px 40px;border-radius:8px;text-decoration:none;font-weight:600;font-size:15px;display:inline-block;box-shadow:0 4px 12px rgba(239,68,68,0.3);">
                            Sepetime Dön
                        </a>
                    </div>
                    <div style="background:#fefce8;border:1px solid #fde047;border-radius:8px;padding:12px 16px;margin:16px 0;">
                        <p style="color:#713f12;font-size:13px;margin:0;">
                            <strong>💡 Not:</strong> Stoklar sınırlı olduğundan, ürünleriniz tükenmeden siparişinizi tamamlamanızı öneririz.
                        </p>
                    </div>
                    <p style="color:#94a3b8;font-size:12px;">Butona tıklayamıyorsanız aşağıdaki bağlantıyı tarayıcınıza yapıştırın:</p>
                    <p style="color:#94a3b8;font-size:11px;word-break:break-all;background:#f1f5f9;padding:10px;border-radius:6px;">%s</p>
                """.formatted(firstName, itemCount, itemsHtml != null ? itemsHtml : "", cartTotal, cartUrl, cartUrl)
                + buildFooter();

        sendHtml(toEmail, subject, html);
    }

    @Override
    @Async
    public void sendCompleteAccountSetup(String toEmail, String firstName, String orderNumber, String setupToken) {
        if (!enabled) {
            log.info("Email disabled — complete account setup token for {}: {}", toEmail, setupToken);
            return;
        }
        String setupUrl = baseUrl + "/hesap-tamamla?token=" + setupToken;
        String subject = "Siparişiniz Alındı! Hesabınızı Tamamlayın — " + getSiteName();
        String html = buildHeader("Siparişiniz Alındı!")
                + """
                    <p style="color:#334155;font-size:15px;">Merhaba <strong>%s</strong>,</p>
                    <p style="color:#475569;font-size:14px;line-height:1.6;">
                        <strong>%s</strong> numaralı siparişiniz başarıyla alındı! 🎉
                    </p>
                    <div style="background:#f0f9ff;border:1px solid #bae6fd;border-radius:8px;padding:16px 20px;margin:20px 0;">
                        <p style="color:#075985;font-size:14px;margin:0 0 8px;font-weight:600;">
                            Bir sonraki alışverişinizi kolaylaştırmak için hesabınızı tamamlayın:
                        </p>
                        <ul style="color:#0c4a6e;font-size:13px;margin:8px 0;padding-left:20px;">
                            <li>Hızlı checkout yapın</li>
                            <li>Siparişlerinizi takip edin</li>
                            <li>Adreslerinizi kaydedin</li>
                            <li>Favori ürünlerinizi saklayın</li>
                        </ul>
                    </div>
                    <div style="text-align:center;margin:28px 0;">
                        <a href="%s" style="background:linear-gradient(135deg,#10b981,#059669);color:#fff;padding:14px 36px;border-radius:8px;text-decoration:none;font-weight:600;font-size:15px;display:inline-block;box-shadow:0 4px 12px rgba(16,185,129,0.3);">
                            Hesabımı Tamamla
                        </a>
                    </div>
                    <p style="color:#64748b;font-size:13px;">Bu bağlantı 7 gün geçerlidir. Sadece şifrenizi belirlemeniz yeterlidir.</p>
                    <p style="color:#94a3b8;font-size:12px;">Butona tıklayamıyorsanız aşağıdaki bağlantıyı tarayıcınıza yapıştırın:</p>
                    <p style="color:#94a3b8;font-size:11px;word-break:break-all;background:#f1f5f9;padding:10px;border-radius:6px;">%s</p>
                """.formatted(firstName, orderNumber, setupUrl, setupUrl)
                + buildFooter();

        sendHtml(toEmail, subject, html);
    }

    @Override
    @Async
    public void sendPasswordResetConfirmation(String toEmail, String firstName) {
        if (!enabled) {
            log.info("Email disabled — password reset confirmation for {}", toEmail);
            return;
        }
        String subject = "Şifreniz Başarıyla Değiştirildi — " + getSiteName();
        String html = buildHeader("Şifre Değişikliği")
                + """
                    <p style="color:#334155;font-size:15px;">Merhaba <strong>%s</strong>,</p>
                    <p style="color:#475569;font-size:14px;line-height:1.6;">
                        Hesabınızın şifresi başarıyla değiştirilmiştir. Bu işlemi siz yapmadıysanız, lütfen hemen bizimle iletişime geçin.
                    </p>
                    <div style="background:#fef2f2;border:1px solid #fecaca;border-radius:8px;padding:14px 18px;margin:20px 0;">
                        <p style="color:#991b1b;font-size:13px;margin:0;">
                            <strong>⚠ Güvenlik Uyarısı:</strong> Bu şifre değişikliğini siz yapmadıysanız, hesabınız tehlikede olabilir.
                            Lütfen hemen şifrenizi tekrar değiştirin veya destek ekibimizle iletişime geçin.
                        </p>
                    </div>
                    <div style="text-align:center;margin:24px 0;">
                        <a href="%s/giris" style="background:linear-gradient(135deg,#2563eb,#1d4ed8);color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:600;font-size:14px;display:inline-block;">
                            Giriş Yap
                        </a>
                    </div>
                """.formatted(firstName, baseUrl)
                + buildFooter();
        sendHtml(toEmail, subject, html);
    }

    @Override
    @Async
    public void sendOrderConfirmation(String toEmail, String firstName, String orderNumber) {
        if (!enabled) {
            log.info("Email disabled — order confirmation for {}: {}", toEmail, orderNumber);
            return;
        }
        String subject = "Siparişiniz Alındı — " + orderNumber;
        String html = buildHeader("Sipariş Onaylandı")
                + """
                    <p style="color:#334155;font-size:15px;">Merhaba <strong>%s</strong>,</p>
                    <p style="color:#475569;font-size:14px;line-height:1.6;">
                        Siparişiniz başarıyla oluşturuldu. Aşağıda sipariş bilgilerinizi bulabilirsiniz:
                    </p>
                    <div style="background:#f0fdf4;border:1px solid #bbf7d0;border-radius:8px;padding:16px;margin:20px 0;text-align:center;">
                        <p style="color:#166534;font-size:13px;margin:0 0 4px;">Sipariş Numaranız</p>
                        <p style="color:#166534;font-size:20px;font-weight:700;margin:0;">%s</p>
                    </div>
                    <p style="color:#475569;font-size:14px;">Siparişinizin durumunu hesabınızdan takip edebilirsiniz.</p>
                """.formatted(firstName, orderNumber)
                + buildFooter();

        sendHtml(toEmail, subject, html);
    }

    @Override
    @Async
    public void sendOrderStatusUpdate(String toEmail, String firstName, String orderNumber, String newStatus, String note) {
        if (!enabled) {
            log.info("Email disabled — order status update for {}: {} → {}", toEmail, orderNumber, newStatus);
            return;
        }
        // Convert the English enum name to a Turkish label — the customer should see
        // "Kargoya Verildi" instead of "SHIPPED".
        String statusLabel = localizeOrderStatus(newStatus);
        String subject = "Sipariş Durumu Güncellendi — " + orderNumber;
        String noteHtml = (note != null && !note.isBlank())
                ? "<p style=\"color:#475569;font-size:13px;background:#f1f5f9;padding:12px;border-radius:6px;border-left:3px solid #2563eb;\"><strong>Not:</strong> " + escape(note) + "</p>"
                : "";
        String html = buildHeader("Sipariş Durumu Güncellendi")
                + """
                    <p style="color:#334155;font-size:15px;">Merhaba <strong>%s</strong>,</p>
                    <p style="color:#475569;font-size:14px;line-height:1.6;">
                        <strong>%s</strong> numaralı siparişinizin durumu güncellendi:
                    </p>
                    <div style="text-align:center;margin:24px 0;">
                        <div style="display:inline-block;background:linear-gradient(135deg,#2563eb,#1e40af);color:#fff;padding:12px 28px;border-radius:8px;font-weight:600;font-size:16px;">
                            %s
                        </div>
                    </div>
                    %s
                    <p style="color:#475569;font-size:14px;">Siparişinizin detaylarını hesabınızdan takip edebilirsiniz.</p>
                """.formatted(escape(firstName), escape(orderNumber), escape(statusLabel), noteHtml)
                + buildFooter();

        sendHtml(toEmail, subject, html);
    }

    @Override
    @Async
    public void sendReturnStatusUpdate(String toEmail, String firstName, String returnNumber,
                                       String orderNumber, String statusLabel, String message) {
        if (!enabled) {
            log.info("Email disabled — return status for {}: {} ({})", toEmail, returnNumber, statusLabel);
            return;
        }
        String subject = "İade Talebiniz — " + returnNumber;
        String orderLine = (orderNumber != null && !orderNumber.isBlank())
                ? "<p style=\"color:#64748b;font-size:13px;\">İlgili sipariş: <strong>" + escape(orderNumber) + "</strong></p>"
                : "";
        String html = buildHeader("İade Talebi Güncellemesi")
                + """
                    <p style="color:#334155;font-size:15px;">Merhaba <strong>%s</strong>,</p>
                    <div style="text-align:center;margin:24px 0;">
                        <div style="display:inline-block;background:linear-gradient(135deg,#7c3aed,#6d28d9);color:#fff;padding:12px 28px;border-radius:8px;font-weight:600;font-size:16px;">
                            %s
                        </div>
                    </div>
                    <p style="color:#475569;font-size:14px;line-height:1.6;">%s</p>
                    <p style="color:#64748b;font-size:13px;">İade talebi numaranız: <strong>%s</strong></p>
                    %s
                    <p style="color:#475569;font-size:14px;">İade sürecinizi hesabınızdan takip edebilirsiniz.</p>
                """.formatted(escape(firstName), escape(statusLabel), escape(message), escape(returnNumber), orderLine)
                + buildFooter();

        sendHtml(toEmail, subject, html);
    }

    // ─────────────────────────────────────────────────────────────
    //  E-Invoice notifications
    // ─────────────────────────────────────────────────────────────

    @Override
    @Async
    public void sendInvoiceReady(String toEmail, String firstName, String orderNumber,
                                  String invoiceNumber, String invoiceType, String totalFormatted) {
        if (!enabled) {
            log.info("Email disabled — invoice ready for {}: {}", toEmail, invoiceNumber);
            return;
        }
        String typeLabel = localizeInvoiceType(invoiceType);
        String safeName = escape(firstName != null ? firstName : "Müşterimiz");
        String subject = "E-Faturanız Hazır — " + orderNumber;
        String baseUrl = getBaseUrl();
        String downloadLink = baseUrl + "/siparislerim";

        String html = buildHeader("E-Faturanız Hazır")
                + """
                    <p style="color:#334155;font-size:15px;">Merhaba <strong>%s</strong>,</p>
                    <p style="color:#475569;font-size:14px;line-height:1.6;">
                        <strong>%s</strong> numaralı siparişinizin %s'ı başarıyla oluşturuldu ve GİB tarafından onaylandı.
                    </p>

                    <div style="background:#f0f9ff;border:1px solid #bae6fd;border-radius:8px;padding:20px;margin:20px 0;">
                        <table style="width:100%%;border-collapse:collapse;">
                            <tr>
                                <td style="padding:6px 0;color:#64748b;font-size:13px;">Fatura No</td>
                                <td style="padding:6px 0;color:#0f172a;font-size:14px;font-weight:600;text-align:right;">%s</td>
                            </tr>
                            <tr>
                                <td style="padding:6px 0;color:#64748b;font-size:13px;">Fatura Tipi</td>
                                <td style="padding:6px 0;color:#0f172a;font-size:14px;text-align:right;">%s</td>
                            </tr>
                            <tr>
                                <td style="padding:6px 0;color:#64748b;font-size:13px;">Toplam Tutar</td>
                                <td style="padding:6px 0;color:#0f172a;font-size:14px;font-weight:600;text-align:right;">%s</td>
                            </tr>
                        </table>
                    </div>

                    <div style="text-align:center;margin:28px 0;">
                        <a href="%s" style="display:inline-block;background:#2563eb;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:600;font-size:14px;">Faturayı Görüntüle / İndir</a>
                    </div>

                    <p style="color:#64748b;font-size:12px;line-height:1.5;">
                        Faturanız yasal olarak geçerlidir ve GİB sistemlerinde kayıtlıdır.
                        İstediğiniz zaman hesabınızdan PDF olarak indirebilirsiniz.
                    </p>
                """.formatted(safeName, escape(orderNumber), typeLabel,
                        escape(invoiceNumber != null ? invoiceNumber : "-"),
                        typeLabel,
                        escape(totalFormatted != null ? totalFormatted : "-"),
                        downloadLink)
                + buildFooter();

        sendHtml(toEmail, subject, html);
    }

    @Override
    @Async
    public void sendAdminInvoiceDigest(String toEmail,
                                        java.util.List<java.util.Map<String,Object>> errorRows,
                                        java.util.List<java.util.Map<String,Object>> stuckPendingRows) {
        if (!enabled) return;
        int errorCount = errorRows != null ? errorRows.size() : 0;
        int stuckCount = stuckPendingRows != null ? stuckPendingRows.size() : 0;
        if (errorCount == 0 && stuckCount == 0) return; // zero-noise

        String subject = "[E-Fatura Uyarısı] " + errorCount + " hata, " + stuckCount + " bekleyen";

        StringBuilder body = new StringBuilder();
        body.append(buildHeader("E-Fatura Günlük Özet"));
        body.append("<p style=\"color:#334155;font-size:15px;\">Aşağıdaki fatura(lar) yönetici dikkati bekliyor:</p>");

        if (errorCount > 0) {
            body.append("<h3 style=\"color:#dc2626;font-size:16px;margin-top:24px;\"><span style=\"font-size:18px;\">⚠</span> Hatalı Faturalar (").append(errorCount).append(")</h3>");
            body.append(renderDigestTable(errorRows, "#fef2f2", "#991b1b"));
        }

        if (stuckCount > 0) {
            body.append("<h3 style=\"color:#d97706;font-size:16px;margin-top:24px;\"><span style=\"font-size:18px;\">⏳</span> 24 Saatten Uzun Bekleyen (").append(stuckCount).append(")</h3>");
            body.append(renderDigestTable(stuckPendingRows, "#fffbeb", "#854d0e"));
        }

        String baseUrl = getBaseUrl();
        body.append("<div style=\"text-align:center;margin:28px 0;\">")
            .append("<a href=\"").append(baseUrl).append("/admin/invoices\" style=\"display:inline-block;background:#2563eb;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:600;font-size:14px;\">Admin Paneli'nde Aç</a>")
            .append("</div>");

        body.append(buildFooter());
        sendHtml(toEmail, subject, body.toString());
    }

    private String renderDigestTable(java.util.List<java.util.Map<String,Object>> rows, String bg, String fg) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table style=\"width:100%;border-collapse:collapse;background:").append(bg).append(";border-radius:6px;overflow:hidden;margin-top:8px;\">");
        sb.append("<tr style=\"color:").append(fg).append(";font-size:12px;text-align:left;\">")
          .append("<th style=\"padding:10px 12px;\">Fatura No</th>")
          .append("<th style=\"padding:10px 12px;\">Sipariş</th>")
          .append("<th style=\"padding:10px 12px;\">Alıcı</th>")
          .append("<th style=\"padding:10px 12px;\">Tutar</th>")
          .append("<th style=\"padding:10px 12px;\">Not</th>")
          .append("</tr>");
        for (var r : rows) {
            sb.append("<tr style=\"border-top:1px solid rgba(0,0,0,0.06);color:#0f172a;font-size:13px;\">");
            sb.append("<td style=\"padding:10px 12px;font-family:monospace;\">").append(escape(String.valueOf(r.getOrDefault("invoiceNumber", "-")))).append("</td>");
            sb.append("<td style=\"padding:10px 12px;\">").append(escape(String.valueOf(r.getOrDefault("orderNumber", "-")))).append("</td>");
            sb.append("<td style=\"padding:10px 12px;\">").append(escape(String.valueOf(r.getOrDefault("recipientName", "-")))).append("</td>");
            sb.append("<td style=\"padding:10px 12px;\">").append(escape(String.valueOf(r.getOrDefault("totalAmount", "-")))).append("</td>");
            sb.append("<td style=\"padding:10px 12px;font-size:11px;color:#64748b;\">").append(escape(String.valueOf(r.getOrDefault("errorMessage", "-")))).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String getBaseUrl() {
        try {
            String v = settingService.getSetting("seo_canonical_domain");
            if (v != null && !v.isBlank()) return v.trim().replaceAll("/$", "");
        } catch (Exception ignored) {}
        return "http://localhost:3000";
    }

    /** Invoice type label mapping for user-facing emails. */
    private static String localizeInvoiceType(String type) {
        if (type == null) return "e-Belge";
        return switch (type.toUpperCase()) {
            case "E_FATURA" -> "e-Faturası";
            case "E_ARSIV"  -> "e-Arşiv Faturası";
            default -> "e-Belgesi";
        };
    }

    // ─────────────────────────────────────────────────────────────
    //  Status → Turkish label (so enum names do not appear in the email)
    // ─────────────────────────────────────────────────────────────

    private static final java.util.Map<String, String> ORDER_STATUS_TR = java.util.Map.ofEntries(
            java.util.Map.entry("PENDING_PAYMENT",   "Ödeme Bekleniyor"),
            java.util.Map.entry("PAID",              "Ödeme Alındı"),
            java.util.Map.entry("PROCESSING",        "Hazırlanıyor"),
            java.util.Map.entry("READY_TO_SHIP",     "Kargoya Hazırlandı"),
            java.util.Map.entry("SHIPPED",           "Kargoya Verildi"),
            java.util.Map.entry("DELIVERED",         "Teslim Edildi"),
            java.util.Map.entry("CANCELLED",         "İptal Edildi"),
            java.util.Map.entry("CANCELED",          "İptal Edildi"),
            java.util.Map.entry("REFUNDED",          "İade Edildi"),
            java.util.Map.entry("RETURNED",          "İade Alındı"),
            java.util.Map.entry("FAILED",            "Başarısız"),
            java.util.Map.entry("BANK_TRANSFER_PENDING", "Havale Bekleniyor"),
            java.util.Map.entry("PAYMENT_PENDING",   "Ödeme Bekleniyor")
    );

    static String localizeOrderStatus(String status) {
        if (status == null || status.isBlank()) return "-";
        String up = status.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return ORDER_STATUS_TR.getOrDefault(up, status); // leave unknown values as-is
    }

    private void sendHtml(String to, String subject, String html) {
        sendHtml(to, subject, html, null);
    }

    private void sendHtml(String to, String subject, String html, String replyTo) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent to {} — subject: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }

    /** Escape a user-supplied string so it can be safely embedded in HTML. */
    private static String escape(String raw) {
        if (raw == null) return "";
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    @Override
    @Async
    public boolean sendContactFormMessage(String toEmail, String fromName, String fromEmail,
                                          String phone, String subject, String message) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Contact form received but no recipient configured (contact_form_email is empty)");
            return false;
        }
        if (!enabled) {
            log.info("Email disabled — contact form from {} <{}>: {}", fromName, fromEmail, subject);
            return false;
        }

        String safeName    = escape(fromName);
        String safeEmail   = escape(fromEmail);
        String safePhone   = escape(phone);
        String safeSubject = escape(subject);
        String safeMessage = escape(message).replace("\n", "<br/>");

        String phoneRow = (phone != null && !phone.isBlank())
                ? "<tr><td style=\"padding:8px 12px;color:#64748b;font-size:13px;width:120px;\">Telefon</td><td style=\"padding:8px 12px;font-size:14px;\">" + safePhone + "</td></tr>"
                : "";

        String html = buildHeader("Yeni İletişim Mesajı")
                + """
                    <p style="color:#334155;font-size:15px;">Web sitenizdeki iletişim formundan yeni bir mesaj geldi:</p>
                    <table style="width:100%%;border-collapse:collapse;background:#f8fafc;border-radius:8px;margin:20px 0;">
                        <tr><td style="padding:8px 12px;color:#64748b;font-size:13px;width:120px;">Ad Soyad</td><td style="padding:8px 12px;font-size:14px;"><strong>%s</strong></td></tr>
                        <tr><td style="padding:8px 12px;color:#64748b;font-size:13px;">E-posta</td><td style="padding:8px 12px;font-size:14px;"><a href="mailto:%s" style="color:#2563eb;text-decoration:none;">%s</a></td></tr>
                        %s
                        <tr><td style="padding:8px 12px;color:#64748b;font-size:13px;">Konu</td><td style="padding:8px 12px;font-size:14px;"><strong>%s</strong></td></tr>
                    </table>
                    <div style="background:#fff;border-left:3px solid #2563eb;padding:16px 20px;margin:20px 0;border-radius:4px;">
                        <p style="color:#334155;font-size:14px;line-height:1.7;margin:0;">%s</p>
                    </div>
                    <p style="color:#94a3b8;font-size:12px;">Bu mesaja yanıt vermek için "Yanıtla" butonunu kullanabilirsiniz — yanıtınız doğrudan gönderene (%s) iletilecektir.</p>
                """.formatted(safeName, safeEmail, safeEmail, phoneRow, safeSubject, safeMessage, safeEmail)
                + buildFooter();

        sendHtml(toEmail, "[İletişim Formu] " + subject, html, fromEmail);
        return true;
    }

    /** HTML attribute/content escape helper */
    private String escapeHtmlAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
