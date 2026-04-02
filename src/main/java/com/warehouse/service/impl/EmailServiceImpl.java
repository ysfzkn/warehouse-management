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
        String verifyUrl = baseUrl + "/store/hesap-dogrula?token=" + verificationToken;
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
        String resetUrl = baseUrl + "/store/sifre-sifirla?token=" + resetToken;
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
        String subject = "Sipariş Durumu Güncellendi — " + orderNumber;
        String noteHtml = (note != null && !note.isBlank())
                ? "<p style=\"color:#475569;font-size:13px;background:#f1f5f9;padding:12px;border-radius:6px;border-left:3px solid #2563eb;\"><strong>Not:</strong> " + note + "</p>"
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
                """.formatted(firstName, orderNumber, newStatus, noteHtml)
                + buildFooter();

        sendHtml(toEmail, subject, html);
    }

    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent to {} — subject: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }
}
