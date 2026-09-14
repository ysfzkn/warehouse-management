package com.warehouse.service.cargo;

import com.warehouse.entity.CargoShipmentOutbox;
import com.warehouse.entity.Order;
import com.warehouse.repository.CargoShipmentOutboxRepository;
import com.warehouse.service.NotificationService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What happens to an order whose shipment the carrier would not create.
 *
 * <p>Before the outbox, the customer was told the order had been queued while nothing had been:
 * the failure left a log line and the order silently never shipped. These tests pin the promise —
 * the work is recorded, retried on a widening backoff, and handed to a person when retrying stops
 * being reasonable.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CargoShipmentOutboxServiceTest {

    @Mock private CargoShipmentOutboxRepository outboxRepository;
    @Mock private NotificationService notificationService;

    private CargoShipmentOutboxService service;

    @BeforeEach
    void setUp() {
        service = new CargoShipmentOutboxService(outboxRepository, notificationService);
        when(outboxRepository.save(any(CargoShipmentOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Order order() {
        Order order = new Order();
        order.setId(42L);
        order.setOrderNumber("SP-2026-0042");
        return order;
    }

    private CargoShipmentOutbox entry(int attempts, String status) {
        CargoShipmentOutbox entry = new CargoShipmentOutbox();
        entry.setOrderId(42L);
        entry.setOrderNumber("SP-2026-0042");
        entry.setAttempts(attempts);
        entry.setStatus(status);
        entry.setNextAttemptAt(LocalDateTime.now());
        return entry;
    }

    @Test
    @DisplayName("Başarısız gönderi gerçekten kuyruğa giriyor")
    void enqueueRecordsTheDebt() {
        when(outboxRepository.findByOrderId(42L)).thenReturn(Optional.empty());

        service.enqueue(order(), "UPSTREAM_UNAVAILABLE", "Kargo sağlayıcı yanıt vermiyor");

        ArgumentCaptor<CargoShipmentOutbox> saved = ArgumentCaptor.forClass(CargoShipmentOutbox.class);
        verify(outboxRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(CargoShipmentOutbox.STATUS_PENDING);
        assertThat(saved.getValue().getOrderId()).isEqualTo(42L);
        assertThat(saved.getValue().getLastErrorCode()).isEqualTo("UPSTREAM_UNAVAILABLE");
        assertThat(saved.getValue().getNextAttemptAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("Aynı sipariş ikinci kez başarısız olursa ikinci kayıt açılmaz")
    void enqueueIsIdempotentPerOrder() {
        CargoShipmentOutbox existing = entry(1, CargoShipmentOutbox.STATUS_PENDING);
        when(outboxRepository.findByOrderId(42L)).thenReturn(Optional.of(existing));

        service.enqueue(order(), "EXCEPTION", "yine olmadı");

        ArgumentCaptor<CargoShipmentOutbox> saved = ArgumentCaptor.forClass(CargoShipmentOutbox.class);
        verify(outboxRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(saved.getValue().getAttempts()).isEqualTo(1);   // deneme sayacı işin sorumluluğu
    }

    @Test
    @DisplayName("Bu arada kargo oluşmuşsa eski bir hata kaydı kuyruğu yeniden açmıyor")
    void enqueueDoesNotReopenASucceededEntry() {
        when(outboxRepository.findByOrderId(42L))
                .thenReturn(Optional.of(entry(2, CargoShipmentOutbox.STATUS_SUCCEEDED)));

        service.enqueue(order(), "EXCEPTION", "geç kalmış hata");

        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("Her başarısız deneme bir sonrakini daha uzağa atıyor")
    void retriesBackOff() {
        CargoShipmentOutbox entry = entry(1, CargoShipmentOutbox.STATUS_PENDING);
        LocalDateTime now = LocalDateTime.now();

        boolean abandoned = service.recordRetryFailure(entry, "TIMEOUT", "zaman aşımı");

        assertThat(abandoned).isFalse();
        assertThat(entry.getAttempts()).isEqualTo(2);
        assertThat(entry.getStatus()).isEqualTo(CargoShipmentOutbox.STATUS_PENDING);
        // 2. denemeden sonra bekleme 15 dakika (5 × 3)
        assertThat(entry.getNextAttemptAt()).isAfter(now.plusMinutes(14));
    }

    @Test
    @DisplayName("Deneme hakkı bitince kayıt düşüyor ve admin'e bildirim gidiyor")
    void givesUpAndTellsSomeone() {
        CargoShipmentOutbox entry = entry(CargoShipmentOutbox.MAX_ATTEMPTS - 1,
                CargoShipmentOutbox.STATUS_PENDING);

        boolean abandoned = service.recordRetryFailure(entry, "UPSTREAM_UNAVAILABLE", "hâlâ kapalı");

        assertThat(abandoned).isTrue();
        assertThat(entry.getStatus()).isEqualTo(CargoShipmentOutbox.STATUS_ABANDONED);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(anyString(), message.capture(), eq("ORDER"), anyLong());
        assertThat(message.getValue()).contains("elle oluşturulması");
    }

    @Test
    @DisplayName("Bekleme süresi dört saati aşmıyor")
    void backoffIsCapped() {
        CargoShipmentOutbox entry = entry(10, CargoShipmentOutbox.STATUS_PENDING);
        LocalDateTime now = LocalDateTime.now();

        assertThat(entry.backoffFrom(now)).isBeforeOrEqualTo(now.plusMinutes(240));
    }
}
