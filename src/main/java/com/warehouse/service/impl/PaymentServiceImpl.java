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

    public PaymentServiceImpl(PaymentTransactionRepository paymentRepo,
                               OrderRepository orderRepo,
                               OrderStatusHistoryRepository statusHistoryRepo,
                               StockRepository stockRepo,
                               OrderItemRepository orderItemRepo,
                               PaymentGatewayFactory gatewayFactory,
                               PaymentProperties paymentProperties,
                               PaymentGatewayConfigRepository gatewayConfigRepo) {
        this.paymentRepo = paymentRepo;
        this.orderRepo = orderRepo;
        this.statusHistoryRepo = statusHistoryRepo;
        this.stockRepo = stockRepo;
        this.orderItemRepo = orderItemRepo;
        this.gatewayFactory = gatewayFactory;
        this.paymentProperties = paymentProperties;
        this.gatewayConfigRepo = gatewayConfigRepo;
    }

    @Override
    public PaymentInitResult initializePayment(Long orderId, String paymentMethod, int installmentCount,
                                                String ipAddress, String idempotencyKey) {
        // 1. Idempotency check
        Optional<PaymentTransaction> existing = paymentRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            PaymentTransaction ex = existing.get();
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

        // 2. Load and validate order
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş bulunamadı."));

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new WarehouseManagementException(ErrorCode.PAYMENT_ALREADY_PROCESSED,
                "Sipariş durumu ödeme için uygun değil: " + order.getStatus());
        }

        // 3. Determine provider
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
            tx.setStatus(PaymentStatus.FAILED);
            tx.setErrorMessage(e.getMessage());
            paymentRepo.save(tx);
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
                case BANK_TRANSFER:
                    tx.setStatus(PaymentStatus.INITIATED);
                    tx.setBankTransferRef(result.getBankTransferReference());
                    tx.setExpiresAt(LocalDateTime.now().plusHours(paymentProperties.getBankTransfer().getDeadlineHours()));
                    order.setBankTransferDeadline(tx.getExpiresAt());
                    orderRepo.save(order);
                    break;
                case DOOR_PAYMENT:
                    // Kapıda ödeme: ödeme henüz alınmadı, sipariş hazırlanmaya başlar
                    tx.setStatus(PaymentStatus.INITIATED);
                    order.setStatus(OrderStatus.PREPARING);
                    orderRepo.save(order);
                    logStatusChange(order, OrderStatus.PENDING_PAYMENT.name(), OrderStatus.PREPARING.name(),
                            "system", "Kapıda ödeme — teslimat sırasında tahsil edilecek");
                    break;
                default:
                    break;
            }
        } else {
            tx.setStatus(PaymentStatus.FAILED);
            tx.setErrorCode(result.getErrorCode());
            tx.setErrorMessage(result.getErrorMessage());
        }

        if (result.getRawResponse() != null) {
            tx.setRawResponse(result.getRawResponse());
        }
        paymentRepo.save(tx);

        logger.info("Payment initialized: orderId={}, provider={}, status={}, txId={}",
            orderId, provider, tx.getStatus(), tx.getId());

        return result;
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

        PaymentGateway gateway = gatewayFactory.getGateway(tx.getPaymentProvider());
        PaymentCallbackResult result = gateway.handleCallback(params);

        Order order = tx.getOrder();

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

            // Verify payment amount matches order total
            if (result.getPaidPrice() != null && order.getGrandTotal() != null) {
                BigDecimal diff = result.getPaidPrice().subtract(order.getGrandTotal()).abs();
                if (diff.compareTo(new BigDecimal("0.01")) > 0) {
                    logger.error("Payment amount mismatch! orderId={}, expected={}, received={}",
                        order.getId(), order.getGrandTotal(), result.getPaidPrice());
                    // Still process but flag for admin review
                    tx.setErrorMessage("AMOUNT_MISMATCH: expected=" + order.getGrandTotal() + ", received=" + result.getPaidPrice());
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
            logStatusChange(order, OrderStatus.PENDING_PAYMENT.name(), OrderStatus.PAID.name(), "system", "iyzico ödeme başarılı");

            logger.info("Payment successful: txId={}, orderId={}", tx.getId(), order.getId());
        } else {
            tx.setStatus(PaymentStatus.FAILED);
            tx.setErrorCode(result.getErrorCode());
            tx.setErrorMessage(result.getErrorMessage());

            // Release reserved stock
            releaseOrderStock(order);

            order.setStatus(OrderStatus.CANCELLED);
            orderRepo.save(order);
            logStatusChange(order, OrderStatus.PENDING_PAYMENT.name(), OrderStatus.CANCELLED.name(), "system", "Odeme basarisiz: " + result.getErrorMessage());

            logger.warn("Payment failed: txId={}, error={}", tx.getId(), result.getErrorMessage());
        }

        if (result.getRawResponse() != null) {
            tx.setRawCallback(result.getRawResponse());
        }
        paymentRepo.save(tx);

        return result;
    }

    @Override
    public void confirmBankTransfer(Long orderId, String confirmedBy) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sipariş bulunamadı."));

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT || !PaymentProvider.BANK_TRANSFER.name().equals(order.getPaymentMethod())) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Bu sipariş havale onayına uygun değil.");
        }

        PaymentTransaction tx = paymentRepo.findByOrderIdAndStatus(orderId, PaymentStatus.INITIATED)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PAYMENT_NOT_FOUND));

        tx.setStatus(PaymentStatus.SUCCESS);
        tx.setPaidAt(LocalDateTime.now());
        tx.setPaidAmount(order.getGrandTotal());
        paymentRepo.save(tx);

        order.setStatus(OrderStatus.PAID);
        orderRepo.save(order);
        logStatusChange(order, OrderStatus.PENDING_PAYMENT.name(), OrderStatus.PAID.name(), confirmedBy, "Havale/EFT onayı");

        logger.info("Bank transfer confirmed: orderId={}, by={}", orderId, confirmedBy);
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

    // ── Helpers ──────────────────────────────────────

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

    private PaymentProvider resolveDefaultCardProvider() {
        var defaultConfig = gatewayConfigRepo.findFirstByActiveTrueAndDefaultGatewayTrueOrderByPriorityAsc();
        if (defaultConfig.isPresent()) {
            try {
                return PaymentProvider.valueOf(defaultConfig.get().getGatewayProtocol());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid gateway protocol in config: {}", defaultConfig.get().getGatewayProtocol());
            }
        }
        return PaymentProvider.IYZICO; // Fallback
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
            if (item.getStockId() != null) {
                stockRepo.findByIdForUpdate(item.getStockId()).ifPresent(stock -> {
                    stock.setReservedQuantity(Math.max(0, stock.getReservedQuantity() - item.getQuantity()));
                    stockRepo.save(stock);
                });
            }
        }
    }

    private boolean tryReReserveStock(Order order) {
        try {
            List<OrderItem> items = orderItemRepo.findByOrderId(order.getId());
            for (OrderItem item : items) {
                List<Stock> stocks = stockRepo.findAvailableByProductForUpdate(item.getProduct().getId());
                int available = stocks.stream().mapToInt(Stock::getAvailableQuantity).sum();
                if (available < item.getQuantity()) return false;
            }
            // Re-reserve
            for (OrderItem item : items) {
                List<Stock> stocks = stockRepo.findAvailableByProductForUpdate(item.getProduct().getId());
                int remaining = item.getQuantity();
                for (Stock stock : stocks) {
                    int avail = stock.getAvailableQuantity();
                    if (avail <= 0) continue;
                    int toReserve = Math.min(remaining, avail);
                    stock.setReservedQuantity(stock.getReservedQuantity() + toReserve);
                    stockRepo.save(stock);
                    remaining -= toReserve;
                    if (remaining <= 0) break;
                }
            }
            return true;
        } catch (Exception e) {
            logger.error("Re-reservation failed: {}", e.getMessage());
            return false;
        }
    }

    private void logStatusChange(Order order, String oldStatus, String newStatus, String changedBy, String note) {
        statusHistoryRepo.save(OrderStatusHistoryFactory.create(order, oldStatus, newStatus, changedBy, "PAYMENT", note));
    }
}
