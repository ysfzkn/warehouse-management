package com.warehouse.service;

import com.warehouse.dto.DeliveryReceiptDto;
import com.warehouse.dto.DeliveryReceiptFilter;
import com.warehouse.enums.DeliveryReceiptKind;
import com.warehouse.enums.DeliveryReceiptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Delivery receipts for stock transfers: issue, print, confirm, archive the signed copy.
 */
public interface DeliveryReceiptService {

    /**
     * Creates the receipt for a transfer, or bumps the revision if one already exists.
     * Re-issuing keeps the receipt number and re-snapshots the shipment, which is what
     * you want after the driver or the address was corrected.
     */
    DeliveryReceiptDto issue(Long transferId, String username);

    /**
     * Issues a receipt of a known kind, for callers that already know which paper this is.
     *
     * <p>{@link #issue(Long, String)} infers the kind from the shipment, which works but has
     * to guess — and since the depot exit's own parties are optional, a shipment can be a
     * depot exit while carrying nothing that says so. The one flow that creates these says it
     * outright instead. The kind is only used when the receipt is new; a reprint keeps the
     * identity of the paper that was signed.</p>
     */
    DeliveryReceiptDto issue(Long transferId, String username, DeliveryReceiptKind kindWhenNew);

    /** The receipt for a transfer, or null when none has been issued yet. */
    /**
     * Taşıyıcı sonradan belirlendiğinde makbuz kaydına işler.
     *
     * <p>Depo çıkış makbuzu taşıyıcı belli olmadan basılıyor ve kâğıdın kapanış paragrafı
     * bunu açıkça taahhüt ediyor: "Taşıyıcı araç ve sürücü bilgisi belirlendiğinde bu
     * belgenin kaydına işlenir." Kod bunu yapmıyordu — sevkiyat güncelleniyor, makbuz
     * kaydı boş kalıyordu ve liste ekranı şoför girilmiş olmasına rağmen kalıcı olarak
     * "basımda yoktu" gösteriyordu.</p>
     *
     * <p>Basım sayacı (revision) bilerek artırılmıyor: bu bir yeniden basım değil, kaydın
     * tamamlanması. İmza tarafları da elle sürülmüyor — onlar kâğıdın imzalandığı andaki
     * kişileri gösteriyor ve sonradan değişmemeli.</p>
     *
     * @return güncellenen makbuz; sevkiyatın makbuzu yoksa {@code null}
     */
    DeliveryReceiptDto noteCarrier(Long transferId);

    DeliveryReceiptDto findByTransfer(Long transferId);

    /** Receipt existence per transfer id — lets the transfer list render its column in one query. */
    Map<Long, DeliveryReceiptDto> findByTransferIds(List<Long> transferIds);

    /** Records who took delivery and when. */
    DeliveryReceiptDto confirmDelivery(Long transferId,
                                       String deliveredByName,
                                       String receivedByName,
                                       LocalDateTime deliveredAt,
                                       String note,
                                       String username);

    /** Printable HTML — the same markup the PDF is rendered from. */
    String renderHtml(Long transferId, boolean showPrintBar);

    /** The archived PDF for one transfer. */
    byte[] renderPdf(Long transferId, String username);

    /**
     * One PDF containing the receipts of several transfers, for the list screen.
     * Transfers without a receipt are skipped rather than failing the whole download.
     */
    byte[] renderBulkPdf(List<Long> transferIds, String username);

    DeliveryReceiptDto addAttachment(Long transferId, MultipartFile file, String username);

    void deleteAttachment(Long attachmentId, String username);

    /** Bytes and content type of a stored attachment, for the view endpoint. */
    StoredAttachment loadAttachment(Long attachmentId);

    /**
     * Arşiv listesi. Boş bir filtre "hepsi" demek.
     *
     * <p>{@code pageable}'ın sıralaması çağırandan geliyor: "en son kesilen" ile "en yakın
     * teslim" farklı sorular ve arşiv ikisini de cevaplamak zorunda.</p>
     */
    Page<DeliveryReceiptDto> search(DeliveryReceiptFilter filter, Pageable pageable);

    /**
     * Ekran başlığındaki sayaçlar.
     *
     * <p>Her biri aynı anda bir kısayol filtresi: kartın gösterdiği sayı ile tıklanınca
     * gelen liste aynı {@code Specification}'dan üretiliyor, yani birbirini tutmaması
     * mümkün değil.</p>
     */
    Map<String, Long> stats();

    record StoredAttachment(byte[] bytes, String contentType, String fileName) {}
}
