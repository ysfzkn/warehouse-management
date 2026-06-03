package com.warehouse.service;

/**
 * Back-in-stock notification service.
 *
 * Usage:
 * 1. Customer calls `subscribe()` for an out-of-stock product
 * 2. When stock is updated, `checkAndNotifyProduct()` is called (scheduled job or hook)
 * 3. If the product is back in stock, all subscribers are emailed and marked notified=true
 */
public interface StockNotificationService {

    /**
     * Subscribes to a stock notification for a product.
     * Idempotent if an active subscription already exists for the same email + product (does not throw).
     *
     * @return true: a new subscription was created; false: it already existed
     */
    boolean subscribe(Long productId, String email, Long customerId);

    /**
     * Checks the stock status of a specific product;
     * if in stock, notifies all pending subscriptions and marks them notified=true.
     *
     * @return the number of subscriptions notified
     */
    int checkAndNotifyProduct(Long productId);

    /**
     * Checks all products that have pending subscriptions.
     * Called by a scheduled job.
     *
     * @return the total number of notifications sent
     */
    int processPendingSubscriptions();
}
