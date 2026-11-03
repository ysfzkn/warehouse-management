package com.warehouse.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Bir depo çıkış makbuzunun içerik düzenlemesi: kim, ne zaman, neden ve neyi neye çevirdi.
 *
 * <p>Genel denetim kaydının yerine değil yanında. Orada da bir satır var, ama serbest metin
 * olarak; buradaki önce/sonra kalemler ekranda kalem kalem karşılaştırılabilsin diye
 * makbuzun kendi biçiminde (JSON) saklanıyor. "Önce" makbuzun düzenleme anındaki hâli,
 * yani elde dolaşan kâğıtta yazan.</p>
 *
 * <p>Yalnızca ekleniyor, hiçbir yol güncellemiyor ya da silmiyor.</p>
 */
@Entity
@Table(name = "delivery_receipt_revisions")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@ToString(exclude = "receipt")
public class DeliveryReceiptRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false, updatable = false)
    private DeliveryReceipt receipt;

    /** Düzenlemenin ürettiği basım numarası. */
    @Column(nullable = false, updatable = false)
    private Integer revision;

    @Column(name = "edited_at", nullable = false, updatable = false)
    private LocalDateTime editedAt;

    @Column(name = "edited_by", nullable = false, length = 100, updatable = false)
    private String editedBy;

    @Column(nullable = false, length = 300, updatable = false)
    private String reason;

    @Column(name = "source_warehouse_before", length = 150, updatable = false)
    private String sourceWarehouseBefore;

    @Column(name = "source_warehouse_after", length = 150, updatable = false)
    private String sourceWarehouseAfter;

    @Column(name = "items_before_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String itemsBeforeJson;

    @Column(name = "items_after_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String itemsAfterJson;
}
