package com.warehouse.dto;

import com.warehouse.enums.DeliveryReceiptStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read model for the receipt panel and the receipts list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryReceiptDto {

    private Long id;
    private Long transferId;
    private String receiptNo;
    private DeliveryReceiptStatus status;
    private Integer revision;

    private String sourceWarehouseName;
    private String customerFullName;
    private String customerPhone;
    private String customerAddress;
    private String orderNumber;
    private String driverName;
    private String driverPhone;
    private String vehiclePlate;
    private LocalDateTime transferDate;
    private String notes;

    private LocalDateTime deliveredAt;
    private String deliveredByName;
    private String receivedByName;
    private String receivedByNote;
    private LocalDateTime confirmedAt;
    private String confirmedBy;

    private LocalDateTime issuedAt;
    private String issuedBy;

    private List<ItemLine> items;
    private List<AttachmentDto> attachments;

    /**
     * Whether the signed page has come back. Surfaced as its own flag because it is the
     * one thing the warehouse actually chases, and it is independent of {@link #status}.
     */
    private boolean signedCopyOnFile;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemLine {
        private String sku;
        private String name;
        private Integer quantity;
        /** Free text on the paper form: which department/person the goods are for. */
        private String targetNote;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentDto {
        private Long id;
        private String fileName;
        private String contentType;
        private Long sizeBytes;
        private LocalDateTime uploadedAt;
        private String uploadedBy;
        /** Signed, expiring URL — the panel renders it directly in an img/iframe. */
        private String url;
    }
}
