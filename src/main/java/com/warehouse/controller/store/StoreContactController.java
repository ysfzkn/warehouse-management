package com.warehouse.controller.store;

import com.warehouse.dto.store.ContactFormRequest;
import com.warehouse.entity.ContactMessage;
import com.warehouse.enums.ContactMessageStatus;
import com.warehouse.repository.ContactMessageRepository;
import com.warehouse.service.EmailService;
import com.warehouse.service.SiteSettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public endpoint for the storefront contact form.
 * Messages are persisted to {@code contact_messages} and forwarded to the
 * address configured via the {@code contact_form_email} site setting.
 */
@RestController
@RequestMapping("/api/store/contact-messages")
public class StoreContactController {

    private static final Logger log = LoggerFactory.getLogger(StoreContactController.class);

    private final ContactMessageRepository contactMessageRepository;
    private final SiteSettingService siteSettingService;
    private final EmailService emailService;

    public StoreContactController(ContactMessageRepository contactMessageRepository,
                                  SiteSettingService siteSettingService,
                                  EmailService emailService) {
        this.contactMessageRepository = contactMessageRepository;
        this.siteSettingService = siteSettingService;
        this.emailService = emailService;
    }

    @PostMapping
    public ResponseEntity<?> submit(@Valid @RequestBody ContactFormRequest req, HttpServletRequest httpReq) {
        // Honeypot: silently accept (bot sees 200 OK) but drop the request entirely.
        if (req.getWebsite() != null && !req.getWebsite().isBlank()) {
            log.debug("Honeypot triggered on contact form from {}", clientIp(httpReq));
            return ResponseEntity.ok(Map.of("success", true));
        }

        String recipient = safeGet("contact_form_email");
        if (recipient == null || recipient.isBlank()) {
            log.warn("Contact form submitted but contact_form_email setting is empty");
            return ResponseEntity.status(503).body(Map.of(
                    "success", false,
                    "message", "İletişim formu şu an yapılandırılmamış. Lütfen daha sonra tekrar deneyin."));
        }

        // Persist first so the message is never lost even if mail fails
        ContactMessage entity = ContactMessage.builder()
                .name(req.getName().trim())
                .email(req.getEmail().trim())
                .phone(req.getPhone() != null ? req.getPhone().trim() : null)
                .subject(req.getSubject().trim())
                .message(req.getMessage().trim())
                .ipAddress(clientIp(httpReq))
                .userAgent(truncate(httpReq.getHeader("User-Agent"), 500))
                .status(ContactMessageStatus.NEW)
                .emailSent(false)
                .build();
        entity = contactMessageRepository.save(entity);

        // Forward via e-mail — failures are logged but do not fail the request
        try {
            boolean sent = emailService.sendContactFormMessage(
                    recipient,
                    entity.getName(),
                    entity.getEmail(),
                    entity.getPhone(),
                    entity.getSubject(),
                    entity.getMessage());
            if (sent) {
                entity.setEmailSent(true);
                contactMessageRepository.save(entity);
            }
        } catch (Exception e) {
            log.error("Failed to forward contact form message id={}: {}", entity.getId(), e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Mesajınız alındı. En kısa sürede size dönüş yapacağız."));
    }

    private String safeGet(String key) {
        try { return siteSettingService.getSetting(key); } catch (Exception e) { return null; }
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
