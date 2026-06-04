package com.warehouse.job;

import com.warehouse.entity.Cart;
import com.warehouse.entity.CartItem;
import com.warehouse.entity.Customer;
import com.warehouse.entity.Product;
import com.warehouse.repository.CartRepository;
import com.warehouse.repository.CartItemRepository;
import com.warehouse.service.EmailService;
import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Hourly scheduled job for abandoned cart recovery.
 *
 * Sends a reminder email to carts that meet the following conditions:
 * - Belongs to a registered customer (not a guest)
 * - Contains at least one product
 * - Not updated by the user for a given period (default: 2 hours)
 * - Not too old (default: newer than 72 hours)
 * - No reminder has been sent before
 */
@Component
public class AbandonedCartRecoveryJob {

    private static final Logger logger = LoggerFactory.getLogger(AbandonedCartRecoveryJob.class);
    private static final NumberFormat TRY_FORMAT = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("tr-TR"));

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final EmailService emailService;
    private final SiteSettingService settingService;

    public AbandonedCartRecoveryJob(CartRepository cartRepository,
                                     CartItemRepository cartItemRepository,
                                     EmailService emailService,
                                     SiteSettingService settingService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.emailService = emailService;
        this.settingService = settingService;
    }

    /**
     * Runs once an hour (3600000ms). First run is 5 minutes after application startup.
     */
    @Scheduled(fixedRate = 3_600_000, initialDelay = 300_000)
    @SchedulerLock(name = "abandonedCartRecovery", lockAtMostFor = "PT10M", lockAtLeastFor = "PT5M")
    @Transactional
    public void processAbandonedCarts() {
        // Feature flag
        String enabled = settingService.getSetting("abandoned_cart_enabled");
        if (!"true".equalsIgnoreCase(enabled)) {
            return;
        }

        // Config (default: 2 hours delay, 72 hours window)
        int delayHours = parseIntSetting("abandoned_cart_delay_hours", 2);
        int windowHours = parseIntSetting("abandoned_cart_reminder_window_hours", 72);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffTime = now.minusHours(delayHours);
        LocalDateTime minRecentTime = now.minusHours(windowHours);

        List<Cart> abandonedCarts = cartRepository.findAbandonedCarts(cutoffTime, minRecentTime);

        if (abandonedCarts.isEmpty()) {
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (Cart cart : abandonedCarts) {
            try {
                if (sendReminderForCart(cart)) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                failCount++;
                logger.error("Terk edilmiş sepet hatırlatma hatası (cartId={}): {}", cart.getId(), e.getMessage(), e);
            }
        }

        logger.info("Terk edilmiş sepet job: {} hatırlatma gönderildi, {} başarısız ({} aday)",
                successCount, failCount, abandonedCarts.size());
    }

    private boolean sendReminderForCart(Cart cart) {
        Customer customer = cart.getCustomer();
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            markAsSent(cart); // Invalid - do not process again
            return false;
        }

        // Marketing consent check
        if (!customer.isMarketingConsent()) {
            logger.debug("Müşteri marketing consent vermemiş, hatırlatma gönderilmiyor: {}", customer.getEmail());
            markAsSent(cart);
            return false;
        }

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        if (items.isEmpty()) {
            markAsSent(cart);
            return false;
        }

        // Cart total and item HTML
        BigDecimal subtotal = BigDecimal.ZERO;
        StringBuilder itemsHtml = new StringBuilder();
        itemsHtml.append("<div style=\"border:1px solid #e2e8f0;border-radius:8px;overflow:hidden;margin:16px 0;\">");

        int totalQty = 0;
        for (CartItem item : items) {
            Product p = item.getProduct();
            if (p == null) continue;
            BigDecimal unitPrice = p.getSalePrice() != null && p.getSalePrice().compareTo(BigDecimal.ZERO) > 0
                    ? p.getSalePrice() : p.getPrice();
            if (unitPrice == null) unitPrice = BigDecimal.ZERO;
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            subtotal = subtotal.add(lineTotal);
            totalQty += item.getQuantity();

            itemsHtml.append(String.format(
                "<div style=\"display:flex;align-items:center;gap:12px;padding:12px 16px;border-bottom:1px solid #f1f5f9;\">" +
                    "<div style=\"flex:1;\">" +
                        "<div style=\"color:#0f172a;font-size:14px;font-weight:600;margin-bottom:4px;\">%s</div>" +
                        "<div style=\"color:#64748b;font-size:12px;\">%d adet × %s</div>" +
                    "</div>" +
                    "<div style=\"color:#0f172a;font-size:14px;font-weight:700;\">%s</div>" +
                "</div>",
                escapeHtml(p.getName()),
                item.getQuantity(),
                formatPrice(unitPrice),
                formatPrice(lineTotal)
            ));
        }
        itemsHtml.append("</div>");

        // Send the email
        emailService.sendAbandonedCartReminder(
                customer.getEmail(),
                customer.getFirstName() != null ? customer.getFirstName() : "Müşterimiz",
                totalQty,
                itemsHtml.toString(),
                formatPrice(subtotal)
        );

        markAsSent(cart);
        logger.info("Terk edilmiş sepet hatırlatma gönderildi: cartId={}, email={}, {} ürün",
                cart.getId(), customer.getEmail(), totalQty);
        return true;
    }

    private void markAsSent(Cart cart) {
        cart.setAbandonedCartReminderSentAt(LocalDateTime.now());
        cartRepository.save(cart);
    }

    private int parseIntSetting(String key, int defaultValue) {
        try {
            String val = settingService.getSetting(key);
            if (val == null || val.isBlank()) return defaultValue;
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) price = BigDecimal.ZERO;
        try {
            return TRY_FORMAT.format(price);
        } catch (Exception e) {
            return price.toString() + " TL";
        }
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
