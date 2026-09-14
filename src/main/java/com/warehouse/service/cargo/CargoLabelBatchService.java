package com.warehouse.service.cargo;

import com.warehouse.entity.Order;
import com.warehouse.repository.OrderRepository;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prints a day's labels in one go.
 *
 * <p>Downloading labels one order at a time is the single most repetitive part of dispatch: a
 * shop sending twenty parcels clicked twenty times, waited twenty times and printed twenty
 * separate files. This fetches each label from the carrier and merges them into one PDF.
 *
 * <p>Orders without a shipment are skipped rather than failing the batch — the caller is told
 * which ones did not make it, so a missing label is visible instead of silently absent from
 * the stack coming off the printer.
 */
@Service
public class CargoLabelBatchService {

    private static final Logger logger = LoggerFactory.getLogger(CargoLabelBatchService.class);

    /** One request should not be able to pull hundreds of labels from the carrier. */
    public static final int MAX_ORDERS_PER_BATCH = 100;

    private final CargoApiService cargoApiService;
    private final OrderRepository orderRepository;

    public CargoLabelBatchService(CargoApiService cargoApiService, OrderRepository orderRepository) {
        this.cargoApiService = cargoApiService;
        this.orderRepository = orderRepository;
    }

    /** The merged PDF plus what was left out of it. */
    public record BatchResult(byte[] pdf, List<String> includedOrders, Map<String, String> skipped) {
        public boolean isEmpty() {
            return pdf == null || pdf.length == 0;
        }
    }

    public BatchResult buildMergedLabels(List<Long> orderIds) {
        List<String> included = new ArrayList<>();
        Map<String, String> skipped = new LinkedHashMap<>();
        List<byte[]> labels = new ArrayList<>();

        for (Long orderId : orderIds.stream().distinct().limit(MAX_ORDERS_PER_BATCH).toList()) {
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null) {
                skipped.put("#" + orderId, "Sipariş bulunamadı");
                continue;
            }
            if (order.getCargoProviderShipmentId() == null || order.getCargoProviderShipmentId().isBlank()) {
                skipped.put(order.getOrderNumber(), "Kargo gönderisi oluşturulmamış");
                continue;
            }
            try {
                byte[] label = cargoApiService.downloadShipmentLabel(order);
                if (label == null || label.length == 0) {
                    skipped.put(order.getOrderNumber(), "Etiket indirilemedi");
                    continue;
                }
                labels.add(label);
                included.add(order.getOrderNumber());
            } catch (Exception e) {
                logger.warn("Toplu etiket — {} atlandı: {}", order.getOrderNumber(), e.toString());
                skipped.put(order.getOrderNumber(), "Hata: " + e.getMessage());
            }
        }

        if (labels.isEmpty()) {
            return new BatchResult(new byte[0], included, skipped);
        }
        return new BatchResult(merge(labels), included, skipped);
    }

    /**
     * Merges the label PDFs in order, the same way delivery receipts are batched elsewhere in
     * the project. A label the merger cannot read is dropped with a warning rather than taking
     * the whole batch down.
     */
    private byte[] merge(List<byte[]> labels) {
        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        merger.setDestinationStream(out);

        for (byte[] label : labels) {
            try {
                merger.addSource(new ByteArrayInputStream(label));
            } catch (Exception e) {
                logger.warn("Toplu etiket — bir PDF eklenemedi, atlandı: {}", e.toString());
            }
        }
        try {
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
            return out.toByteArray();
        } catch (Exception e) {
            logger.error("Toplu etiket birleştirme hatası: {}", e.toString());
            return new byte[0];
        }
    }

    /** File name for the download: {@code kargo-etiketleri-20260914-1530.pdf}. */
    public String suggestedFileName() {
        return "kargo-etiketleri-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                + ".pdf";
    }
}
