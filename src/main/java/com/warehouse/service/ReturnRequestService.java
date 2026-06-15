package com.warehouse.service;

import com.warehouse.entity.ReturnRequest;
import com.warehouse.enums.ReturnReason;
import com.warehouse.enums.ReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Customer returns / RMA (iade) workflow.
 *
 * <p>Lifecycle: a customer opens a {@link ReturnRequest} against a SHIPPED/DELIVERED
 * order (status PENDING, order → RETURN_REQUESTED). An admin then APPROVES (customer
 * ships the items back) or REJECTS it; on physical receipt the admin marks it
 * RECEIVED (stock is restored, order → RETURNED); finally a REFUND moves money back
 * via the original payment gateway (order → REFUNDED). Each transition notifies the
 * customer by email; new requests notify the admin in-app.
 */
public interface ReturnRequestService {

    /** One requested line: which order item and how many units to return. */
    record ReturnItemRequest(Long orderItemId, int quantity, String reason) {}

    ReturnRequest createReturn(Long customerId, String orderNumber, ReturnReason reason,
                               String customerNote, List<ReturnItemRequest> items);

    Page<ReturnRequest> listForCustomer(Long customerId, Pageable pageable);

    ReturnRequest getForCustomer(Long customerId, String returnNumber);

    Page<ReturnRequest> listForAdmin(ReturnStatus status, Pageable pageable);

    ReturnRequest getByIdForAdmin(Long id);

    ReturnRequest approve(Long id, String adminNote);

    ReturnRequest reject(Long id, String adminNote);

    /** Mark the returned goods as physically received; restores stock, order → RETURNED. */
    ReturnRequest markReceived(Long id, String adminNote);

    /** Refund the customer via the original payment method; return → REFUNDED, order → REFUNDED. */
    ReturnRequest refund(Long id, BigDecimal amount, String adminNote, String ipAddress);

    /** Counts per status for the admin dashboard badges. */
    long countByStatus(ReturnStatus status);

    // ── Evidence photos ──

    /** Bytes + metadata of a stored return photo, for streaming to the browser. */
    record PhotoData(byte[] bytes, String contentType, String fileName) {}

    /** Customer attaches an evidence photo to their own return. Returns the photo id. */
    Long addPhoto(Long customerId, String returnNumber, String fileName, String contentType,
                  java.io.InputStream inputStream);

    /** Loads a photo's bytes by id (used by the view endpoint). */
    PhotoData getPhoto(Long photoId);

    // ── Admin reply ──

    /** Admin sends a written reply to the customer about a return (emails them; status unchanged). */
    ReturnRequest reply(Long id, String message);
}
