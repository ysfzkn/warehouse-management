package com.warehouse.service.cargo;

import com.warehouse.entity.CargoProvider;
import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;
import com.warehouse.entity.Product;
import com.warehouse.enums.OrderStatus;
import com.warehouse.repository.CargoProviderRepository;
import com.warehouse.repository.OrderItemRepository;
import com.warehouse.repository.OrderRepository;
import com.warehouse.repository.OrderStatusHistoryRepository;
import com.warehouse.service.NotificationService;
import com.warehouse.service.OrderDeliveryService;
import com.warehouse.service.SiteSettingService;
import com.warehouse.service.notification.NotificationDispatchService;
import com.warehouse.util.OrderStatusHistoryFactory;
import com.warehouse.util.OrderStatusMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** Audit labels for status history rows written from the cargo path. */
    public static final String SOURCE_WEBHOOK = "KARGONOMI_WEBHOOK";
    public static final String SOURCE_JOB = "CARGO_TRACKING_JOB";

    private final List<CargoApiProvider> providers;
    private final SiteSettingService settingService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CargoProviderRepository cargoProviderRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OrderDeliveryService orderDeliveryService;
    private final NotificationDispatchService notificationDispatchService;
    private final NotificationService notificationService;
    private final CargoEventLedger eventLedger;
    private final CargoShipmentOutboxService outboxService;
    private final CargoPackagePlanner packagePlanner;
    private final KargonomiGeoLookupService geoLookup;
    private final com.warehouse.repository.WarehouseRepository warehouseRepository;

    public CargoApiService(List<CargoApiProvider> providers,
                            SiteSettingService settingService,
                            OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            CargoProviderRepository cargoProviderRepository,
                            OrderStatusHistoryRepository statusHistoryRepository,
                            OrderDeliveryService orderDeliveryService,
                            NotificationDispatchService notificationDispatchService,
                            NotificationService notificationService,
                            CargoEventLedger eventLedger,
                            CargoShipmentOutboxService outboxService,
                            CargoPackagePlanner packagePlanner,
                            KargonomiGeoLookupService geoLookup,
                            com.warehouse.repository.WarehouseRepository warehouseRepository) {
        this.providers = providers;
        this.settingService = settingService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cargoProviderRepository = cargoProviderRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.orderDeliveryService = orderDeliveryService;
        this.notificationDispatchService = notificationDispatchService;
        this.notificationService = notificationService;
        this.eventLedger = eventLedger;
        this.outboxService = outboxService;
        this.packagePlanner = packagePlanner;
        this.geoLookup = geoLookup;
        this.warehouseRepository = warehouseRepository;
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
        CargoShipmentResult result = attemptShipment(order);
        if (result == null) return null;

        // The outbox is what makes "kuyruğa alındı" true rather than a comforting log line.
        if (result.isSuccess()) {
            outboxService.markSucceeded(order.getId());
        } else {
            outboxService.enqueue(order, result.getErrorCode(), result.getErrorMessage());
        }
        return result;
    }

    /**
     * One attempt at creating the shipment, with no queueing of its own — the caller decides what
     * a failure means. {@link com.warehouse.job.CargoShipmentOutboxJob} calls this directly so a
     * retry does not re-queue itself.
     *
     * @return null when the integration is off or no provider is configured
     */
    @Transactional
    public CargoShipmentResult attemptShipment(Order order) {
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
     * Queries the provider for the order's current tracking status. Writes nothing —
     * deliberately kept outside any transaction so the HTTP call does not hold one open.
     */
    public CargoTrackingStatus fetchTrackingStatus(Order order) {
        if (!isEnabled() || order == null) return null;

        CargoApiProvider provider = getActiveProvider();
        if (provider == null) return null;

        String reference = shipmentReference(order);
        if (reference == null) return null;

        return provider.getTrackingStatus(reference);
    }

    /**
     * Queries the cargo tracking status for the order and applies it.
     */
    public CargoTrackingStatus trackOrder(Order order) {
        CargoTrackingStatus status = fetchTrackingStatus(order);
        if (status != null) {
            applyTrackingUpdate(order.getId(), status, SOURCE_JOB);
        }
        return status;
    }

    /**
     * What the provider wants when asked about a shipment.
     *
     * <p>Kargonomi looks a shipment up by its own id ({@code GET /shipments/{id}}), not by the
     * carrier's tracking number — passing the tracking number here returned 404 for every
     * polled order. The tracking number stays as a fallback for providers that key on it and
     * for shipments imported without a provider id.
     */
    private String shipmentReference(Order order) {
        if (order.getCargoProviderShipmentId() != null && !order.getCargoProviderShipmentId().isBlank()) {
            return order.getCargoProviderShipmentId();
        }
        if (order.getCargoTrackingNo() != null && !order.getCargoTrackingNo().isBlank()) {
            return order.getCargoTrackingNo();
        }
        return null;
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
     * Can the active carrier actually deliver to this district?
     *
     * <p>Answers {@code true} whenever we cannot tell — integration off, a provider that does not
     * publish its coverage, or a carrier we could not reach. A checkout is never blocked by an
     * uncertain answer; only by a district the carrier positively does not recognise.
     */
    public boolean isAddressDeliverable(String city, String district) {
        if (!isEnabled()) return true;
        if (getActiveProvider() instanceof KargonomiCargoProvider) {
            return geoLookup.isDeliverable(city, district);
        }
        return true;
    }

    /**
     * Is the return-label feature switched on?
     *
     * <p>Separate from {@code cargo_api_enabled} on purpose: Kargonomi's documentation does not
     * describe how a return shipment is created, so the {@code is_return} flag we send is an
     * assumption. Until it is confirmed, this stays off and returns are handled the way they are
     * today — the customer posts the goods back themselves.
     */
    public boolean isReturnLabelEnabled() {
        return isEnabled()
                && "true".equalsIgnoreCase(settingService.getSetting("cargo_return_label_enabled"));
    }

    /**
     * Creates the shipment that brings goods back from the customer.
     *
     * <p>Roles are reversed relative to a normal shipment: the customer is the sender and our own
     * warehouse is the recipient. The parcel plan is deliberately simple — one parcel for the
     * whole return, because we cannot know how the customer will box it up.
     *
     * @return the carrier's result, or null if the feature is off
     */
    @Transactional
    public CargoShipmentResult createReturnShipmentForOrder(Order order) {
        if (!isReturnLabelEnabled()) return null;

        CargoApiProvider provider = getActiveProvider();
        if (provider == null) return null;

        Map<String, Object> customerAddress = order.getShippingAddressSnapshot();
        String customerName = (strFromMap(customerAddress, "firstName") + " "
                + strFromMap(customerAddress, "lastName")).trim();

        CargoShipmentRequest request = CargoShipmentRequest.builder()
                .orderId(order.getId())
                .orderNumber("IADE-" + order.getOrderNumber())
                // Recipient: us.
                .recipientName(firstNonBlank(settingService.getSetting("sender_name"),
                        settingService.getSetting("site_name")))
                .recipientPhone(settingService.getSetting("sender_phone"))
                .recipientAddress(settingService.getSetting("sender_address"))
                .recipientCity(settingService.getSetting("sender_city"))
                .recipientDistrict(settingService.getSetting("sender_district"))
                .recipientPostalCode(settingService.getSetting("sender_postal_code"))
                .recipientCountryCode("TR")
                // Sender: the customer posting the goods back.
                .senderName(customerName)
                .senderPhone(strFromMap(customerAddress, "phone"))
                .senderAddress(strFromMap(customerAddress, "addressLine"))
                .senderCity(strFromMap(customerAddress, "city"))
                .senderDistrict(strFromMap(customerAddress, "district"))
                .senderPostalCode(strFromMap(customerAddress, "postalCode"))
                .packageCount(1)
                .packages(packagePlanner.plan(orderItemRepository.findByOrderId(order.getId()), 1))
                .contentDescription("İade — Sipariş #" + order.getOrderNumber())
                .build();

        CargoShipmentResult result = provider.createReturnShipment(request);
        if (result != null && result.isSuccess()) {
            logger.info("İade kargosu oluşturuldu: order={}, takip={}",
                    order.getOrderNumber(), result.getTrackingNumber());
        } else {
            logger.warn("İade kargosu oluşturulamadı: order={}, hata={}", order.getOrderNumber(),
                    result != null ? result.getErrorMessage() : "sonuç yok");
        }
        return result;
    }

    private String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    /**
     * Corrects the recipient details on an order that is already with the carrier — a mistyped
     * phone number, a missing apartment number. Updates our own snapshot and the carrier's copy
     * together, so the two cannot drift apart.
     *
     * @return true if the carrier accepted the correction (or there was no shipment to correct)
     */
    @Transactional
    public boolean correctRecipient(Order order, String name, String phone, String address,
                                     String city, String district) {
        Map<String, Object> snapshot = order.getShippingAddressSnapshot() != null
                ? new java.util.LinkedHashMap<>(order.getShippingAddressSnapshot())
                : new java.util.LinkedHashMap<String, Object>();

        if (name != null && !name.isBlank()) {
            String[] parts = name.trim().split("\\s+", 2);
            snapshot.put("firstName", parts[0]);
            snapshot.put("lastName", parts.length > 1 ? parts[1] : "");
        }
        if (phone != null && !phone.isBlank()) snapshot.put("phone", phone.trim());
        if (address != null && !address.isBlank()) snapshot.put("addressLine", address.trim());
        if (city != null && !city.isBlank()) snapshot.put("city", city.trim());
        if (district != null && !district.isBlank()) snapshot.put("district", district.trim());

        order.setShippingAddressSnapshot(snapshot);
        orderRepository.save(order);

        if (order.getCargoProviderShipmentId() == null || order.getCargoProviderShipmentId().isBlank()) {
            return true;   // nothing at the carrier yet; the next shipment picks up the new address
        }
        if (!(getActiveProvider() instanceof KargonomiCargoProvider k)) return false;

        boolean ok = k.patchRecipient(order.getCargoProviderShipmentId(), name, phone, address, city, district);
        statusHistoryRepository.save(OrderStatusHistoryFactory.create(
                order, order.getStatus(), order.getStatus(),
                com.warehouse.util.CurrentUser.usernameOrSystem(), "ADMIN",
                ok ? "Kargo alıcı bilgisi düzeltildi" : "Kargo alıcı bilgisi düzeltilemedi (kargo firması kabul etmedi)"));
        return ok;
    }

    /**
     * Withdraws an order's shipment from the carrier.
     *
     * <p>Draft and dispatched shipments are withdrawn differently: a draft is deleted outright,
     * anything further along has to be cancelled — and Kargonomi only accepts a cancellation
     * within 36 hours. Cancelling an order used to leave its shipment standing either way.
     *
     * @return true if the carrier no longer holds a live shipment for this order
     */
    @Transactional
    public boolean withdrawShipment(Order order) {
        String shipmentId = order.getCargoProviderShipmentId();
        if (!isEnabled() || shipmentId == null || shipmentId.isBlank()) return false;

        boolean stillDraft = order.getCargoStatus() == null
                || "draft".equalsIgnoreCase(order.getCargoStatus())
                || "ready".equalsIgnoreCase(order.getCargoStatus());

        boolean removed;
        if (stillDraft && getActiveProvider() instanceof KargonomiCargoProvider k) {
            removed = k.deleteShipment(shipmentId);
            if (removed) {
                // Deleted outright — the tracking number never existed for the customer.
                order.setCargoProviderShipmentId(null);
                order.setCargoTrackingNo(null);
                order.setCargoLabelUrl(null);
                order.setCargoStatus(null);
                orderRepository.save(order);
            }
        } else {
            CargoShipmentResult result = cancelShipment(order);
            removed = result != null && result.isSuccess();
        }

        statusHistoryRepository.save(OrderStatusHistoryFactory.create(
                order, order.getStatus(), order.getStatus(),
                com.warehouse.util.CurrentUser.usernameOrSystem(), "ADMIN",
                removed ? (stillDraft ? "Kargo taslağı silindi" : "Kargo gönderisi iptal edildi")
                        : "Kargo gönderisi geri çekilemedi — kargo firması panelinden kontrol edin"));

        if (!removed) {
            notifyAdminSafely("Kargo iptal edilemedi: " + order.getOrderNumber(),
                    "Sipariş iptal edildi ama kargo gönderisi geri çekilemedi. Kargonomi panelinden "
                            + "elle iptal edilmesi gerekiyor (36 saat kuralı geçmiş olabilir)."
                            + trackingSuffix(order), order);
        }
        return removed;
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
     * Applies a tracking update coming from a webhook or from the polling job.
     *
     * <p>This is the single place where a carrier status turns into something the rest of the
     * system can see:
     * <ul>
     *   <li><b>Delivered</b> → the order moves to DELIVERED (through the status machine),
     *       stock reservations are converted to a sale, the status history gets a row and the
     *       customer is notified.</li>
     *   <li><b>Could not be delivered / lost / returning / cancelled</b> → the order status is
     *       deliberately left alone (a human decides what a failed delivery means), but the raw
     *       carrier status is stored on the order and an admin notification is raised — once per
     *       actual status change, not once per webhook retry.</li>
     *   <li>Everything else → only the raw status and the "last tracked" timestamp move.</li>
     * </ul>
     *
     * @param orderId the order to update
     * @param status  the parsed carrier status
     * @param source  audit label — {@link #SOURCE_WEBHOOK} or {@link #SOURCE_JOB}
     * @return true if anything on the order changed
     */
    @Transactional
    public boolean applyTrackingUpdate(Long orderId, CargoTrackingStatus status, String source) {
        if (orderId == null || status == null) return false;
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return false;

        boolean changed = false;
        order.setCargoLastTrackedAt(LocalDateTime.now());

        // Save the tracking code if it arrived for the first time
        if ((order.getCargoTrackingNo() == null || order.getCargoTrackingNo().isBlank())
                && status.getTrackingNumber() != null && !status.getTrackingNumber().isBlank()) {
            order.setCargoTrackingNo(status.getTrackingNumber());
            changed = true;
        }

        // Raw carrier status — the comparison point that keeps repeated deliveries of the same
        // event from raising the same alert again and again.
        String raw = status.getStatusText() != null && !status.getStatusText().isBlank()
                ? status.getStatusText()
                : String.valueOf(status.getStatus());
        boolean carrierStatusChanged = !raw.equalsIgnoreCase(
                order.getCargoStatus() != null ? order.getCargoStatus() : "");
        if (carrierStatusChanged) {
            order.setCargoStatus(raw.length() > 60 ? raw.substring(0, 60) : raw);
            order.setCargoStatusUpdatedAt(LocalDateTime.now());
            changed = true;
        }

        orderRepository.save(order);

        // Append to the cargo history before acting on it, so the trail survives even if the
        // status handling below runs into trouble.
        eventLedger.record(order, status, source, carrierStatusChanged);

        switch (status.getStatus()) {
            case DELIVERED -> changed |= markDelivered(order, status, source);
            case FAILED, RETURN_IN_TRANSIT, RETURNED, CANCELLED -> {
                if (carrierStatusChanged) raiseCargoProblemAlert(order, status, source);
            }
            default -> { /* CREATED / PICKED_UP / IN_TRANSIT / OUT_FOR_DELIVERY / UNKNOWN — sadece kayıt */ }
        }

        return changed;
    }

    /**
     * Carrier says delivered → move the order to DELIVERED with all the side effects the admin
     * path performs. Never forces an invalid transition; an order that is cancelled or still
     * being prepared raises an alert for a human instead.
     */
    private boolean markDelivered(Order order, CargoTrackingStatus status, String source) {
        if (order.getActualDeliveryDate() == null) {
            order.setActualDeliveryDate(status.getDeliveredAt() != null
                    ? status.getDeliveredAt().toLocalDate()
                    : LocalDate.now());
        }

        OrderStatus previous = order.getStatus();
        if (previous == OrderStatus.DELIVERED) {
            orderRepository.save(order);
            return false;
        }

        if (!OrderStatusMachine.isValidTransition(previous, OrderStatus.DELIVERED)) {
            orderRepository.save(order);
            logger.warn("Kargo teslim bildirdi ama sipariş {} durumu {} — otomatik geçiş yapılmadı",
                    order.getOrderNumber(), previous);
            notifyAdminSafely("Kargo teslim dedi, sipariş durumu uymuyor: " + order.getOrderNumber(),
                    "Kargo firması siparişi teslim edilmiş gösteriyor ama sipariş \""
                            + OrderStatusMachine.getLabel(previous) + "\" durumunda. Elle kontrol edilmeli."
                            + trackingSuffix(order),
                    order);
            return false;
        }

        order.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);

        statusHistoryRepository.save(OrderStatusHistoryFactory.create(
                order, previous, OrderStatus.DELIVERED, "system", source,
                "Kargo teslim bildirimi: " + KargonomiCargoProvider.statusLabel(status.getStatusText())));

        // Reserved stock → actual sale, and door payments settled. Shared with the admin path.
        orderDeliveryService.applyDeliveredEffects(order, "system", source);

        try {
            notificationDispatchService.notifyOrderStatusChange(
                    order.getCustomer(), order.getOrderNumber(),
                    "DELIVERED", order.getCargoTrackingNo(), "Siparişiniz teslim edildi.");
        } catch (Exception e) {
            logger.warn("Teslimat bildirimi gönderilemedi (sipariş {}): {}",
                    order.getOrderNumber(), e.toString());
        }

        logger.info("Order auto-delivered: {} (tracking={}, source={})",
                order.getOrderNumber(), order.getCargoTrackingNo(), source);
        return true;
    }

    /**
     * Could not be delivered, lost, returning or cancelled by the carrier. The order status is
     * left as it is — these need a decision, not an automatic transition — but the event is
     * written to the order's history and pushed to the admin panel.
     */
    private void raiseCargoProblemAlert(Order order, CargoTrackingStatus status, String source) {
        String label = KargonomiCargoProvider.statusLabel(status.getStatusText());
        if (label == null || label.isBlank()) label = problemLabel(status.getStatus());

        try {
            statusHistoryRepository.save(OrderStatusHistoryFactory.create(
                    order, order.getStatus(), order.getStatus(), "system", source,
                    "Kargo durumu: " + label));
        } catch (Exception e) {
            logger.warn("Kargo durum geçmişi yazılamadı (sipariş {}): {}",
                    order.getOrderNumber(), e.toString());
        }

        notifyAdminSafely("Kargo sorunu: " + order.getOrderNumber(),
                label + trackingSuffix(order), order);

        logger.warn("Kargo sorunu — order={}, durum={}, source={}",
                order.getOrderNumber(), label, source);
    }

    private String problemLabel(CargoTrackingStatus.CargoStatus status) {
        return switch (status) {
            case FAILED -> "Kargo teslim edilemedi";
            case RETURN_IN_TRANSIT -> "Kargo göndericiye geri dönüyor";
            case RETURNED -> "Kargo iade edildi";
            case CANCELLED -> "Kargo iptal edildi";
            default -> "Kargo durumu güncellendi";
        };
    }

    private String trackingSuffix(Order order) {
        StringBuilder sb = new StringBuilder();
        if (order.getCargoProviderName() != null && !order.getCargoProviderName().isBlank()) {
            sb.append(" — ").append(order.getCargoProviderName());
        }
        if (order.getCargoTrackingNo() != null && !order.getCargoTrackingNo().isBlank()) {
            sb.append(" / ").append(order.getCargoTrackingNo());
        }
        return sb.toString();
    }

    /** An in-app admin notification must never be the reason a cargo update fails. */
    private void notifyAdminSafely(String title, String message, Order order) {
        try {
            notificationService.create(title, message, "ORDER", order.getId());
        } catch (Exception e) {
            logger.warn("Kargo admin bildirimi oluşturulamadı (sipariş {}): {}",
                    order.getOrderNumber(), e.toString());
        }
    }

    /**
     * Is status tracking actually arriving by webhook? Used by the polling job to decide whether
     * it is the primary channel or just a safety net.
     *
     * <p>Note this can only confirm that <em>we</em> are set up to accept webhooks (an active
     * Kargonomi provider plus a signing secret — without it the receiver rejects everything).
     * Whether the webhook is still registered on the Kargonomi side is not visible from here,
     * which is exactly why the job keeps running as a backstop.
     */
    public boolean isWebhookTrackingActive() {
        if (!(getActiveProvider() instanceof KargonomiCargoProvider)) return false;
        String secret = settingService.getSetting("kargonomi_webhook_secret");
        return secret != null && !secret.isBlank();
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

        // Parcel plan — how many boxes actually leave the warehouse, and the desi of each.
        // Not the same as the unit count: consolidatable items share a box.
        List<CargoShipmentRequest.PackagePlan> packagePlan =
                packagePlanner.plan(orderItems, order.getCargoPackageCount());

        BigDecimal totalDesi = packagePlan.stream()
                .map(CargoShipmentRequest.PackagePlan::getDesi)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalWeight = BigDecimal.ZERO;
        for (OrderItem oi : orderItems) {
            Product p = oi.getProduct();
            if (p == null || p.getWeight() == null) continue;
            totalWeight = totalWeight.add(
                    BigDecimal.valueOf(p.getWeight()).multiply(BigDecimal.valueOf(oi.getQuantity())));
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
                .senderWarehouseId(resolveSenderWarehouseId(orderItems))
                .senderName(senderName)
                .senderPhone(senderPhone)
                .senderAddress(senderAddress)
                .senderCity(senderCity)
                .senderDistrict(senderDistrict)
                .senderPostalCode(senderPostalCode)
                .packageCount(packagePlan.size())
                .packages(packagePlan)
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

    /**
     * Which of our warehouses this order ships from, translated to the carrier's id.
     *
     * <p>Takes the warehouse of the first line that has one: a single order is picked from one
     * place in practice, and guessing between two would print one wrong address either way.
     * Null means "use the global setting", which is the whole story for a single-warehouse shop.
     */
    private String resolveSenderWarehouseId(List<OrderItem> orderItems) {
        Long warehouseId = orderItems.stream()
                .map(OrderItem::getWarehouseId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (warehouseId == null) return null;

        return warehouseRepository.findById(warehouseId)
                .map(com.warehouse.entity.Warehouse::getKargonomiWarehouseId)
                .filter(id -> id != null && !id.isBlank())
                .orElse(null);
    }

    private String strFromMap(Map<String, Object> map, String key) {
        if (map == null) return "";
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }
}
