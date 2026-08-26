package com.warehouse.service.impl;

import com.warehouse.entity.Customer;
import com.warehouse.entity.Product;
import com.warehouse.entity.Stock;
import com.warehouse.entity.StockNotificationSubscription;
import com.warehouse.repository.CustomerRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.StockNotificationSubscriptionRepository;
import com.warehouse.service.EmailService;
import com.warehouse.service.StockNotificationService;
import com.warehouse.service.StockService;
import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class StockNotificationServiceImpl implements StockNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(StockNotificationServiceImpl.class);
    private static final NumberFormat TRY_FORMAT = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("tr-TR"));

    private final StockNotificationSubscriptionRepository subscriptionRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final StockService stockService;
    private final EmailService emailService;
    private final SiteSettingService settingService;

    public StockNotificationServiceImpl(StockNotificationSubscriptionRepository subscriptionRepository,
                                         ProductRepository productRepository,
                                         CustomerRepository customerRepository,
                                         StockService stockService,
                                         EmailService emailService,
                                         SiteSettingService settingService) {
        this.subscriptionRepository = subscriptionRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.stockService = stockService;
        this.emailService = emailService;
        this.settingService = settingService;
    }

    @Override
    @Transactional
    public boolean subscribe(Long productId, String email, Long customerId) {
        if (productId == null || email == null || email.isBlank()) {
            throw new IllegalArgumentException("productId ve email zorunludur");
        }
        String normalizedEmail = email.trim().toLowerCase();

        // Idempotent: if a pending subscription already exists, don't add another
        if (subscriptionRepository.existsByProductIdAndEmailAndNotifiedFalse(productId, normalizedEmail)) {
            logger.debug("Stok bildirimi zaten var: productId={}, email={}", productId, normalizedEmail);
            return false;
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Ürün bulunamadı: " + productId));

        StockNotificationSubscription.StockNotificationSubscriptionBuilder builder =
                StockNotificationSubscription.builder()
                        .product(product)
                        .email(normalizedEmail)
                        .notified(false);

        if (customerId != null) {
            customerRepository.findById(customerId).ifPresent(builder::customer);
        }

        subscriptionRepository.save(builder.build());
        logger.info("Stok bildirim aboneliği oluşturuldu: productId={}, email={}", productId, normalizedEmail);
        return true;
    }

    @Override
    @Transactional
    public int checkAndNotifyProduct(Long productId) {
        List<StockNotificationSubscription> pending = subscriptionRepository.findByProductIdAndNotifiedFalse(productId);
        if (pending.isEmpty()) return 0;

        // Calculate the product's total sellable stock
        int available = totalAvailableQuantity(productId);
        if (available <= 0) return 0;

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || !product.isActive()) return 0;

        String productUrl = buildProductUrl(product);
        String imageUrl = buildImageUrl(product);
        String priceStr = formatPrice(product.getSalePrice() != null && product.getSalePrice().compareTo(BigDecimal.ZERO) > 0
                ? product.getSalePrice() : product.getPrice());

        int notifiedCount = 0;
        for (StockNotificationSubscription sub : pending) {
            try {
                emailService.sendBackInStockNotification(
                        sub.getEmail(), product.getName(), productUrl, imageUrl, priceStr);

                sub.setNotified(true);
                sub.setNotifiedAt(LocalDateTime.now());
                subscriptionRepository.save(sub);
                notifiedCount++;
            } catch (Exception e) {
                logger.warn("Back-in-stock bildirim hatası (subId={}): {}", sub.getId(), e.getMessage());
            }
        }

        if (notifiedCount > 0) {
            logger.info("Back-in-stock bildirim gönderildi: productId={}, count={}/{}",
                    productId, notifiedCount, pending.size());
        }
        return notifiedCount;
    }

    @Override
    @Transactional
    public int processPendingSubscriptions() {
        List<Long> productIds = subscriptionRepository.findProductIdsWithPendingSubscriptions();
        if (productIds.isEmpty()) return 0;

        int total = 0;
        for (Long productId : productIds) {
            try {
                total += checkAndNotifyProduct(productId);
            } catch (Exception e) {
                logger.warn("Back-in-stock kontrol hatası (productId={}): {}", productId, e.getMessage());
            }
        }
        if (total > 0) {
            logger.info("Back-in-stock job: {} bildirim gönderildi ({} ürün kontrol edildi)", total, productIds.size());
        }
        return total;
    }

    // === Private helpers ===

    private int totalAvailableQuantity(Long productId) {
        try {
            List<Stock> stocks = stockService.getStocksByProduct(productId);
            return stocks.stream().mapToInt(Stock::getAvailableQuantity).sum();
        } catch (Exception e) {
            logger.warn("Stok miktarı okunamadı (productId={}): {}", productId, e.getMessage());
            return 0;
        }
    }

    private String buildProductUrl(Product product) {
        String baseUrl = settingService.getSetting("seo_canonical_domain");
        if (baseUrl == null || baseUrl.isBlank()) {
            // Use app.base-url as a fallback if needed, but it's not in SiteSetting; this is the best fallback for the frontend domain
            baseUrl = settingService.getSetting("app_base_url");
        }
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "";
        String cleanBase = baseUrl.replaceAll("/+$", "");
        return cleanBase + "/urun/" + product.getSlug();
    }

    private String buildImageUrl(Product product) {
        try {
            if (product.getImages() != null && !product.getImages().isEmpty()) {
                var primary = com.warehouse.util.ProductImageUtil.displayCover(product.getImages())
                        .orElse(null);
                if (primary != null) {
                    // Image API URL
                    String baseUrl = settingService.getSetting("seo_canonical_domain");
                    String cleanBase = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "";
                    return cleanBase + "/api/admin/products/images/" + primary.getId() + "/view";
                }
            }
        } catch (Exception e) {
            logger.debug("Ürün görsel URL'i oluşturulamadı: {}", e.toString());
        }
        return null;
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) return "";
        try {
            return TRY_FORMAT.format(price);
        } catch (Exception e) {
            return price.toString() + " TL";
        }
    }
}
