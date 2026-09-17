package com.warehouse.enums;

/**
 * Makbuz arşivinde teslim planına göre kesit.
 *
 * <p>Makbuzun kendi durumu ({@link DeliveryReceiptStatus}) belgenin hayat döngüsünü anlatıyor:
 * düzenlendi, teslim edildi, iptal. Plan ise bambaşka bir eksen — <em>ne zaman</em> teslim
 * edilecek. İkisi birbirinin yerine geçemiyor: planı bugüne düşmüş bir makbuz da, tarihi üç
 * hafta geçmiş bir makbuz da "Düzenlendi" durumunda duruyor, oysa biri normal iş, öteki
 * kovalanması gereken bir kayıp.</p>
 *
 * <p>Bu yüzden ayrı bir filtre: durum listesine "gecikmiş" diye bir değer eklemek, belgenin
 * durumu ile teslimatın durumunu aynı alana yığmak olurdu.</p>
 */
public enum DeliveryPlanFilter {

    /** Planı olan, henüz teslim edilmemiş ve iptal olmamış makbuzlar. */
    SCHEDULED,

    /** Planı bugüne düşenler. */
    DUE_TODAY,

    /** Tarihi geçmiş ve hâlâ kapanmamış olanlar — arşivin kovalama listesi. */
    OVERDUE,

    /** Planı olmayanlar: mal makbuz imzalanırken çıkmış klasik akış. */
    NONE
}
