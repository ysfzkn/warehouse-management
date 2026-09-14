package com.warehouse.service.cargo;

import com.warehouse.entity.Order;
import com.warehouse.entity.OrderStatusHistory;
import com.warehouse.enums.OrderStatus;
import com.warehouse.repository.CargoProviderRepository;
import com.warehouse.repository.OrderItemRepository;
import com.warehouse.repository.OrderRepository;
import com.warehouse.repository.OrderStatusHistoryRepository;
import com.warehouse.service.NotificationService;
import com.warehouse.service.OrderDeliveryService;
import com.warehouse.service.SiteSettingService;
import com.warehouse.service.notification.NotificationDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a carrier status is allowed to do to an order.
 *
 * <p>The rule being pinned down: "delivered" is the only status that moves the order by itself,
 * and it only does so through a legal state-machine transition. Everything that went wrong —
 * not delivered, lost, coming back — reaches a human instead of silently rewriting the order.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CargoApiServiceTrackingTest {

    @Mock private SiteSettingService settingService;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private CargoProviderRepository cargoProviderRepository;
    @Mock private OrderStatusHistoryRepository statusHistoryRepository;
    @Mock private OrderDeliveryService orderDeliveryService;
    @Mock private NotificationDispatchService notificationDispatchService;
    @Mock private NotificationService notificationService;
    @Mock private CargoEventLedger eventLedger;
    @Mock private CargoShipmentOutboxService outboxService;
    @Mock private CargoPackagePlanner packagePlanner;

    private CargoApiService service;

    @BeforeEach
    void setUp() {
        service = new CargoApiService(List.of(), settingService, orderRepository, orderItemRepository,
                cargoProviderRepository, statusHistoryRepository, orderDeliveryService,
                notificationDispatchService, notificationService,
                eventLedger, outboxService, packagePlanner);
    }

    private Order shippedOrder() {
        Order order = new Order();
        order.setId(7L);
        order.setOrderNumber("SP-2026-0007");
        order.setStatus(OrderStatus.SHIPPED);
        order.setCargoTrackingNo("1234567890");
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        return order;
    }

    private CargoTrackingStatus status(CargoTrackingStatus.CargoStatus mapped, String raw) {
        return CargoTrackingStatus.builder()
                .trackingNumber("1234567890")
                .status(mapped)
                .statusText(raw)
                .deliveredAt(mapped == CargoTrackingStatus.CargoStatus.DELIVERED
                        ? LocalDateTime.now() : null)
                .build();
    }

    @Test
    @DisplayName("Teslim edildi → sipariş DELIVERED, stok etkileri uygulanır, müşteri bilgilendirilir")
    void deliveredMovesTheOrder() {
        Order order = shippedOrder();

        boolean changed = service.applyTrackingUpdate(order.getId(),
                status(CargoTrackingStatus.CargoStatus.DELIVERED, "webservice_shipment_delivered"),
                CargoApiService.SOURCE_WEBHOOK);

        assertThat(changed).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getActualDeliveryDate()).isNotNull();
        assertThat(order.getCargoStatus()).isEqualTo("webservice_shipment_delivered");

        verify(orderDeliveryService).applyDeliveredEffects(eq(order), eq("system"),
                eq(CargoApiService.SOURCE_WEBHOOK));
        verify(notificationDispatchService).notifyOrderStatusChange(any(), eq("SP-2026-0007"),
                eq("DELIVERED"), eq("1234567890"), anyString());

        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(statusHistoryRepository).save(history.capture());
        assertThat(history.getValue().getOldStatus()).isEqualTo("SHIPPED");
        assertThat(history.getValue().getNewStatus()).isEqualTo("DELIVERED");
        assertThat(history.getValue().getNote()).contains("Kargo Teslim Edildi");
    }

    @Test
    @DisplayName("İptal edilmiş siparişe teslim bildirimi gelirse durum zorlanmaz, admin uyarılır")
    void deliveredOnCancelledOrderDoesNotForceTheTransition() {
        Order order = shippedOrder();
        order.setStatus(OrderStatus.CANCELLED);

        service.applyTrackingUpdate(order.getId(),
                status(CargoTrackingStatus.CargoStatus.DELIVERED, "webservice_shipment_delivered"),
                CargoApiService.SOURCE_WEBHOOK);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderDeliveryService, never()).applyDeliveredEffects(any(), anyString(), anyString());
        verify(notificationDispatchService, never())
                .notifyOrderStatusChange(any(), anyString(), anyString(), any(), anyString());
        verify(notificationService).create(anyString(), anyString(), eq("ORDER"), anyLong());
    }

    @Test
    @DisplayName("Teslim edilemedi → sipariş durumu değişmez, admin bildirimi oluşur")
    void failedDeliveryAlertsWithoutChangingTheOrder() {
        Order order = shippedOrder();

        service.applyTrackingUpdate(order.getId(),
                status(CargoTrackingStatus.CargoStatus.FAILED, "webservice_shipment_not_delivered"),
                CargoApiService.SOURCE_WEBHOOK);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getCargoStatus()).isEqualTo("webservice_shipment_not_delivered");

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(anyString(), message.capture(), eq("ORDER"), eq(7L));
        assertThat(message.getValue()).contains("Kargo Teslim Edilemedi");
        verify(orderDeliveryService, never()).applyDeliveredEffects(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Aynı sorun tekrar bildirilirse ikinci bir uyarı üretilmez")
    void repeatedProblemStatusAlertsOnlyOnce() {
        Order order = shippedOrder();
        CargoTrackingStatus lost = status(CargoTrackingStatus.CargoStatus.FAILED, "webservice_shipment_missing");

        service.applyTrackingUpdate(order.getId(), lost, CargoApiService.SOURCE_WEBHOOK);
        service.applyTrackingUpdate(order.getId(), lost, CargoApiService.SOURCE_JOB);

        verify(notificationService, times(1)).create(anyString(), anyString(), eq("ORDER"), eq(7L));
    }

    @Test
    @DisplayName("Yolda bildirimi sadece ham durumu ve son sorgulama zamanını günceller")
    void inTransitOnlyRecords() {
        Order order = shippedOrder();

        service.applyTrackingUpdate(order.getId(),
                status(CargoTrackingStatus.CargoStatus.IN_TRANSIT, "webservice_shipment_started"),
                CargoApiService.SOURCE_JOB);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getCargoLastTrackedAt()).isNotNull();
        verify(notificationService, never()).create(anyString(), anyString(), anyString(), anyLong());
        verify(statusHistoryRepository, never()).save(any());
    }
}
