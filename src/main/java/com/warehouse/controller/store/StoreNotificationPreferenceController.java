package com.warehouse.controller.store;

import com.warehouse.entity.Customer;
import com.warehouse.entity.NotificationPreference;
import com.warehouse.repository.CustomerRepository;
import com.warehouse.repository.NotificationPreferenceRepository;
import com.warehouse.security.JwtService;
import com.warehouse.service.notification.NotificationChannelType;
import com.warehouse.service.notification.NotificationType;
import com.warehouse.util.CustomerTokenExtractor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Müşteri bildirim tercihleri endpoint'leri.
 * Müşteriler hangi kanal (SMS/EMAIL) için hangi bildirim tipini
 * (sipariş, kargo, pazarlama vb.) almak istediğini yönetir.
 */
@RestController
@RequestMapping("/api/store/account/notification-preferences")
@RequiredArgsConstructor
public class StoreNotificationPreferenceController {

    private final NotificationPreferenceRepository preferenceRepository;
    private final CustomerRepository customerRepository;
    private final JwtService jwtService;

    /**
     * Müşterinin tüm bildirim tercihlerini döner.
     * Her [kanal x tip] için mevcut değer veya default (true) döner.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getPreferences(HttpServletRequest request) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        if (customerId == null) return ResponseEntity.status(401).body(Map.of("error", "Giriş yapmanız gerekiyor."));

        List<NotificationPreference> prefs = preferenceRepository.findByCustomerId(customerId);

        // Kanal -> tip -> enabled mapping'i oluştur
        Map<String, Map<String, Boolean>> result = new LinkedHashMap<>();
        for (NotificationChannelType channel : NotificationChannelType.values()) {
            Map<String, Boolean> channelMap = new LinkedHashMap<>();
            for (NotificationType type : NotificationType.values()) {
                // Default: marketing = false, diğerleri = true
                boolean defaultValue = type != NotificationType.MARKETING;
                Optional<NotificationPreference> pref = prefs.stream()
                        .filter(p -> p.getChannel() == channel && p.getType() == type)
                        .findFirst();
                channelMap.put(type.name(), pref.map(NotificationPreference::isEnabled).orElse(defaultValue));
            }
            result.put(channel.name(), channelMap);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Tercihleri günceller. Body yapısı:
     * {
     *   "EMAIL": { "ORDER_CONFIRMED": true, "MARKETING": false },
     *   "SMS": { "CARGO_SHIPPED": true }
     * }
     */
    @PutMapping
    @Transactional
    public ResponseEntity<?> updatePreferences(HttpServletRequest request,
                                                @RequestBody Map<String, Map<String, Boolean>> body) {
        Long customerId = CustomerTokenExtractor.extractCustomerId(request, jwtService);
        if (customerId == null) return ResponseEntity.status(401).body(Map.of("error", "Giriş yapmanız gerekiyor."));

        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) return ResponseEntity.status(404).body(Map.of("error", "Müşteri bulunamadı."));

        for (Map.Entry<String, Map<String, Boolean>> channelEntry : body.entrySet()) {
            NotificationChannelType channel;
            try { channel = NotificationChannelType.valueOf(channelEntry.getKey()); }
            catch (IllegalArgumentException e) { continue; }

            for (Map.Entry<String, Boolean> typeEntry : channelEntry.getValue().entrySet()) {
                NotificationType type;
                try { type = NotificationType.valueOf(typeEntry.getKey()); }
                catch (IllegalArgumentException e) { continue; }

                boolean enabled = Boolean.TRUE.equals(typeEntry.getValue());

                // Upsert
                NotificationPreference pref = preferenceRepository
                        .findByCustomerIdAndChannelAndType(customerId, channel, type)
                        .orElseGet(() -> {
                            NotificationPreference p = NotificationPreference.builder()
                                    .customer(customer)
                                    .channel(channel)
                                    .type(type)
                                    .enabled(enabled)
                                    .build();
                            return p;
                        });
                pref.setEnabled(enabled);
                preferenceRepository.save(pref);
            }
        }

        return ResponseEntity.ok(Map.of("message", "Bildirim tercihleri güncellendi."));
    }
}
