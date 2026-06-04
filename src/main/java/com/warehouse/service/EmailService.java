package com.warehouse.service;

public interface EmailService {
    void sendEmailVerification(String toEmail, String firstName, String verificationToken);
    void sendPasswordReset(String toEmail, String firstName, String resetToken);
    void sendOrderConfirmation(String toEmail, String firstName, String orderNumber);
    void sendOrderStatusUpdate(String toEmail, String firstName, String orderNumber, String newStatus, String note);
    void sendPasswordResetConfirmation(String toEmail, String firstName);

    /**
     * Sends a "complete your account" e-mail to the customer after a guest order.
     * By clicking the link the customer can set a password and activate their account.
     */
    void sendCompleteAccountSetup(String toEmail, String firstName, String orderNumber, String setupToken);

    /**
     * Sends a notification e-mail when an out-of-stock product is back in stock.
     *
     * @param toEmail       recipient e-mail
     * @param productName   product name
     * @param productUrl    full URL of the product detail page
     * @param productImage  product image URL (optional)
     * @param price         price (formatted, e.g. "1.299,00 ₺")
     */
    void sendBackInStockNotification(String toEmail, String productName, String productUrl,
                                      String productImage, String price);

    /**
     * Sends an abandoned cart reminder e-mail.
     * Includes the cart items and a direct cart link.
     *
     * @param toEmail     recipient e-mail
     * @param firstName   customer name
     * @param itemCount   number of items in the cart
     * @param itemsHtml   HTML summary of the cart items (name, image, price)
     * @param cartTotal   cart total amount (formatted string)
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
     * Notification mail to the customer when the order's e-Fatura is approved by GİB.
     * Contents: invoice number, amount, order number, "Download from My Account" CTA.
     */
    void sendInvoiceReady(String toEmail, String firstName, String orderNumber,
                          String invoiceNumber, String invoiceType, String totalFormatted);

    /**
     * Daily e-Fatura status digest to the admin: ERROR/REJECTED and 24h+ PENDING invoices.
     * Not sent if there are no issues (zero-noise digest).
     */
    void sendAdminInvoiceDigest(String toEmail, java.util.List<java.util.Map<String,Object>> errorRows,
                                java.util.List<java.util.Map<String,Object>> stuckPendingRows);

    boolean isEnabled();
}
