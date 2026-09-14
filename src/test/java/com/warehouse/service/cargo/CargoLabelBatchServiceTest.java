package com.warehouse.service.cargo;

import com.warehouse.entity.Order;
import com.warehouse.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Printing a dispatch run's labels in one go.
 *
 * <p>The rule that matters at the printer: one order missing a shipment must not cost you the
 * other nineteen labels — it is reported, and the rest still print.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CargoLabelBatchServiceTest {

    @Mock private CargoApiService cargoApiService;
    @Mock private OrderRepository orderRepository;

    private CargoLabelBatchService service;

    @BeforeEach
    void setUp() {
        service = new CargoLabelBatchService(cargoApiService, orderRepository);
    }

    private Order order(long id, String number, String shipmentId) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber(number);
        order.setCargoProviderShipmentId(shipmentId);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        return order;
    }

    /** A minimal one-page PDF, so the merger has something real to work with. */
    private static byte[] onePagePdf() {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("Seçilen etiketler tek PDF'te birleşiyor")
    void mergesEveryLabelIntoOnePdf() {
        order(1L, "SP-1", "SHIP-1");
        order(2L, "SP-2", "SHIP-2");
        when(cargoApiService.downloadShipmentLabel(any())).thenReturn(onePagePdf());

        var result = service.buildMergedLabels(List.of(1L, 2L));

        assertThat(result.isEmpty()).isFalse();
        assertThat(result.includedOrders()).containsExactly("SP-1", "SP-2");
        assertThat(result.skipped()).isEmpty();
    }

    @Test
    @DisplayName("Kargosu olmayan sipariş diğerlerini götürmüyor, sebebiyle raporlanıyor")
    void reportsOrdersItCouldNotPrint() {
        order(1L, "SP-1", "SHIP-1");
        order(2L, "SP-2", null);                       // kargo oluşturulmamış
        when(orderRepository.findById(3L)).thenReturn(Optional.empty());
        when(cargoApiService.downloadShipmentLabel(any())).thenReturn(onePagePdf());

        var result = service.buildMergedLabels(List.of(1L, 2L, 3L));

        assertThat(result.isEmpty()).isFalse();
        assertThat(result.includedOrders()).containsExactly("SP-1");
        assertThat(result.skipped()).containsEntry("SP-2", "Kargo gönderisi oluşturulmamış");
        assertThat(result.skipped()).containsEntry("#3", "Sipariş bulunamadı");
    }

    @Test
    @DisplayName("Hiçbir etiket indirilemezse boş sonuç döner")
    void emptyWhenNothingCouldBeFetched() {
        order(1L, "SP-1", "SHIP-1");
        when(cargoApiService.downloadShipmentLabel(any())).thenReturn(new byte[0]);

        var result = service.buildMergedLabels(List.of(1L));

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.skipped()).containsEntry("SP-1", "Etiket indirilemedi");
    }

    @Test
    @DisplayName("Aynı sipariş iki kez seçilse de tek etiket basılıyor")
    void deduplicatesTheSelection() {
        order(1L, "SP-1", "SHIP-1");
        when(cargoApiService.downloadShipmentLabel(any())).thenReturn(onePagePdf());

        var result = service.buildMergedLabels(List.of(1L, 1L, 1L));

        assertThat(result.includedOrders()).containsExactly("SP-1");
    }
}
