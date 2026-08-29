package com.warehouse.service.impl;

import com.warehouse.dto.payment.*;
import com.warehouse.entity.*;
import com.warehouse.enums.*;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.config.PaymentProperties;
import com.warehouse.repository.*;
import com.warehouse.util.OrderStatusHistoryFactory;
import com.warehouse.service.PaymentService;
import com.warehouse.service.payment.PaymentGateway;
import com.warehouse.service.payment.PaymentGatewayFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentTransactionRepository paymentRepo;
    private final OrderRepository orderRepo;
    private final OrderStatusHistoryRepository statusHistoryRepo;
    private final StockRepository stockRepo;
    private final OrderItemRepository orderItemRepo;
    private final PaymentGatewayFactory gatewayFactory;
    private final PaymentProperties paymentProperties;
    private final PaymentGatewayConfigRepository gatewayConfigRepo;
    private final com.warehouse.service.CartService cartService;
    private final StockEventRepository stockEventRepo;
    private final com.warehouse.service.InvoiceService invoiceService;
    private final com.warehouse.service.notification.NotificationDispatchService notificationDispatchService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.warehouse.service.SiteSettingService siteSettingService;

    public PaymentServiceImpl(PaymentTransactionRepository paymentRepo,
                               OrderRepository orderRepo,
                               OrderStatusHistoryRepository statusHistoryRepo,
                               StockRepository stockRepo,
                               OrderItemRepository orderItemRepo,
                               PaymentGatewayFactory gatewayFactory,
                               PaymentProperties paymentProperties,
                               PaymentGatewayConfigRepository gatewayConfigRepo,
                               com.warehouse.service.CartService cartService,
                               StockEventRepository stockEventRepo,
                               com.warehouse.service.InvoiceService invoiceService,
                               com.warehouse.service.notification.NotificationDispatchService notificationDispatchService,
                               org.springframework.context.ApplicationEventPublisher eventPublisher,
                               com.warehouse.service.SiteSettingService siteSettingService) {
        this.paymentRepo = paymentRepo;
        this.orderRepo = orderRepo;
        this.stockEventRepo = stockEventRepo;
        this.statusHistoryRepo = statusHistoryRepo;
        this.stockRepo = stockRepo;
        this.orderItemRepo = orderItemRepo;
        this.gatewayFactory = gatewayFactory;
        this.paymentProperties = paymentProperties;
        this.gatewayConfigRepo = gatewayConfigRepo;
        this.cartService = cartService;
        this.invoiceService = invoiceService;
        this.notificationDispatchService = notificationDispatchService;
        this.eventPublisher = eventPublisher;
        this.siteSettingService = siteSettingService;
    }

    @Override
    public PaymentInitResult initializePayment(Long orderId, String paymentMethod, int installmentCount,
                                                String ipAddress, String idempotencyKey,
                                                PaymentCaller caller) {
        // 1. Load the order and prove the caller is entitled to pay for it.
        //
        // This endpoint is public by necessity (guests pay without an account) and used
        // to act on whatever orderId it was handed. Order ids are sequential, so an
        // attacker could walk pending orders and re-initialise a stranger's order as
        // DOOR_CASH — which moves it straight to PREPARING and ships goods that were
        // never paid for. Nothing below runs until access is established.
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş bulunamadı."));
        requireOrderAccess(order, caller);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new WarehouseManagementException(ErrorCode.PAYMENT_ALREADY_PROCESSED,
                "Sipariş durumu ödeme için uygun değil: " + order.getStatus());
        }

        // 2. Idempotency check — scoped to this order, so a guessed key belonging to
        // someone else's payment cannot be replayed to read back their gateway token.
        Optional<PaymentTransaction> existing = paymentRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            PaymentTransaction ex = existing.get();
            if (ex.getOrder() == null || !orderId.equals(ex.getOrder().getId())) {
                throw new WarehouseManagementException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            if (ex.getStatus() == PaymentStatus.SUCCESS) {
                logger.info("Idempotent hit: payment {} already SUCCESS for key {}", ex.getId(), idempotencyKey);
                return PaymentInitResult.builder().success(true).token(ex.getToken()).build();
            }
            if (ex.getStatus() == PaymentStatus.PROCESSING) {
                logger.info("Idempotent hit: payment {} still PROCESSING for key {}", ex.getId(), idempotencyKey);
                return PaymentInitResult.builder().success(true).token(ex.getToken())
                    .htmlContent(null).build();
            }
            throw new WarehouseManagementException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }

        // 3. Determine provider — after checking the method is actually offered, so a
        // switched-off method (cash on delivery, bank transfer) cannot be forced by hand.
        requirePaymentMethodEnabled(paymentMethod);
        PaymentProvider provider = resolveProvider(paymentMethod);
        PaymentGateway gateway = gatewayFactory.getGateway(provider);

        // 4. Create payment transaction
        PaymentTransaction tx = new PaymentTransaction();
        tx.setOrder(order);
        tx.setIdempotencyKey(idempotencyKey);
        tx.setPaymentProvider(provider);
        tx.setStatus(PaymentStatus.INITIATED);
        tx.setAmount(order.getGrandTotal());
        tx.setCurrency("TRY");
        tx.setInstallmentCount(installmentCount);
        tx.setIpAddress(ipAddress);
        tx = paymentRepo.save(tx);

        // 5. Build request and call gateway
        PaymentInitRequest request = buildInitRequest(order, installmentCount, ipAddress);
        PaymentInitResult result;
        try {
            result = gateway.initializePayment(request);
        } catch (Exception e) {
            // Gateway exception (network, NPE, etc.) → tx FAILED + order CANCELLED + stock release
            tx.setStatus(PaymentStatus.FAILED);
            tx.setErrorMessage(e.getMessage());
            paymentRepo.save(tx);
            try { releaseOrderStock(order); }
            catch (Exception se) { logger.warn("Stock release failed: {}", se.getMessage()); }
            OrderStatus oldStatus = order.getStatus();
            order.setStatus(OrderStatus.CANCELLED);
            orderRepo.save(order);
            logStatusChange(order, oldStatus != null ? oldStatus.name() : "PENDING_PAYMENT",
                    OrderStatus.CANCELLED.name(), "system",
                    "Ödeme gateway exception: " + e.getMessage());
            logger.error("Payment gateway exception for orderId={}: {}", orderId, e.getMessage(), e);
            throw new WarehouseManagementException(ErrorCode.PAYMENT_INIT_FAILED, e.getMessage());
        }

        // 6. Update transaction based on result
        if (result.isSuccess()) {
            switch (provider) {
                case IYZICO:
                    tx.setStatus(PaymentStatus.PROCESSING);
                    tx.setToken(result.getToken());
                    tx.setExpiresAt(LocalDateTime.now().plusMinutes(paymentProperties.getIyzico().getTimeoutMinutes()));
                    break;
                case PAYTR:
                case NESTPAY:
                case GVP:
                    // Direct bank POS / PayTR iframe — card entry happens on the gateway side,
                    // the result arrives via a server-to-server callback (notify_url).
                    // PayTR retry: every 1 minute for 24 hours until it receives "OK".
                    tx.setStatus(PaymentStatus.PROCESSING);
                    // result.getToken() — set by VirtualPosGateway as the transactionId (=merchant_oid=orderNumber)
                    tx.setToken(result.getToken());
                    tx.setExpiresAt(LocalDateTime.now().plusMinutes(30)); // PayTR iframe timeout default
                    break;
                case BANK_TRANSFER:
                    tx.setStatus(PaymentStatus.INITIATED);
                    tx.setBankTransferRef(result.getBankTransferReference());
                    tx.setExpiresAt(LocalDateTime.now().plusHours(paymentProperties.getBankTransfer().getDeadlineHours()));
                    order.setBankTransferDeadline(tx.getExpiresAt());
                    orderRepo.save(order);
                    // Clear cart — order accepted, waiting for transfer
                    try { if (order.getCustomer() != null) cartService.clearCart(order.getCustomer().getId(), null); }
                    catch (Exception e) { logger.warn("Cart clear failed: {}", e.getMessage()); }
                    break;
                case DOOR_PAYMENT:
                    // Cash on delivery: payment not collected yet, the order starts being prepared
                    tx.setStatus(PaymentStatus.INITIATED);
                    order.setStatus(OrderStatus.PREPARING);
                    orderRepo.save(order);
                    logStatusChange(order, OrderStatus.PENDING_PAYMENT.name(), OrderStatus.PREPARING.name(),
                            "system", "Kapıda ödeme — teslimat sırasında tahsil edilecek");
                    // Clear cart — door payment accepted, order is being prepared
                    try { if (order.getCustomer() != null) cartService.clearCart(order.getCustomer().getId(), null); }
                    catch (Exception e) { logger.warn("Cart clear failed: {}", e.getMessage()); }
                    break;
                default:
                    break;
            }
        } else {
            // Gateway init fail (missing config, network error, card decline, etc.)
            // → move the order to CANCELLED and release the stock reservation. Otherwise,
            // when the user retries, an orphan order + stock lock is left behind.
            tx.setStatus(PaymentStatus.FAILED);
            tx.setErrorCode(result.getErrorCode());
            tx.setErrorMessage(result.getErrorMessage());

            try {
                releaseOrderStock(order);
            } catch (Exception e) {
                logger.warn("Stock release failed for order {} during init failure: {}", orderId, e.getMessage());
            }
            OrderStatus oldStatus = order.getStatus();
            order.setStatus(OrderStatus.CANCELLED);
            orderRepo.save(order);
            logStatusChange(order, oldStatus != null ? oldStatus.name() : "PENDING_PAYMENT",
                    OrderStatus.CANCELLED.name(), "system",
                    "Ödeme başlatılamadı: " + (result.getErrorMessage() != null ? result.getErrorMessage() : "bilinmeyen hata"));
            logger.warn("Payment init failed: orderId={}, txId={}, provider={}, error={}",
                    orderId, tx.getId(), provider, result.getErrorMessage());
        }

        if (result.getRawResponse() != null) {
            tx.setRawResponse(result.getRawResponse());
        }
        paymentRepo.save(tx);

        logger.info("Payment initialized: orderId={}, provider={}, status={}, txId={}",
            orderId, provider, tx.getStatus(), tx.getId());

        // Add provider info so the frontend can render the UI per brand
        result.setProviderName(provider.name());
        result.setProviderDisplayName(providerDisplayName(provider));
        return result;
    }

    /** Provider enum → user-visible name. */
    private String providerDisplayName(PaymentProvider provider) {
        return switch (provider) {
            case IYZICO -> "iyzico";
            case PAYTR -> "PayTR";
            case NESTPAY -> "NestPay";
            case GVP -> "Garanti Sanal POS";
            case BANK_TRANSFER -> "Havale / EFT";
            case DOOR_PAYMENT -> "Kapıda Ödeme";
        };
    }

    @Override
    public PaymentCallbackResult handlePaymentCallback(Map<String, String> params) {
        String token = params.get("token");
        if (token == null || token.isBlank()) {
            throw new WarehouseManagementException(ErrorCode.PAYMENT_CALLBACK_INVALID, "Token eksik");
        }

        // Find payment by token
        PaymentTransaction tx = paymentRepo.findByToken(token)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PAYMENT_NOT_FOUND, "Token: " + token));

        // ── Idempotency protection (PayTR retries every minute until it receives "OK") ──
        // If a callback arrives again for a tx that has already SUCCEEDED:
        //   - A PAID order should not be set to PAID again (bloats the audit log)
        //   - OrderPaidEvent should not be published again (duplicate invoice/email/stock movement)
        //   - The customer should not receive another confirmation email
        if (tx.getStatus() == PaymentStatus.SUCCESS) {
            logger.info("Idempotent callback hit (already SUCCESS): txId={}, token={}", tx.getId(), token);
            return PaymentCallbackResult.builder()
                    .success(true)
                    .token(token)
                    .paymentId(tx.getProviderPaymentId())
                    .paidPrice(tx.getPaidAmount())
                    .threeDSecure(tx.isThreeDSecure())
                    .cardLastFour(tx.getCardLastFour())
                    .cardType(tx.getCardType())
                    .build();
        }
        if (tx.getStatus() == PaymentStatus.FAILED || tx.getStatus() == PaymentStatus.REFUNDED) {
            logger.info("Idempotent callback hit (terminal {}): txId={}, token={}",
                    tx.getStatus(), tx.getId(), token);
            return PaymentCallbackResult.builder()
                    .success(false)
                    .token(token)
                    .errorCode(tx.getErrorCode())
                    .errorMessage(tx.getErrorMessage())
                    .build();
        }

        PaymentGateway gateway = gatewayFactory.getGateway(tx.getPaymentProvider());
        PaymentCallbackResult result = gateway.handleCallback(params);

        Order order = tx.getOrder();

        // Dynamic provider name — for logs + events (was previously hardcoded "iyzico")
        final String providerName = tx.getPaymentProvider() != null
                ? tx.getPaymentProvider().name().toLowerCase()
                : "unknown";

        if (result.isSuccess()) {
            // Handle late callback (after timeout cleanup)
            if (tx.getStatus() == PaymentStatus.TIMEOUT) {
                logger.warn("Late callback received for timed-out payment txId={}, attempting re-reservation", tx.getId());
                boolean canReReserve = tryReReserveStock(order);
                if (!canReReserve) {
                    logger.error("Cannot re-reserve stock for late callback txId={}, initiating refund", tx.getId());
                    tx.setStatus(PaymentStatus.FAILED);
                    tx.setErrorMessage("Stok tukendi, otomatik iade baslatildi");
                    paymentRepo.save(tx);
                    // Auto-refund would happen here via gateway.refund()
                    return result;
                }
            }

            // Verify payment amount matches order total — BLOCK if mismatch
            if (result.getPaidPrice() != null && order.getGrandTotal() != null) {
                BigDecimal diff = result.getPaidPrice().subtract(order.getGrandTotal()).abs();
                if (diff.compareTo(new BigDecimal("1.00")) > 0) {
                    logger.error("CRITICAL: Payment amount mismatch BLOCKED! orderId={}, expected={}, received={}",
                        order.getId(), order.getGrandTotal(), result.getPaidPrice());
                    tx.setStatus(PaymentStatus.FAILED);
                    tx.setErrorMessage("TUTAR_UYUMSUZ: Beklenen=" + order.getGrandTotal() + ", Gelen=" + result.getPaidPrice());
                    paymentRepo.save(tx);
                    return PaymentCallbackResult.builder().success(false)
                        .errorMessage("Ödeme tutarı sipariş tutarı ile uyuşmuyor. İşlem reddedildi.").build();
                }
            }

            tx.setStatus(PaymentStatus.SUCCESS);
            tx.setPaidAt(LocalDateTime.now());
            tx.setPaidAmount(result.getPaidPrice());
            tx.setProviderPaymentId(result.getPaymentId());
            tx.setInstallmentCount(result.getInstallmentCount());
            tx.setCardLastFour(result.getCardLastFour());
            tx.setCardType(result.getCardType());
            tx.setCardAssociation(result.getCardAssociation());
            tx.setCardFamily(result.getCardFamily());
            tx.setThreeDSecure(result.isThreeDSecure());

            order.setStatus(OrderStatus.PAID);
            orderRepo.save(order);
            logStatusChange(order, OrderStatus.PENDING_PAYMENT.name(), OrderStatus.PAID.name(),
                    "system", providerName + " ödeme başarılı");

            logger.info("Payment successful: txId={}, orderId={}, provider={}",
                    tx.getId(), order.getId(), providerName);

            // Publish the OrderPaid event — for automatic invoice issuance (async, AFTER_COMMIT)
            // and to trigger the other subscribers (notification, shipment, etc.).
            // The synchronous invoice call was blocking the transaction; with an event-driven
            // architecture, the checkout response time drops + behaves fail-soft.
            eventPublisher.publishEvent(new com.warehouse.event.OrderPaidEvent(
                    this, order.getId(), order.getOrderNumber(), providerName));

            // Send order confirmation notification (email + SMS) — sync (critical notification)
            try {
                notificationDispatchService.notifyOrderConfirmed(order.getCustomer(), order.getOrderNumber());
            } catch (Exception e) {
                logger.warn("Sipariş onay bildirimi gönderilemedi (sipariş {}): {}", order.getOrderNumber(), e.getMessage());
            }

            // Clear cart after successful payment
            try {
                if (order.getCustomer() != null) {
                    cartService.clearCart(order.getCustomer().getId(), null);
                }
            } catch (Exception e) {
                logger.warn("Cart clear failed after payment success: {}", e.getMessage());
            }
        } else {
            tx.setStatus(PaymentStatus.FAILED);
            tx.setErrorCode(result.getErrorCode());
            tx.setErrorMessage(result.getErrorMessage());

            // Release reserved stock
            releaseOrderStock(order);

            order.setStatus(OrderStatus.CANCELLED);
            orderRepo.save(order);
            logStatusChange(order, OrderStatus.PENDING_PAYMENT.name(), OrderStatus.CANCELLED.name(), "system", "Ödeme başarısız: " + result.getErrorMessage());

            logger.warn("Payment failed: txId={}, error={}", tx.getId(), result.getErrorMessage());
        }

        if (result.getRawResponse() != null) {
            tx.setRawCallback(result.getRawResponse());
        }
        paymentRepo.save(tx);

        return result;
    }

    /** Allowed amount tolerance for bank transfer confirmation (to account for bank swift fee differences). */
    private static final BigDecimal BANK_TRANSFER_AMOUNT_TOLERANCE = new BigDecimal("1.00");

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void confirmBankTransfer(Long orderId, BigDecimal paidAmount,
                                     LocalDateTime paidAt, String confirmedBy,
                                     String receiptNote) {
        // Validations — null/sane checks
        if (orderId == null) throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş ID boş.");
        if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Yatırılan tutar zorunlu ve > 0 olmalı.");
        }
        if (paidAt == null) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Bankadaki gerçek ödeme tarihi (paidAt) zorunlu — ekstreden okuyun.");
        }
        if (paidAt.isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Ödeme tarihi gelecekte olamaz.");
        }
        if (confirmedBy == null || confirmedBy.isBlank()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Admin kullanıcı bilgisi eksik.");
        }

        // PESSIMISTIC LOCK — prevent a race with double-confirm and the expiry job
        Order order = orderRepo.findByIdForUpdate(orderId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş bulunamadı."));

        // Idempotency — if already PAID, silently skip (admin double-click)
        if (order.getStatus() == OrderStatus.PAID) {
            logger.info("Bank transfer confirm idempotent skip: orderId={} already PAID by previous request", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || !PaymentProvider.BANK_TRANSFER.name().equals(order.getPaymentMethod())) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Bu sipariş havale onayına uygun değil. Mevcut durum: " + order.getStatus());
        }

        PaymentTransaction tx = paymentRepo.findByOrderIdAndStatus(orderId, PaymentStatus.INITIATED)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PAYMENT_NOT_FOUND,
                    "Onaylanacak INITIATED durumda bir havale işlemi yok."));

        // AMOUNT VERIFICATION — block if the wrong amount was deposited
        BigDecimal expected = order.getGrandTotal();
        BigDecimal diff = paidAmount.subtract(expected).abs();
        if (diff.compareTo(BANK_TRANSFER_AMOUNT_TOLERANCE) > 0) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, String.format(
                    "Yatırılan tutar (%s TL) sipariş tutarıyla (%s TL) uyuşmuyor (fark: %s TL, tolerans: %s TL). " +
                    "Tutar farklıysa: müşteriden eksik kısmı isteyin veya iade edip 'Havale Reddet' kullanın.",
                    paidAmount, expected, diff, BANK_TRANSFER_AMOUNT_TOLERANCE));
        }

        // If the deadline is exceeded, warn but do not block — the admin is making a deliberate decision
        if (tx.getExpiresAt() != null && tx.getExpiresAt().isBefore(LocalDateTime.now())) {
            logger.warn("Bank transfer confirmed AFTER deadline: orderId={}, deadline={}, paidAt={}, by={}",
                    orderId, tx.getExpiresAt(), paidAt, confirmedBy);
        }

        tx.setStatus(PaymentStatus.SUCCESS);
        tx.setPaidAt(paidAt);         // Actual date from the bank (from the statement)
        tx.setPaidAmount(paidAmount); // Actual amount from the bank
        paymentRepo.save(tx);

        order.setStatus(OrderStatus.PAID);
        orderRepo.save(order);

        String note = "Havale/EFT onayı — paidAmount=" + paidAmount + " TL, paidAt=" + paidAt
                + (receiptNote != null && !receiptNote.isBlank() ? ", not: " + receiptNote : "");
        logStatusChange(order, OrderStatus.PENDING_PAYMENT.name(), OrderStatus.PAID.name(), confirmedBy, note);

        logger.info("Bank transfer confirmed: orderId={}, amount={}, by={}", orderId, paidAmount, confirmedBy);

        // OrderPaid event — automatic invoice issuance (async)
        eventPublisher.publishEvent(new com.warehouse.event.OrderPaidEvent(
                this, order.getId(), order.getOrderNumber(), "bank_transfer:" + confirmedBy));

        try {
            notificationDispatchService.notifyPaymentReceived(order.getCustomer(), order.getOrderNumber());
            notificationDispatchService.notifyOrderConfirmed(order.getCustomer(), order.getOrderNumber());
        } catch (Exception e) {
            logger.warn("Havale onay bildirimi gönderilemedi (sipariş {}): {}", order.getOrderNumber(), e.getMessage());
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void rejectBankTransfer(Long orderId, String reason, String rejectedBy) {
        if (orderId == null) throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş ID boş.");
        if (reason == null || reason.isBlank()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Red sebebi zorunlu (audit ve müşteri bildirimi için).");
        }
        if (rejectedBy == null || rejectedBy.isBlank()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Admin kullanıcı bilgisi eksik.");
        }

        Order order = orderRepo.findByIdForUpdate(orderId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş bulunamadı."));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            logger.info("Bank transfer reject idempotent skip: orderId={} already CANCELLED", orderId);
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || !PaymentProvider.BANK_TRANSFER.name().equals(order.getPaymentMethod())) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Bu sipariş havale reddine uygun değil. Mevcut durum: " + order.getStatus());
        }

        // If there is an INITIATED tx, move it to FAILED
        paymentRepo.findByOrderIdAndStatus(orderId, PaymentStatus.INITIATED).ifPresent(tx -> {
            tx.setStatus(PaymentStatus.FAILED);
            tx.setErrorMessage("Havale reddedildi: " + reason);
            paymentRepo.save(tx);
        });

        // Release the stock reservation
        try { releaseOrderStock(order); }
        catch (Exception e) { logger.warn("Stock release failed: {}", e.getMessage()); }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        orderRepo.save(order);
        logStatusChange(order, oldStatus.name(), OrderStatus.CANCELLED.name(), rejectedBy,
                "Havale reddi: " + reason);

        logger.info("Bank transfer rejected: orderId={}, reason='{}', by={}", orderId, reason, rejectedBy);

        // TODO: NotificationDispatchService.notifyOrderCancelled(...) should be added.
        // For now the admin sends the cancellation email manually; the reason is stored
        // in the order_status_history.note column in the DB and can be reported on.
        logger.info("Customer email pending: order {} cancelled with reason '{}' — admin manual follow-up gerekirse",
                order.getOrderNumber(), reason);
    }

    @Override
    public RefundResult initiateRefund(Long orderId, BigDecimal amount, String reason, String ipAddress) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş bulunamadı."));

        PaymentTransaction tx = paymentRepo.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PAYMENT_NOT_FOUND, "Başarılı ödeme kaydı bulunamadı."));

        PaymentGateway gateway = gatewayFactory.getGateway(tx.getPaymentProvider());

        RefundRequest refundReq = RefundRequest.builder()
            .paymentTransactionId(tx.getProviderPaymentId())
            .conversationId(tx.getConversationId())
            .amount(amount)
            .currency("TRY")
            .ip(ipAddress)
            .reason(reason)
            .build();

        RefundResult result = gateway.refund(refundReq);

        if (result.isSuccess()) {
            boolean isFullRefund = amount.compareTo(tx.getPaidAmount()) >= 0;
            tx.setStatus(isFullRefund ? PaymentStatus.REFUNDED : PaymentStatus.PARTIAL_REFUNDED);
            tx.setRefundAmount(amount);
            tx.setRefundedAt(LocalDateTime.now());
            paymentRepo.save(tx);

            if (isFullRefund) {
                order.setStatus(OrderStatus.REFUNDED);
                orderRepo.save(order);
                logStatusChange(order, order.getStatus().name(), OrderStatus.REFUNDED.name(), "admin", "Tam iade: " + reason);
            }

            logger.info("Refund success: orderId={}, amount={}", orderId, amount);
        } else {
            logger.error("Refund failed: orderId={}, error={}", orderId, result.getErrorMessage());
            throw new WarehouseManagementException(ErrorCode.PAYMENT_REFUND_FAILED, result.getErrorMessage());
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResult getPaymentStatus(Long paymentId) {
        PaymentTransaction tx = paymentRepo.findById(paymentId)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PAYMENT_NOT_FOUND));

        return PaymentStatusResult.builder()
            .paymentId(tx.getId())
            .status(tx.getStatus())
            .amount(tx.getAmount())
            .paidAmount(tx.getPaidAmount())
            .orderNumber(tx.getOrder().getOrderNumber())
            .errorMessage(tx.getErrorMessage())
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public InstallmentQueryResult getInstallmentOptions(String binNumber, BigDecimal price) {
        return gatewayFactory.getDefaultGateway().getInstallmentOptions(binNumber, price);
    }

    @Override
    @Transactional(readOnly = true)
    public com.warehouse.entity.PaymentTransaction findTransactionByToken(String token) {
        return paymentRepo.findByToken(token).orElse(null);
    }

    // ── Helpers ──────────────────────────────────────

    /**
     * Authorises the caller against the order: either the authenticated owner, or the
     * bearer of the unexpired one-time token minted for this order at checkout.
     * Comparison is constant time so the token cannot be recovered byte by byte.
     */
    private void requireOrderAccess(Order order, PaymentCaller caller) {
        if (caller != null && caller.customerId() != null
                && order.getCustomer() != null
                && caller.customerId().equals(order.getCustomer().getId())) {
            return;
        }
        String presented = caller != null ? caller.accessToken() : null;
        String expected = order.getPaymentAccessToken();
        LocalDateTime expiresAt = order.getPaymentAccessTokenExpiresAt();
        if (presented != null && expected != null && expiresAt != null
                && expiresAt.isAfter(LocalDateTime.now())
                && java.security.MessageDigest.isEqual(
                        presented.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        expected.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return;
        }
        logger.warn("Yetkisiz odeme baslatma denemesi: orderId={}, customerId={}",
                order.getId(), caller != null ? caller.customerId() : null);
        throw new WarehouseManagementException(ErrorCode.UNAUTHORIZED_ACTION,
                "Bu siparis icin odeme baslatma yetkiniz yok.");
    }

    /**
     * Rejects payment methods the store has switched off. Without this the admin
     * toggles were advisory: they filtered the list the storefront rendered, while a
     * hand-crafted request could still pick DOOR_CASH and skip collection entirely.
     */
    private void requirePaymentMethodEnabled(String paymentMethod) {
        String method = paymentMethod == null ? "" : paymentMethod.toUpperCase(java.util.Locale.ROOT);
        String settingKey = switch (method) {
            case "CREDIT_CARD", "VIRTUAL_POS" -> "payment_method_credit_card_enabled";
            case "BANK_TRANSFER" -> "payment_method_bank_transfer_enabled";
            case "DOOR_CASH", "DOOR_CARD" -> "payment_method_door_cash_enabled";
            default -> null;
        };
        if (settingKey == null) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Gecersiz odeme yontemi.");
        }
        if (!siteSettingService.getBoolSetting(settingKey, true)) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Bu odeme yontemi su anda kullanilamiyor.");
        }
    }

    private PaymentProvider resolveProvider(String paymentMethod) {
        if (paymentMethod == null) return resolveDefaultCardProvider();
        try {
            PaymentMethod method = PaymentMethod.valueOf(paymentMethod.toUpperCase());
            return switch (method) {
                case CREDIT_CARD, VIRTUAL_POS -> resolveDefaultCardProvider();
                case BANK_TRANSFER -> PaymentProvider.BANK_TRANSFER;
                case DOOR_CASH, DOOR_CARD -> PaymentProvider.DOOR_PAYMENT;
            };
        } catch (IllegalArgumentException e) {
            // Fallback for legacy string values
            return switch (paymentMethod.toUpperCase()) {
                case "CREDIT_CARD" -> resolveDefaultCardProvider();
                case "BANK_TRANSFER" -> PaymentProvider.BANK_TRANSFER;
                case "DOOR_CASH", "DOOR_CARD" -> PaymentProvider.DOOR_PAYMENT;
                default -> resolveDefaultCardProvider();
            };
        }
    }

    /**
     * Resolve the active card payment provider. Order:
     *   1. The first active gateway with {@code defaultGateway=true}
     *   2. If none is marked default, the first active gateway with the lowest priority
     *   3. If there is no active gateway → fail-fast ("payment system not configured" to the user)
     *
     * The old behavior fell back to a hardcoded IYZICO — if the admin only enabled
     * PayTR and forgot to mark it as default, Iyzico was called and an
     * "Empty key" error was thrown.
     */
    private PaymentProvider resolveDefaultCardProvider() {
        // 1. First, the default-marked active gateway
        var defaultConfig = gatewayConfigRepo.findFirstByActiveTrueAndDefaultGatewayTrueOrderByPriorityAsc();
        if (defaultConfig.isPresent()) {
            try {
                return PaymentProvider.valueOf(defaultConfig.get().getGatewayProtocol());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid gateway protocol in config: {}", defaultConfig.get().getGatewayProtocol());
            }
        }
        // 2. No default marked → first active gateway (priority asc)
        var anyActive = gatewayConfigRepo.findByActiveTrueOrderByPriorityAsc();
        if (!anyActive.isEmpty()) {
            var gw = anyActive.get(0);
            try {
                logger.info("Hiçbir default gateway işaretlenmemiş; ilk active gateway kullanılıyor: code={}, protocol={}",
                        gw.getCode(), gw.getGatewayProtocol());
                return PaymentProvider.valueOf(gw.getGatewayProtocol());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid gateway protocol in config: {}", gw.getGatewayProtocol());
            }
        }
        // 3. No active gateway at all → fail-fast
        throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Aktif bir kart ödeme sistemi yapılandırılmamış. Lütfen admin panelinden "
                        + "bir ödeme sağlayıcı (PayTR, Iyzico, NestPay vb.) aktive edin.");
    }

    private PaymentInitRequest buildInitRequest(Order order, int installmentCount, String ipAddress) {
        Customer customer = order.getCustomer();
        Map<String, Object> shippingAddr = order.getShippingAddressSnapshot();
        Map<String, Object> billingAddr = order.getBillingAddressSnapshot();

        List<PaymentInitRequest.BasketItemDto> basketItems = new ArrayList<>();
        List<OrderItem> items = orderItemRepo.findByOrderId(order.getId());
        for (OrderItem item : items) {
            String name = item.getProductSnapshot() != null ? (String) item.getProductSnapshot().getOrDefault("name", "Urun") : "Urun";
            basketItems.add(PaymentInitRequest.BasketItemDto.builder()
                .id(String.valueOf(item.getProduct().getId()))
                .name(name)
                .category("Urunler")
                .price(item.getLineTotal())
                .build());
        }

        return PaymentInitRequest.builder()
            .orderId(String.valueOf(order.getId()))
            .orderNumber(order.getOrderNumber())
            .price(order.getSubtotal())
            .paidPrice(order.getGrandTotal())
            .currency("TRY")
            .installmentCount(installmentCount)
            .buyerId(String.valueOf(customer.getId()))
            .buyerName(customer.getFirstName())
            .buyerSurname(customer.getLastName())
            .buyerEmail(customer.getEmail())
            .buyerPhone(customer.getPhone() != null ? customer.getPhone() : "05000000000")
            .buyerIdentityNumber(customer.getTcKimlikNo())
            .buyerIp(ipAddress)
            .buyerCity(shippingAddr != null ? (String) shippingAddr.getOrDefault("city", "Istanbul") : "Istanbul")
            .buyerCountry("Turkey")
            .buyerAddress(shippingAddr != null ? (String) shippingAddr.getOrDefault("addressLine", "") : "")
            .shippingContactName(shippingAddr != null ? shippingAddr.getOrDefault("firstName", "") + " " + shippingAddr.getOrDefault("lastName", "") : "")
            .shippingCity(shippingAddr != null ? (String) shippingAddr.getOrDefault("city", "Istanbul") : "Istanbul")
            .shippingCountry("Turkey")
            .shippingAddress(shippingAddr != null ? (String) shippingAddr.getOrDefault("addressLine", "") : "")
            .billingContactName(billingAddr != null ? billingAddr.getOrDefault("firstName", "") + " " + billingAddr.getOrDefault("lastName", "") : "")
            .billingCity(billingAddr != null ? (String) billingAddr.getOrDefault("city", "Istanbul") : "Istanbul")
            .billingCountry("Turkey")
            .billingAddress(billingAddr != null ? (String) billingAddr.getOrDefault("addressLine", "") : "")
            .basketItems(basketItems)
            .build();
    }

    private void releaseOrderStock(Order order) {
        List<OrderItem> items = orderItemRepo.findByOrderId(order.getId());
        for (OrderItem item : items) {
            List<Map<String, Object>> members = reservedMembersOf(item);
            if (members != null) {
                // Bundle: release each member allocation recorded at checkout.
                for (Map<String, Object> alloc : members) {
                    releaseStockUnits(toLong(alloc.get("stockId")), toInt(alloc.get("quantity")),
                            toLong(alloc.get("productId")), order);
                }
            } else if (item.getStockId() != null) {
                Long pid = item.getProduct() != null ? item.getProduct().getId() : null;
                releaseStockUnits(item.getStockId(), item.getQuantity(), pid, order);
            }
        }
    }

    private void releaseStockUnits(Long stockId, int quantity, Long productId, Order order) {
        if (stockId == null || quantity <= 0) return;
        stockRepo.findByIdForUpdate(stockId).ifPresent(stock -> {
            int oldReserved = stock.getReservedQuantity();
            stock.setReservedQuantity(Math.max(0, oldReserved - quantity));
            stockRepo.save(stock);

            StockEvent event = new StockEvent();
            event.setStockId(stock.getId());
            event.setProductId(productId);
            event.setEventType(StockEventType.RELEASED);
            event.setOldValue(oldReserved);
            event.setNewValue(stock.getReservedQuantity());
            event.setSource(StockEventSource.ORDER);
            event.setSourceDetail("Sipariş #" + order.getOrderNumber() + " ödeme başarısız — stok serbest (" + quantity + " adet)");
            event.setOrderNumber(order.getOrderNumber());
            stockEventRepo.save(event);
        });
    }

    /** Member reservation allocations for a bundle order item (null for simple products). */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> reservedMembersOf(OrderItem item) {
        Map<String, Object> snap = item.getProductSnapshot();
        if (snap != null && Boolean.TRUE.equals(snap.get("isBundle")) && snap.get("reservedMembers") instanceof List<?> l) {
            return (List<Map<String, Object>>) (List<?>) l;
        }
        return null;
    }

    /** Aggregate the units needed per member product across a bundle's allocations. */
    private Map<Long, Integer> bundleMemberNeeds(List<Map<String, Object>> members) {
        Map<Long, Integer> need = new java.util.LinkedHashMap<>();
        for (Map<String, Object> a : members) {
            Long pid = toLong(a.get("productId"));
            if (pid != null) need.merge(pid, toInt(a.get("quantity")), Integer::sum);
        }
        return need;
    }

    private static Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        try { return v != null ? Long.parseLong(v.toString().trim()) : null; } catch (NumberFormatException e) { return null; }
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return v != null ? Integer.parseInt(v.toString().trim()) : 0; } catch (NumberFormatException e) { return 0; }
    }

    private boolean tryReReserveStock(Order order) {
        try {
            List<OrderItem> items = orderItemRepo.findByOrderId(order.getId());
            // Availability check (bundles check each member; simple products check themselves)
            for (OrderItem item : items) {
                List<Map<String, Object>> members = reservedMembersOf(item);
                if (members != null) {
                    for (Map.Entry<Long, Integer> need : bundleMemberNeeds(members).entrySet()) {
                        if (sumAvailable(need.getKey()) < need.getValue()) return false;
                    }
                } else {
                    if (sumAvailable(item.getProduct().getId()) < item.getQuantity()) return false;
                }
            }
            // Re-reserve
            for (OrderItem item : items) {
                List<Map<String, Object>> members = reservedMembersOf(item);
                if (members != null) {
                    for (Map.Entry<Long, Integer> need : bundleMemberNeeds(members).entrySet()) {
                        reReserveUnits(need.getKey(), need.getValue());
                    }
                } else {
                    reReserveUnits(item.getProduct().getId(), item.getQuantity());
                }
            }
            return true;
        } catch (Exception e) {
            logger.error("Re-reservation failed: {}", e.getMessage());
            return false;
        }
    }

    private int sumAvailable(Long productId) {
        return stockRepo.findAvailableByProductForUpdate(productId).stream()
                .mapToInt(Stock::getAvailableQuantity).sum();
    }

    private void reReserveUnits(Long productId, int needed) {
        int remaining = needed;
        for (Stock stock : stockRepo.findAvailableByProductForUpdate(productId)) {
            int avail = stock.getAvailableQuantity();
            if (avail <= 0) continue;
            int toReserve = Math.min(remaining, avail);
            stock.setReservedQuantity(stock.getReservedQuantity() + toReserve);
            stockRepo.save(stock);
            remaining -= toReserve;
            if (remaining <= 0) break;
        }
    }

    private void logStatusChange(Order order, String oldStatus, String newStatus, String changedBy, String note) {
        statusHistoryRepo.save(OrderStatusHistoryFactory.create(order, oldStatus, newStatus, changedBy, "PAYMENT", note));
    }
}
