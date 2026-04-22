package com.warehouse.service.cargo;

import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;
import com.warehouse.entity.Product;
import com.warehouse.repository.OrderItemRepository;
import com.warehouse.repository.OrderRepository;
import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Kargo API üst seviye servisi.
 *
 * Aktif CargoApiProvider'ı bulur ve siparişlere dayalı olarak:
 * - Kargo gönderimi oluşturur (ship order)
 * - Takip durumunu sorgular
 * - Gönderimi iptal eder
 *
 * Site ayarından 'cargo_api_enabled' false ise hiçbir işlem yapmaz.
 */
@Service
public class CargoApiService {

    private static final Logger logger = LoggerFactory.getLogger(CargoApiService.class);

    private final List<CargoApiProvider> providers;
    private final SiteSettingService settingService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public CargoApiService(List<CargoApiProvider> providers,
                            SiteSettingService settingService,
                            OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository) {
        this.providers = providers;
        this.settingService = settingService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * Kargo API entegrasyonu aktif mi?
     */
    public boolean isEnabled() {
        return "true".equalsIgnoreCase(settingService.getSetting("cargo_api_enabled"));
    }

    /**
     * Sipariş oluşturulduğunda otomatik olarak kargo gönderimi oluşturulsun mu?
     */
    public boolean isAutoCreateEnabled() {
        return isEnabled() && "true".equalsIgnoreCase(settingService.getSetting("cargo_api_auto_create"));
    }

    /**
     * Sipariş için kargo gönderimi oluşturur ve Order'a tracking bilgilerini kaydeder.
     *
     * @return oluşturulan gönderim sonucu; API aktif değilse null
     */
    @Transactional
    public CargoShipmentResult createShipmentForOrder(Order order) {
        if (!isEnabled()) {
            logger.debug("Cargo API disabled, skipping shipment creation for order {}", order.getOrderNumber());
            return null;
        }

        CargoApiProvider provider = getActiveProvider();
        if (provider == null) {
            logger.warn("No active cargo API provider for order {}", order.getOrderNumber());
            return null;
        }

        try {
            CargoShipmentRequest request = buildShipmentRequest(order);
            CargoShipmentResult result = provider.createShipment(request);

            if (result.isSuccess()) {
                order.setCargoTrackingNo(result.getTrackingNumber());
                order.setCargoProviderShipmentId(result.getProviderShipmentId());
                order.setCargoLabelUrl(result.getLabelUrl());
                if (result.getCarrierName() != null) {
                    order.setCargoProviderName(result.getCarrierName());
                }
                orderRepository.save(order);

                logger.info("Cargo shipment created: order={}, tracking={}, provider={}",
                        order.getOrderNumber(), result.getTrackingNumber(), provider.getProviderName());
            } else {
                logger.error("Cargo shipment creation failed: order={}, error={}",
                        order.getOrderNumber(), result.getErrorMessage());
            }
            return result;
        } catch (Exception e) {
            logger.error("Cargo shipment exception for order {}: {}", order.getOrderNumber(), e.getMessage(), e);
            return CargoShipmentResult.failure("EXCEPTION", e.getMessage());
        }
    }

    /**
     * Sipariş için kargo takip durumunu sorgular ve Order'ı günceller.
     * Teslim edilmişse actualDeliveryDate set edilir.
     */
    @Transactional
    public CargoTrackingStatus trackOrder(Order order) {
        if (!isEnabled() || order.getCargoTrackingNo() == null) return null;

        CargoApiProvider provider = getActiveProvider();
        if (provider == null) return null;

        CargoTrackingStatus status = provider.getTrackingStatus(order.getCargoTrackingNo());
        if (status != null) {
            order.setCargoLastTrackedAt(LocalDateTime.now());
            if (status.getStatus() == CargoTrackingStatus.CargoStatus.DELIVERED
                    && order.getActualDeliveryDate() == null
                    && status.getDeliveredAt() != null) {
                order.setActualDeliveryDate(status.getDeliveredAt().toLocalDate());
            }
            orderRepository.save(order);
        }
        return status;
    }

    /**
     * Kargo gönderimini iptal eder.
     */
    @Transactional
    public CargoShipmentResult cancelShipment(Order order) {
        if (!isEnabled() || order.getCargoProviderShipmentId() == null) return null;

        CargoApiProvider provider = getActiveProvider();
        if (provider == null) return null;

        CargoShipmentResult result = provider.cancelShipment(order.getCargoProviderShipmentId());
        if (result.isSuccess()) {
            logger.info("Cargo shipment cancelled: order={}", order.getOrderNumber());
        }
        return result;
    }

    /**
     * Order'ın kargo etiketini (PDF) indirir. Yalnızca Kargonomi gibi etiket indirmeyi
     * destekleyen sağlayıcılar için çalışır.
     *
     * @return PDF byte[], yoksa boş array
     */
    public byte[] downloadShipmentLabel(Order order) {
        if (!isEnabled() || order.getCargoProviderShipmentId() == null) return new byte[0];
        CargoApiProvider provider = getActiveProvider();
        if (provider instanceof KargonomiCargoProvider k) {
            return k.downloadBarcodePdf(order.getCargoProviderShipmentId());
        }
        if (provider instanceof MockCargoProvider m) {
            return m.downloadLabelPdf(order.getCargoProviderShipmentId());
        }
        return new byte[0];
    }

    /** Aktif sağlayıcının hesap bakiyesi. Destek yoksa {@code null}. */
    public BigDecimal getProviderBalance() {
        if (!isEnabled()) return null;
        CargoApiProvider provider = getActiveProvider();
        if (provider instanceof KargonomiCargoProvider k) {
            return k.getBalance();
        }
        return null;
    }

    /**
     * Webhook ya da polling'den gelen tracking update'ini Order'a uygular.
     * @return true ise Order güncellendi, false ise değişiklik yok
     */
    @Transactional
    public boolean applyTrackingUpdate(Order order, CargoTrackingStatus status) {
        if (order == null || status == null) return false;
        boolean changed = false;
        order.setCargoLastTrackedAt(LocalDateTime.now());

        if (status.getStatus() == CargoTrackingStatus.CargoStatus.DELIVERED
                && status.getDeliveredAt() != null
                && order.getActualDeliveryDate() == null) {
            order.setActualDeliveryDate(status.getDeliveredAt().toLocalDate());
            changed = true;
        }
        // Tracking code ilk kez geldiyse kaydet
        if ((order.getCargoTrackingNo() == null || order.getCargoTrackingNo().isBlank())
                && status.getTrackingNumber() != null) {
            order.setCargoTrackingNo(status.getTrackingNumber());
            changed = true;
        }
        if (changed) orderRepository.save(order);
        return changed;
    }

    // === Private helpers ===

    /**
     * Aktif (isEnabled()==true) sağlayıcıyı bulur.
     */
    public CargoApiProvider getActiveProvider() {
        return providers.stream()
                .filter(CargoApiProvider::isEnabled)
                .findFirst()
                .orElse(null);
    }

    /**
     * Order'dan CargoShipmentRequest oluşturur.
     */
    private CargoShipmentRequest buildShipmentRequest(Order order) {
        Map<String, Object> shippingAddr = order.getShippingAddressSnapshot();

        // Gönderici bilgileri site_settings'tan
        String senderName = settingService.getSetting("sender_name");
        String senderPhone = settingService.getSetting("sender_phone");
        String senderAddress = settingService.getSetting("sender_address");
        String senderCity = settingService.getSetting("sender_city");
        String senderDistrict = settingService.getSetting("sender_district");
        String senderPostalCode = settingService.getSetting("sender_postal_code");

        if (senderName == null || senderName.isBlank()) {
            senderName = settingService.getSetting("site_name");
        }

        // Sipariş kalemleri
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        List<CargoShipmentRequest.ShipmentItem> items = orderItems.stream()
                .map(oi -> {
                    Product p = oi.getProduct();
                    return CargoShipmentRequest.ShipmentItem.builder()
                            .productName(p != null ? p.getName() : "-")
                            .sku(p != null ? p.getSku() : null)
                            .quantity(oi.getQuantity())
                            .unitPrice(oi.getUnitPrice())
                            .build();
                })
                .toList();

        // Kapıda ödeme kontrolü
        boolean isCod = "DOOR_CASH".equals(order.getPaymentMethod()) || "DOOR_CARD".equals(order.getPaymentMethod());
        BigDecimal codAmount = isCod ? order.getGrandTotal() : null;

        // Toplam ağırlık ve desi hesapla (ürün boyutlarından)
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalDesi = BigDecimal.ZERO;
        int totalPackages = 0;
        for (OrderItem oi : orderItems) {
            Product p = oi.getProduct();
            if (p == null) continue;
            BigDecimal weight = p.getWeight() != null ? BigDecimal.valueOf(p.getWeight()) : BigDecimal.ZERO;
            BigDecimal volumetric = BigDecimal.ZERO;
            if (p.getLengthCm() != null && p.getWidthCm() != null && p.getHeightCm() != null) {
                volumetric = BigDecimal.valueOf(p.getLengthCm())
                        .multiply(BigDecimal.valueOf(p.getWidthCm()))
                        .multiply(BigDecimal.valueOf(p.getHeightCm()))
                        .divide(new BigDecimal("3000"), 2, java.math.RoundingMode.HALF_UP);
            }
            BigDecimal desi = weight.max(volumetric);
            totalWeight = totalWeight.add(weight.multiply(BigDecimal.valueOf(oi.getQuantity())));
            totalDesi = totalDesi.add(desi.multiply(BigDecimal.valueOf(oi.getQuantity())));
            totalPackages += oi.getQuantity();
        }

        // Kargonomi slug'ı: cargo_company veya cargoProviderName
        String carrierSlug = null;
        if (order.getCargoCompany() != null) {
            carrierSlug = order.getCargoCompany().name().toLowerCase();
        }

        return CargoShipmentRequest.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .recipientName(strFromMap(shippingAddr, "firstName") + " " + strFromMap(shippingAddr, "lastName"))
                .recipientPhone(strFromMap(shippingAddr, "phone"))
                .recipientEmail(order.getCustomer() != null ? order.getCustomer().getEmail() : null)
                .recipientAddress(strFromMap(shippingAddr, "addressLine"))
                .recipientCity(strFromMap(shippingAddr, "city"))
                .recipientDistrict(strFromMap(shippingAddr, "district"))
                .recipientPostalCode(strFromMap(shippingAddr, "postalCode"))
                .recipientCountryCode("TR")
                .senderName(senderName)
                .senderPhone(senderPhone)
                .senderAddress(senderAddress)
                .senderCity(senderCity)
                .senderDistrict(senderDistrict)
                .senderPostalCode(senderPostalCode)
                .packageCount(Math.max(totalPackages, 1))
                .totalWeightKg(totalWeight)
                .totalDesi(totalDesi)
                .contentDescription("Sipariş #" + order.getOrderNumber())
                .orderAmount(order.getGrandTotal())
                .cashOnDelivery(isCod)
                .cashOnDeliveryAmount(codAmount)
                .preferredCarrier(carrierSlug)
                .deliveryNote(order.getCustomerNote())
                .items(items)
                .build();
    }

    private String strFromMap(Map<String, Object> map, String key) {
        if (map == null) return "";
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }
}
