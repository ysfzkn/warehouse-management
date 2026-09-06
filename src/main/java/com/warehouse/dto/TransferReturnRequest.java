package com.warehouse.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.warehouse.enums.TransferReturnOrderOutcome;
import com.warehouse.enums.TransferReturnReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Records goods from a completed shipment arriving back at the warehouse. */
@Data
public class TransferReturnRequest {

    /** When the goods physically came back. Defaults to now; may be backdated, never future. */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime returnedAt;

    @NotNull(message = "İade sebebi zorunludur")
    private TransferReturnReason reason;

    @Size(max = 1000, message = "Not 1000 karakteri aşamaz")
    private String note;

    /**
     * What happens to the linked order. Required when the shipment has one, rejected when it
     * does not — the two cases have different consequences for both the order book and the
     * stock reservation, and neither can be guessed from the goods coming back.
     */
    private TransferReturnOrderOutcome orderOutcome;

    @Valid
    @NotEmpty(message = "En az bir kalem iade edilmelidir")
    private List<Item> items;

    @Data
    public static class Item {
        /**
         * The shipped line coming back — not a product id.
         *
         * <p>The same product can appear on two lines off two different stock rows, and the
         * returned goods belong on the row they left from. Naming the line settles both that
         * and how much may still be returned.</p>
         */
        @NotNull(message = "Sevkiyat kalemi zorunludur")
        private Long transferItemId;

        @NotNull(message = "Adet zorunludur")
        @Min(value = 1, message = "Adet en az 1 olmalıdır")
        private Integer quantity;
    }
}
