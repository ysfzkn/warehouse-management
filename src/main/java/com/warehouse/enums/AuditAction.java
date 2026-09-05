package com.warehouse.enums;

public enum AuditAction {
    STOCK_CREATE,
    STOCK_UPDATE,
    STOCK_ADD,
    STOCK_REMOVE,
    STOCK_DELETE,
    STOCK_RESERVE,
    STOCK_RELEASE,
    TRANSFER_CREATE,
    TRANSFER_START,
    TRANSFER_COMPLETE,
    TRANSFER_CANCEL,
    TRANSFER_UPDATE,
    TRANSFER_DELETE,
    TRANSFER_APPROVE,
    TRANSFER_REJECT,
    /** Depodan çıkan malın geri gelmesi. Sevkiyat iptali değil, üzerine yazılan olay. */
    TRANSFER_RETURN,

    // Teslimat makbuzu. Kâğıdın hayat döngüsü denetim kaydında görünmeli: kim bastı,
    // kim yeniden bastı, teslimatı kim onayladı, imzalı nüshayı kim yükledi.
    RECEIPT_ISSUE,
    RECEIPT_REISSUE,
    RECEIPT_DOWNLOAD,
    RECEIPT_DELIVERY_CONFIRM,
    RECEIPT_ATTACHMENT_UPLOAD,
    RECEIPT_ATTACHMENT_DELETE
}


