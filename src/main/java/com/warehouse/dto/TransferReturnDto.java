package com.warehouse.dto;

import com.warehouse.enums.TransferReturnOrderOutcome;
import com.warehouse.enums.TransferReturnReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** One recorded return, for the shipment detail panel and the return history. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferReturnDto {

    private Long id;
    private Long transferId;
    private LocalDateTime returnedAt;
    private TransferReturnReason reason;
    private String note;
    /** Order decision, when the shipment had a linked order. */
    private TransferReturnOrderOutcome orderOutcome;
    private Integer totalQuantity;
    private String recordedBy;
    private LocalDateTime createdAt;
    private List<Line> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private Long transferItemId;
        private Long productId;
        private String productName;
        private String productSku;
        private Integer quantity;
    }
}
