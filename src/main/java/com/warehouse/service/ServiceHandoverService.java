package com.warehouse.service;

import com.warehouse.constants.NotificationMessages;
import com.warehouse.dto.DeliveryReceiptDto;
import com.warehouse.dto.NotificationRequest;
import com.warehouse.dto.ServiceHandoverRequest;
import com.warehouse.dto.StockTransferDto;
import com.warehouse.entity.StockTransfer;
import com.warehouse.enums.DomainEntityType;
import com.warehouse.enums.TransferStatus;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.mapper.StockTransferMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Depot exit: records the shipment, takes the stock down and issues the receipt as one act.
 *
 * <p>The three steps belong to two services, which is exactly why this class exists. Left to
 * the controller they would run in separate transactions, and a receipt that failed to render
 * would leave the stock already deducted with no paper to show for it — the operator would be
 * looking at an error message while the goods had silently left the books. Here the whole
 * thing commits or none of it does.</p>
 *
 * <p>Planlı çıkışta aynı gerekçe ikinci kez geçerli, sadece sırası ters: teslimat kapanırken
 * stok düşümü ile makbuzun imza kaydı tek işlemde olmalı. Yarısı işlenmiş bir teslimat —
 * stok düşmüş ama makbuzda kimin teslim aldığı yazmıyor, ya da tersi — kâğıt ile kaydın
 * uyuşmadığı tam olarak o hâldir.</p>
 */
@Service
public class ServiceHandoverService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final StockTransferService transferService;
    private final DeliveryReceiptService receiptService;
    private final StockTransferMapper transferMapper;
    private final NotificationService notificationService;

    public ServiceHandoverService(StockTransferService transferService,
                                  DeliveryReceiptService receiptService,
                                  StockTransferMapper transferMapper,
                                  NotificationService notificationService) {
        this.transferService = transferService;
        this.receiptService = receiptService;
        this.transferMapper = transferMapper;
        this.notificationService = notificationService;
    }

    @Transactional
    public Result handOver(ServiceHandoverRequest request, String username) {
        StockTransfer transfer = transferService.createServiceHandover(request);
        DeliveryReceiptDto receipt = receiptService.issue(transfer.getId(), username,
                com.warehouse.enums.DeliveryReceiptKind.SERVICE_HANDOVER);
        return new Result(transferMapper.toDto(transfer), receipt);
    }

    /**
     * Planlı bir teslimatı kapatır: stok <em>burada</em> düşer ve makbuz imzalanmış sayılır.
     *
     * <p>Bu metot, planlı çıkışın varlık sebebinin karşılığı. Mal makbuz basıldığında değil,
     * müşteriye gerçekten teslim edildiğinde stoktan iniyor; arada geçen sürede rezervede
     * duruyor. İki adımı ayrı uç noktalara bölmek, kullanıcının "Teslimatı Tamamla"ya basıp
     * ikinci adımda hata alması hâlinde stoğu düşmüş ama teslim alanı boş bir kayıt
     * bırakırdı — ve kâğıt ile kaydın ayrıştığı yer tam olarak orası olurdu.</p>
     *
     * <p>Düşüm yine {@code completeTransfer} üzerinden: stokun çıktığı tek bir kod yolu
     * olması, planlı çıkışın ikinci bir çıkış kapısına dönüşmesini yapısal olarak engelliyor.</p>
     */
    @Transactional
    public Result completeScheduledDelivery(Long transferId,
                                            String deliveredByName,
                                            String receivedByName,
                                            LocalDateTime deliveredAt,
                                            String note,
                                            String username) {
        StockTransfer transfer = transferService.getTransferByIdOrThrow(transferId);
        if (transfer.getScheduledDeliveryAt() == null) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Bu sevkiyat planlı bir teslimat değil. Teslim bilgisi için makbuz "
                            + "panelindeki teslim onayını kullanın.");
        }
        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "İptal edilmiş sevkiyat teslim edilemez.");
        }
        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Bu teslimat zaten tamamlanmış; stok bir kez düştü. Teslim bilgisini "
                            + "düzeltmek için makbuz panelindeki teslim onayını kullanın.");
        }
        if (receivedByName == null || receivedByName.isBlank()) {
            // Makbuz servisi de aynı kuralı uyguluyor, ama burada erken durmak önemli:
            // sıradaki adım stoğu düşürüyor ve kural ancak ondan sonra kontrol edilseydi
            // işlem geri sarılana kadar stok hareketi denetim kaydına yazılmış olurdu.
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Teslim alan kişinin adı soyadı zorunludur.");
        }

        LocalDateTime when = deliveredAt != null ? deliveredAt : LocalDateTime.now();

        // Makbuz normalde çıkış anında basılıyor; basılmamışsa burada basılıyor ki teslim
        // onayı dayanaksız kalmasın.
        if (receiptService.findByTransfer(transferId) == null) {
            receiptService.issue(transferId, username,
                    com.warehouse.enums.DeliveryReceiptKind.SERVICE_HANDOVER);
        }

        StockTransfer completed = transferService.completeTransfer(transferId,
                "Planlı teslimat tamamlandı — teslim alan: " + receivedByName.trim());

        DeliveryReceiptDto receipt = receiptService.confirmDelivery(transferId,
                deliveredByName, receivedByName, when, note, username);

        notificationService.create(NotificationRequest.builder()
                .title(NotificationMessages.DELIVERY_COMPLETED_TITLE)
                .message(String.format(
                        "%s adına planlanan teslimat tamamlandı (planlanan: %s, teslim: %s). "
                                + "Teslim alan: %s. Ürünler stoktan düşüldü.",
                        completed.getCustomerFullName(),
                        format(completed.getScheduledDeliveryAt()), format(when),
                        receivedByName.trim()))
                .entityType(DomainEntityType.StockTransfer.name())
                .entityId(transferId)
                .actor(username)
                .sourceWarehouseId(completed.getSourceWarehouse() != null
                        ? completed.getSourceWarehouse().getId() : null)
                .sourceWarehouseName(completed.getSourceWarehouse() != null
                        ? completed.getSourceWarehouse().getName() : null)
                .quantity(completed.getQuantity())
                .build());

        return new Result(transferMapper.toDto(completed), receipt);
    }

    private static String format(LocalDateTime value) {
        return value == null ? "-" : value.format(DATE_TIME);
    }

    /** The shipment and its receipt, so the caller can print without a second round trip. */
    public record Result(StockTransferDto transfer, DeliveryReceiptDto receipt) {}
}
