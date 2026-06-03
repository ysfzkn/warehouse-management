package com.warehouse.service.cargo;

import com.warehouse.entity.CargoProvider;
import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;
import com.warehouse.entity.Product;
import com.warehouse.repository.CargoProviderRepository;
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
 * High-level cargo API service.
 *
 * Finds the active CargoApiProvider and, based on orders:
 * - Creates a cargo shipment (ship order)
 * - Queries the tracking status
 * - Cancels a shipment
 *
 * Does nothing if the 'cargo_api_enabled' site setting is false.
 */
@Service
public class CargoApiService {

    private static final Logger logger = LoggerFactory.getLogger(CargoApiService.class);

    private final List<CargoApiProvider> providers;
    private final SiteSettingService settingService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CargoProviderRepository cargoProviderRepository;

    public CargoApiService(List<CargoApiProvider> providers,
                            SiteSettingService settingService,
                            OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            CargoProviderRepository cargoProviderRepository) {
        this.providers = providers;
        this.settingService = settingService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cargoProviderRepository = cargoProviderRepository;
    }

    /**
     * Is the cargo API integration enabled?
     */
    public boolean isEnabled() {
        return "true".equalsIgnoreCase(settingService.getSetting("cargo_api_enabled"));
    }

    /**
     * Should a cargo shipment be created automatically when an order is placed?
     */
    public boolean isAutoCreateEnabled() {
        return isEnabled() && "true".equalsIgnoreCase(settingService.getSetting("cargo_api_auto_create"));
    }

    /**
     * Creates a cargo shipment for the order and saves the tracking info to the Order.
     *
     * @return the created shipment result; null if the API is not enabled
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

                // Important: order.cargoCompany (chosen by the customer at checkout) is NOT touched.
                // cargoProviderName is display-only — the carrier name returned by Kargonomi
                // (usually the same as the customer's choice; if different, "auto-cheapest" was selected).
                if (result.getCarrierName() != null) {
                    order.setCargoProviderName(result.getCarrierName());
                }
                orderRepository.save(order);

                logger.info("Cargo shipment created: order={}, tracking={}, chosenCarrier={}, provider={}",
                        order.getOrderNumber(), result.getTrackingNumber(),
                        result.getCarrierName(), provider.getProviderName());
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
     * Queries the cargo tracking status for the order and updates the Order.
     * Sets actualDeliveryDate if it has been delivered.
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
     * Cancels the cargo shipment.
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
     * Downloads the order's cargo label (PDF). Works only for providers that
     * support label download, such as Kargonomi.
     *
     * @return PDF byte[], or an empty array if none
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

    /** The active provider's account balance. {@code null} if unsupported. */
    public BigDecimal getProviderBalance() {
        if (!isEnabled()) return null;
        CargoApiProvider provider = getActiveProvider();
        if (provider instanceof KargonomiCargoProvider k) {
            return k.getBalance();
        }
        return null;
    }

    /**
     * Applies a tracking update from a webhook or polling to the Order.
     * @return true if the Order was updated, false if there was no change
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
        // Save the tracking code if it arrived for the first time
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
     * Finds the Kargonomi slug for the {@code Order.cargoCompany} chosen by the customer.
     * <ol>
     *   <li>cargo_providers.kargonomi_slug (explicit DB mapping) — ideal</li>
     *   <li>The lowercase enum name (YURTICI → "yurtici") — fallback</li>
     *   <li>null → Kargonomi automatically picks the cheapest</li>
     * </ol>
     */
    private String resolveKargonomiSlug(Order order) {
        if (order.getCargoCompany() == null) return null;
        String code = order.getCargoCompany().name();

        // 1) explicit slug from cargo_providers
        var provider = cargoProviderRepository.findByCode(code).orElse(null);
        if (provider != null && provider.getKargonomiSlug() != null
                && !provider.getKargonomiSlug().isBlank()) {
            return provider.getKargonomiSlug().trim().toLowerCase();
        }

        // 2) Fallback: lowercase enum name
        return code.toLowerCase();
    }

    /**
     * Finds the active (isEnabled()==true) provider.
     */
    public CargoApiProvider getActiveProvider() {
        return providers.stream()
                .filter(CargoApiProvider::isEnabled)
                .findFirst()
                .orElse(null);
    }

    /**
     * Builds a CargoShipmentRequest from the Order.
     */
    private CargoShipmentRequest buildShipmentRequest(Order order) {
        Map<String, Object> shippingAddr = order.getShippingAddressSnapshot();

        // Sender information from site_settings
        String senderName = settingService.getSetting("sender_name");
        String senderPhone = settingService.getSetting("sender_phone");
        String senderAddress = settingService.getSetting("sender_address");
        String senderCity = settingService.getSetting("sender_city");
        String senderDistrict = settingService.getSetting("sender_district");
        String senderPostalCode = settingService.getSetting("sender_postal_code");

        if (senderName == null || senderName.isBlank()) {
            senderName = settingService.getSetting("site_name");
        }

        // Order items
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

        // Cash-on-delivery check
        boolean isCod = "DOOR_CASH".equals(order.getPaymentMethod()) || "DOOR_CARD".equals(order.getPaymentMethod());
        BigDecimal codAmount = isCod ? order.getGrandTotal() : null;

        // Compute total weight and desi (from product dimensions)
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

        // Kargonomi slug — the Kargonomi carrier slug corresponding to the cargo company
        // the customer chose at checkout. Pulled from cargo_providers.kargonomi_slug (explicit mapping).
        // If there is no match, fall back to the lowercase enum name (e.g. YURTICI → "yurtici"),
        // and if none exists, null → Kargonomi automatically picks the cheapest.
        String carrierSlug = resolveKargonomiSlug(order);

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
