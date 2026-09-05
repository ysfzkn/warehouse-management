package com.warehouse.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Depot exit: goods leave the warehouse into a service company's hands before anyone
 * knows which driver will carry them onward.
 *
 * <p>Deliberately not a variant of {@link StockTransferCreateRequest}. That form's whole
 * point is that a driver, a TC number and a plate are mandatory; adding a flag that turns
 * those four off would make the ordinary transfer form one boolean away from accepting a
 * shipment with no carrier at all. Two request types, two sets of rules, no shared escape
 * hatch — the recipient fields that are optional there are mandatory here and vice versa.</p>
 */
@Data
public class ServiceHandoverRequest {

    @NotNull(message = "Çıkış deposu zorunludur")
    private Long sourceWarehouseId;

    // ── Malı devralan taraf ───────────────────────────────────────────────────

    @NotBlank(message = "Teslim alan servis/kişi adı zorunludur")
    @Size(min = 2, max = 150, message = "Teslim alan adı 2-150 karakter olmalıdır")
    private String handoverToName;

    @Size(max = 30, message = "Teslim alan telefonu 30 karakteri aşamaz")
    private String handoverToPhone;

    @NotBlank(message = "Teslim eden kişinin adı zorunludur")
    @Size(min = 2, max = 150, message = "Teslim eden adı 2-150 karakter olmalıdır")
    private String handedOverBy;

    // ── Malın gideceği müşteri ────────────────────────────────────────────────

    @NotBlank(message = "Müşteri adı soyadı zorunludur")
    @Size(max = 150, message = "Müşteri adı 150 karakteri aşamaz")
    private String customerFullName;

    @NotBlank(message = "Müşteri telefonu zorunludur")
    @Size(max = 20, message = "Müşteri telefonu 20 karakteri aşamaz")
    private String customerPhone;

    @NotBlank(message = "Müşteri adresi zorunludur")
    @Size(max = 500, message = "Müşteri adresi 500 karakteri aşamaz")
    private String customerAddress;

    /** Optional link to the order this shipment fulfils. */
    private Long orderId;

    /** Optional link to the recipient's e-commerce customer record. */
    private Long customerId;

    // ── Belge ─────────────────────────────────────────────────────────────────

    /** When the goods physically changed hands. Defaults to now when omitted. */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime handedOverAt;

    @Size(max = 500, message = "Not 500 karakteri aşamaz")
    private String notes;

    @Valid
    @NotEmpty(message = "En az bir ürün seçmelisiniz")
    private List<Item> items;

    @Data
    public static class Item {
        private Long stockId;

        @NotNull(message = "Ürün zorunludur")
        private Long productId;

        @NotNull(message = "Adet zorunludur")
        @Min(value = 1, message = "Adet en az 1 olmalıdır")
        private Integer quantity;
    }
}
