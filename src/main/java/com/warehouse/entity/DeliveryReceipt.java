package com.warehouse.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.warehouse.enums.DeliveryReceiptKind;
import com.warehouse.enums.DeliveryReceiptStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The delivery receipt handed to the driver for a customer shipment, and the record of
 * what came back signed.
 *
 * <p>Almost every field here also exists on {@link StockTransfer}. That duplication is
 * the point: the receipt freezes the shipment as it was when the paper was printed. If
 * the driver is swapped or the customer's address is corrected afterwards, the signed
 * page and the system would otherwise disagree, and the signed page is the one that
 * matters in a dispute. Reprinting bumps {@link #revision} rather than minting a new
 * number, so one shipment never has two receipt numbers in circulation.</p>
 */
@Entity
@Table(name = "delivery_receipts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(exclude = {"transfer", "attachments"})
public class DeliveryReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_transfer_id", nullable = false)
    private StockTransfer transfer;

    /** Human-facing number printed on the paper, e.g. {@code TM-2026-000042}. */
    @Column(name = "receipt_no", nullable = false, length = 30, unique = true)
    private String receiptNo;

    /**
     * Which paper this is. Drives the title, the number series and — the reason it exists
     * at all — how many copies get printed.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryReceiptKind kind = DeliveryReceiptKind.DELIVERY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryReceiptStatus status = DeliveryReceiptStatus.ISSUED;

    /** Incremented on every reprint; shown on the page so duplicates are distinguishable. */
    @Column(nullable = false)
    private Integer revision = 1;

    // ── Company letterhead, copied at print time ──────────────────────────────
    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "company_address", length = 500)
    private String companyAddress;

    @Column(name = "company_phone", length = 100)
    private String companyPhone;

    // ── Shipment snapshot ─────────────────────────────────────────────────────
    @Column(name = "source_warehouse_name", length = 150)
    private String sourceWarehouseName;

    @Column(name = "customer_full_name", length = 150)
    private String customerFullName;

    @Column(name = "customer_phone", length = 30)
    private String customerPhone;

    @Column(name = "customer_address", length = 500)
    private String customerAddress;

    @Column(name = "order_number", length = 50)
    private String orderNumber;

    @Column(name = "driver_name", length = 100)
    private String driverName;

    @Column(name = "driver_phone", length = 30)
    private String driverPhone;

    @Column(name = "vehicle_plate", length = 20)
    private String vehiclePlate;

    @Column(name = "transfer_date")
    private LocalDateTime transferDate;

    /**
     * Line items as they read on the printed page, serialised as JSON.
     *
     * <p>A join back to {@code products} would re-render the receipt with today's product
     * names and today's prices; a receipt has to keep saying what the customer actually
     * signed for.</p>
     */
    @Column(name = "items_json", nullable = false, columnDefinition = "TEXT")
    private String itemsJson;

    @Column(length = 1000)
    private String notes;

    // ── Filled in once the delivery is confirmed ──────────────────────────────
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** Who handed the goods over — usually the driver, occasionally a warehouse hand. */
    @Column(name = "delivered_by_name", length = 150)
    private String deliveredByName;

    /** Who signed for them at the other end. */
    @Column(name = "received_by_name", length = 150)
    private String receivedByName;

    @Column(name = "received_by_note", length = 500)
    private String receivedByNote;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by", length = 100)
    private String confirmedBy;

    // ── Depo çıkışı: malı devralan servis ve devreden görevli ─────────────────
    //
    // Kept apart from deliveredByName / receivedByName above rather than reusing them.
    // Those two belong to the "delivery confirmed" step and are filled in later, possibly
    // months later; writing both events into one pair of columns would mean a reprint of
    // the depot exit receipt showing the customer's name where the service company's
    // signature actually sits on the signed page.

    /** The service company or person who collected the goods from the warehouse. */
    @Column(name = "handover_to_name", length = 150)
    private String handoverToName;

    @Column(name = "handover_to_phone", length = 30)
    private String handoverToPhone;

    /** Our own person who handed them over. */
    @Column(name = "handed_over_by_name", length = 150)
    private String handedOverByName;

    /**
     * Everything searchable about this receipt, folded onto ASCII.
     *
     * <p>The screen's search box compares against this instead of the individual columns.
     * Turkish makes the direct comparison unreliable in a way no amount of care at the call
     * site fixes: lower-casing is not reversible — "I" is the capital of both "ı" and "i",
     * and "İ" lower-cases to a combining sequence — so "IŞIK" could never find "Işık".
     * Folding both the stored value and the query to plain ASCII makes "AYSE", "ayşe" and
     * "Ayşe" the same string.</p>
     *
     * <p>Maintained here rather than by a caller so no write path can forget: a receipt with
     * a stale search column silently drops out of the list.</p>
     */
    @Column(name = "search_text", length = 500)
    private String searchText;

    // ── Bookkeeping ───────────────────────────────────────────────────────────
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "issued_by", length = 100)
    private String issuedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<DeliveryReceiptAttachment> attachments = new ArrayList<>();

    /**
     * Rebuilds {@link #searchText}. The parts are the ones the screen's search box advertises
     * — receipt number, customer, order, driver, plate — plus the two parties the archive now
     * shows in its "Teslim Alan" column.
     */
    private void refreshSearchText() {
        this.searchText = com.warehouse.util.TurkishText.normalizeForSearch(
                receiptNo, customerFullName, customerPhone, orderNumber,
                driverName, vehiclePlate, handoverToName, handoverToPhone, receivedByName);
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.issuedAt == null) {
            this.issuedAt = now;
        }
        refreshSearchText();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        refreshSearchText();
    }

    public void addAttachment(DeliveryReceiptAttachment attachment) {
        if (attachment == null) return;
        attachment.setReceipt(this);
        this.attachments.add(attachment);
    }
}
