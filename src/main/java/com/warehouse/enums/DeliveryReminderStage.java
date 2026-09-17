package com.warehouse.enums;

/**
 * Planlı bir teslimat için gönderilen hatırlatmanın hangi aşama olduğu.
 *
 * <p>Aşamalar sırayla ve her biri tam bir kez gider. Sıranın kendisi bir kural: teslim günü
 * gelmişken "yarın teslim edilecek" hatırlatması göndermek bilgi değil gürültü olurdu, bu
 * yüzden zamanı geçmiş aşama postalanmadan damgalanıp kapatılır.</p>
 */
public enum DeliveryReminderStage {

    /** Teslimden bir gün önce — hazırlık için. */
    DAY_BEFORE("Yarın teslim edilecek", "1 gün kaldı"),

    /** Teslim günü sabahı. */
    DUE_TODAY("Bugün teslim edilecek", "Teslim günü"),

    /**
     * Tarihi geçti, teslimat hâlâ kapatılmadı.
     *
     * <p>İlk ikisi hatırlatma, bu bir uyarı: mal rezervede duruyor, stok ne çıkmış ne de
     * serbest. Bu aşama olmasaydı unutulan bir plan ancak sayımda fark edilirdi.</p>
     */
    OVERDUE("Teslim tarihi geçti", "Gecikmiş teslimat");

    private final String title;
    private final String badge;

    DeliveryReminderStage(String title, String badge) {
        this.title = title;
        this.badge = badge;
    }

    /** Bildirim başlığı ve mail konusu olarak kullanılan cümle. */
    public String getTitle() {
        return title;
    }

    /** Mailin üstündeki kısa etiket. */
    public String getBadge() {
        return badge;
    }
}
