package com.warehouse.service;

public interface EmailService {
    void sendEmailVerification(String toEmail, String firstName, String verificationToken);
    void sendPasswordReset(String toEmail, String firstName, String resetToken);
    void sendOrderConfirmation(String toEmail, String firstName, String orderNumber);
    void sendOrderStatusUpdate(String toEmail, String firstName, String orderNumber, String newStatus, String note);
    boolean isEnabled();
}
