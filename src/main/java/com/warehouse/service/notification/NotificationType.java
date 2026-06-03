package com.warehouse.service.notification;

/**
 * Notification types that can be sent.
 * The user can keep a preference (enabled/disabled) for each type.
 */
public enum NotificationType {
    /** Order created / confirmed */
    ORDER_CONFIRMED,
    /** Order status changed (preparing, shipped, delivered) */
    ORDER_STATUS_CHANGE,
    /** Cargo tracking number added */
    CARGO_SHIPPED,
    /** Delivered */
    ORDER_DELIVERED,
    /** Payment received */
    PAYMENT_RECEIVED,
    /** Marketing / campaign */
    MARKETING
}
