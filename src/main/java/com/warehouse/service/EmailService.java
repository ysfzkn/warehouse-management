package com.warehouse.service;

public interface EmailService {
    void sendEmailVerification(String toEmail, String firstName, String verificationToken);
    void sendPasswordReset(String toEmail, String firstName, String resetToken);
    void sendOrderConfirmation(String toEmail, String firstName, String orderNumber);
    void sendOrderStatusUpdate(String toEmail, String firstName, String orderNumber, String newStatus, String note);
    void sendPasswordResetConfirmation(String toEmail, String firstName);

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

    boolean isEnabled();
}
