package com.warehouse.service;

public interface EmailService {
    void sendEmailVerification(String toEmail, String firstName, String verificationToken);
    void sendPasswordReset(String toEmail, String firstName, String resetToken);
    void sendOrderConfirmation(String toEmail, String firstName, String orderNumber);
    void sendOrderStatusUpdate(String toEmail, String firstName, String orderNumber, String newStatus, String note);
    void sendPasswordResetConfirmation(String toEmail, String firstName);

    /**
     * Misafir sipariş sonrası müşteriye "hesabını tamamla" e-postası gönderir.
     * Müşteri linke tıklayarak şifresini oluşturabilir ve hesabını aktive edebilir.
     */
    void sendCompleteAccountSetup(String toEmail, String firstName, String orderNumber, String setupToken);

    /**
     * Stoğu biten bir ürün tekrar stoğa girdiğinde bildirim e-postası gönderir.
     *
     * @param toEmail       alıcı e-posta
     * @param productName   ürün adı
     * @param productUrl    ürün detay sayfasının tam URL'i
     * @param productImage  ürün görsel URL'i (opsiyonel)
     * @param price         fiyat (formatlanmış, örn: "1.299,00 ₺")
     */
    void sendBackInStockNotification(String toEmail, String productName, String productUrl,
                                      String productImage, String price);

    /**
     * Terk edilmiş sepet hatırlatma e-postası gönderir.
     * Sepetteki ürünleri ve doğrudan sepet linkini içerir.
     *
     * @param toEmail     alıcı e-posta
     * @param firstName   müşteri adı
     * @param itemCount   sepetteki ürün sayısı
     * @param itemsHtml   sepet ürünlerinin HTML özeti (ad, görsel, fiyat)
     * @param cartTotal   sepet toplam tutarı (formatlanmış string)
     */
    void sendAbandonedCartReminder(String toEmail, String firstName, int itemCount, String itemsHtml, String cartTotal);

    /**
     * Deliver a customer-submitted contact form message to the site operator.
     *
     * @param toEmail   recipient (value of the {@code contact_form_email} site setting)
     * @param fromName  name the visitor entered
     * @param fromEmail e-mail the visitor entered — used as Reply-To
     * @param phone     optional phone number
     * @param subject   subject line entered by the visitor
     * @param message   raw message body (will be HTML-escaped before rendering)
     * @return {@code true} if the mail was handed off to the SMTP layer, {@code false} otherwise
     */
    boolean sendContactFormMessage(String toEmail, String fromName, String fromEmail,
                                   String phone, String subject, String message);

    /**
     * Sipariş için e-Fatura GİB tarafından onaylandığında müşteriye bildirim mail'i.
     * İçerik: fatura numarası, tutar, sipariş numarası, "Hesabımdan İndir" CTA.
     */
    void sendInvoiceReady(String toEmail, String firstName, String orderNumber,
                          String invoiceNumber, String invoiceType, String totalFormatted);

    /**
     * Admin'e günlük e-Fatura durum özeti: ERROR/REJECTED ve 24h+ PENDING faturalar.
     * Hiç sorun yoksa gönderilmez (zero-noise digest).
     */
    void sendAdminInvoiceDigest(String toEmail, java.util.List<java.util.Map<String,Object>> errorRows,
                                java.util.List<java.util.Map<String,Object>> stuckPendingRows);

    boolean isEnabled();
}
