package com.warehouse.dto;

import com.warehouse.enums.DeliveryPlanFilter;
import com.warehouse.enums.DeliveryReceiptKind;
import com.warehouse.enums.DeliveryReceiptStatus;
import com.warehouse.enums.ReceiptDateField;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Makbuz arşivinin filtreleri.
 *
 * <p>Tek tek parametre yerine bir nesne, çünkü filtre sayısı yediye çıktı ve hepsi
 * opsiyonel: yedi parametreli bir imzada çağıranın {@code null, null, true, null…} yazması
 * kaçınılmaz, ve oradaki bir kayma sessizce yanlış kesit döndürür.</p>
 *
 * <p>Alanların hiçbiri zorunlu değil; boş bir filtre "hepsi" demek. Servis katmanı bunu bir
 * {@code Specification}'a çeviriyor ve yalnızca dolu olan alanlar için yüklem üretiyor —
 * {@code (:param IS NULL OR …)} kalıbının PostgreSQL'de patlamasının sebebi buydu, bkz.
 * {@link com.warehouse.repository.DeliveryReceiptRepository}.</p>
 */
@Data
@Builder
public class DeliveryReceiptFilter {

    /** Belgenin hayat döngüsü: düzenlendi / teslim edildi / iptal. */
    private DeliveryReceiptStatus status;

    /** Hangi kâğıt: teslimat makbuzu (TM) ya da depo çıkış makbuzu (DC). */
    private DeliveryReceiptKind kind;

    /** İmzalı nüsha dosyaya girdi mi. */
    private Boolean hasSignedCopy;

    /**
     * Depo çıkışında şoför/plaka hâlâ girilmemiş olanlar.
     *
     * <p>Taşıyıcı sonradan girildiğinde makbuz kaydına da işleniyor, bu yüzden makbuzun
     * kendi alanına bakmak yeterli — sevkiyata join gerekmiyor.</p>
     */
    private Boolean carrierPending;

    /** Teslim planına göre kesit; durumdan bağımsız bir eksen. */
    private DeliveryPlanFilter plan;

    /**
     * Tarih aralığının hangi tarihe bakacağı. Boşsa {@link ReceiptDateField#ISSUED}.
     *
     * <p>Planlı teslimat geldiğinden beri bir makbuzun üç tarihi ayrı günlere düşebiliyor;
     * hangisinin sorulduğunu kullanıcı seçiyor.</p>
     */
    private ReceiptDateField dateField;

    private LocalDateTime from;
    private LocalDateTime to;

    /** Makbuz no, müşteri, şoför, plaka, sipariş no — ASCII'ye katlanmış arama. */
    private String search;

    /** Boşsa varsayılan alan. */
    public ReceiptDateField dateFieldOrDefault() {
        return dateField != null ? dateField : ReceiptDateField.ISSUED;
    }
}
