package com.warehouse.service.notification;

import com.warehouse.entity.Customer;
import com.warehouse.entity.NotificationPreference;
import com.warehouse.repository.NotificationPreferenceRepository;
import com.warehouse.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Notification dispatch service.
 * Decides which channel (SMS/EMAIL) to send a notification through based on the customer's preferences.
 *
 * Preference rules:
 * - If the customer has specified a preference for a channel, it is used
 * - Otherwise the defaults are: EMAIL = true, SMS = true (orders), SMS = false (marketing)
 * - For marketing, the KVKK marketingConsent is also checked
 */
@Service
public class NotificationDispatchService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificationPreferenceRepository preferenceRepository;
    private final SmsService smsService;
    private final EmailService emailService;

    public NotificationDispatchService(NotificationPreferenceRepository preferenceRepository,
                                        SmsService smsService,
                                        EmailService emailService) {
        this.preferenceRepository = preferenceRepository;
        this.smsService = smsService;
        this.emailService = emailService;
    }

    /**
     * Order created notification (SMS + email, based on customer preferences).
     */
    public void notifyOrderConfirmed(Customer customer, String orderNumber) {
        if (customer == null) return;

        // Email
        if (shouldSendViaChannel(customer, NotificationChannelType.EMAIL, NotificationType.ORDER_CONFIRMED, true)) {
            try {
                emailService.sendOrderConfirmation(customer.getEmail(), customer.getFirstName(), orderNumber);
            } catch (Exception e) {
                logger.warn("Sipariş onay e-postası gönderilemedi: {}", e.getMessage());
            }
        }

        // SMS
        if (shouldSendViaChannel(customer, NotificationChannelType.SMS, NotificationType.ORDER_CONFIRMED, true)
                && customer.getPhone() != null && !customer.getPhone().isBlank()) {
            String firstName = customer.getFirstName() != null ? customer.getFirstName() : "";
            String msg = String.format(
                "Sayin %s, %s numarali siparisiniz alindi. Detaylari mail adresinize gonderildi.",
                firstName, orderNumber
            );
            smsService.send(customer.getPhone(), msg);
        }
    }

    /**
     * Order status change notification (shipped, delivered, etc.).
     */
    public void notifyOrderStatusChange(Customer customer, String orderNumber,
                                         String newStatus, String cargoTrackingNo, String note) {
        if (customer == null) return;

        NotificationType type = resolveTypeForStatus(newStatus);

        // Email
        if (shouldSendViaChannel(customer, NotificationChannelType.EMAIL, type, true)) {
            try {
                emailService.sendOrderStatusUpdate(customer.getEmail(), customer.getFirstName(),
                        orderNumber, newStatus, note);
            } catch (Exception e) {
                logger.warn("Sipariş durum e-postası gönderilemedi: {}", e.getMessage());
            }
        }

        // SMS (only for important status changes)
        if (type == NotificationType.CARGO_SHIPPED || type == NotificationType.ORDER_DELIVERED) {
            if (shouldSendViaChannel(customer, NotificationChannelType.SMS, type, true)
                    && customer.getPhone() != null && !customer.getPhone().isBlank()) {
                String msg = buildStatusSms(orderNumber, type, cargoTrackingNo);
                smsService.send(customer.getPhone(), msg);
            }
        }
    }

    /**
     * Payment received notification.
     */
    public void notifyPaymentReceived(Customer customer, String orderNumber) {
        if (customer == null) return;
        if (shouldSendViaChannel(customer, NotificationChannelType.SMS, NotificationType.PAYMENT_RECEIVED, true)
                && customer.getPhone() != null && !customer.getPhone().isBlank()) {
            String msg = String.format(
                "%s numarali siparisinizin odemesi alindi. Hazirlik sureci baslamistir.",
                orderNumber
            );
            smsService.send(customer.getPhone(), msg);
        }
    }

    // === Helpers ===

    /**
     * Returns the customer's preferred setting for a channel+type combination.
     * Falls back to the default value if no record exists.
     */
    private boolean shouldSendViaChannel(Customer customer, NotificationChannelType channel,
                                          NotificationType type, boolean defaultEnabled) {
        // For marketing, KVKK consent is checked separately
        if (type == NotificationType.MARKETING && !customer.isMarketingConsent()) {
            return false;
        }

        try {
            Optional<NotificationPreference> pref = preferenceRepository
                    .findByCustomerIdAndChannelAndType(customer.getId(), channel, type);
            return pref.map(NotificationPreference::isEnabled).orElse(defaultEnabled);
        } catch (Exception e) {
            logger.debug("Tercih okuma hatası, default kullanılıyor: {}", e.getMessage());
            return defaultEnabled;
        }
    }

    private NotificationType resolveTypeForStatus(String status) {
        if (status == null) return NotificationType.ORDER_STATUS_CHANGE;
        return switch (status.toUpperCase()) {
            case "SHIPPED" -> NotificationType.CARGO_SHIPPED;
            case "DELIVERED" -> NotificationType.ORDER_DELIVERED;
            case "PAID" -> NotificationType.PAYMENT_RECEIVED;
            default -> NotificationType.ORDER_STATUS_CHANGE;
        };
    }

    private String buildStatusSms(String orderNumber, NotificationType type, String trackingNo) {
        return switch (type) {
            case CARGO_SHIPPED -> String.format(
                "%s numarali siparisiniz kargoya verildi.%s",
                orderNumber,
                (trackingNo != null && !trackingNo.isBlank()) ? " Takip No: " + trackingNo : ""
            );
            case ORDER_DELIVERED -> String.format(
                "%s numarali siparisiniz teslim edildi. Bizi tercih ettiginiz icin tesekkurler.",
                orderNumber
            );
            default -> String.format(
                "%s numarali siparisinizin durumu guncellendi.",
                orderNumber
            );
        };
    }
}
