package com.warehouse.service;

import com.warehouse.config.PaymentProperties;
import com.warehouse.dto.payment.*;
import com.warehouse.entity.*;
import com.warehouse.enums.*;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.*;
import com.warehouse.service.impl.PaymentServiceImpl;
import com.warehouse.service.payment.PaymentGateway;
import com.warehouse.service.payment.PaymentGatewayFactory;
import com.warehouse.testutil.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentTransactionRepository paymentRepo;
    @Mock private OrderRepository orderRepo;
    @Mock private OrderStatusHistoryRepository statusHistoryRepo;
    @Mock private StockRepository stockRepo;
    @Mock private OrderItemRepository orderItemRepo;
    @Mock private PaymentGatewayFactory gatewayFactory;
    @Mock private PaymentProperties paymentProperties;
    @Mock private PaymentGateway mockGateway;
    @Mock private com.warehouse.repository.PaymentGatewayConfigRepository gatewayConfigRepo;
    @Mock private com.warehouse.service.CartService cartService;
    @Mock private StockEventRepository stockEventRepo;
    @Mock private com.warehouse.service.InvoiceService invoiceService;
    @Mock private com.warehouse.service.notification.NotificationDispatchService notificationDispatchService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentRepo, orderRepo, statusHistoryRepo,
            stockRepo, orderItemRepo, gatewayFactory, paymentProperties, gatewayConfigRepo, cartService, stockEventRepo,
            invoiceService, notificationDispatchService, eventPublisher);

        PaymentProperties.IyzicoConfig iyzicoConfig = new PaymentProperties.IyzicoConfig();
        iyzicoConfig.setTimeoutMinutes(15);
        lenient().when(paymentProperties.getIyzico()).thenReturn(iyzicoConfig);
        lenient().when(paymentProperties.getProvider()).thenReturn("IYZICO");

        PaymentProperties.BankTransferConfig bankConfig = new PaymentProperties.BankTransferConfig();
        bankConfig.setDeadlineHours(48);
        lenient().when(paymentProperties.getBankTransfer()).thenReturn(bankConfig);
    }

    @Test
    void should_initialize_payment_for_credit_card_successfully() {
        Customer customer = TestDataFactory.createCustomer();
        Order order = TestDataFactory.createOrder(customer);
        String idempotencyKey = UUID.randomUUID().toString();

        when(paymentRepo.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        when(gatewayFactory.getGateway(PaymentProvider.IYZICO)).thenReturn(mockGateway);
        when(paymentRepo.save(any(PaymentTransaction.class))).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            if (tx.getId() == null) tx.setId(1L);
            return tx;
        });
        when(orderItemRepo.findByOrderId(order.getId())).thenReturn(List.of());
        when(mockGateway.initializePayment(any())).thenReturn(
            PaymentInitResult.builder().success(true).htmlContent("<html>form</html>").token("test-token").rawResponse(Map.of()).build()
        );

        PaymentInitResult result = paymentService.initializePayment(order.getId(), "CREDIT_CARD", 1, "127.0.0.1", idempotencyKey);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getHtmlContent()).contains("form");
        verify(paymentRepo, times(2)).save(any(PaymentTransaction.class));
    }

    @Test
    void should_initialize_payment_for_bank_transfer_with_reference() {
        Customer customer = TestDataFactory.createCustomer();
        Order order = TestDataFactory.createOrder(customer);
        order.setPaymentMethod("BANK_TRANSFER");
        String idempotencyKey = UUID.randomUUID().toString();

        when(paymentRepo.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        when(gatewayFactory.getGateway(PaymentProvider.BANK_TRANSFER)).thenReturn(mockGateway);
        when(paymentRepo.save(any())).thenAnswer(inv -> { PaymentTransaction t = inv.getArgument(0); if(t.getId()==null) t.setId(1L); return t; });
        when(orderItemRepo.findByOrderId(order.getId())).thenReturn(List.of());
        when(mockGateway.initializePayment(any())).thenReturn(
            PaymentInitResult.builder().success(true).bankTransferReference("HVL-TEST-1234").bankDetails(Map.of("iban","TR123")).rawResponse(Map.of()).build()
        );
        when(orderRepo.save(any())).thenReturn(order);

        PaymentInitResult result = paymentService.initializePayment(order.getId(), "BANK_TRANSFER", 1, "127.0.0.1", idempotencyKey);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBankTransferReference()).startsWith("HVL");
    }

    @Test
    void should_auto_complete_door_payment() {
        Customer customer = TestDataFactory.createCustomer();
        Order order = TestDataFactory.createOrder(customer);
        String idempotencyKey = UUID.randomUUID().toString();

        when(paymentRepo.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        when(gatewayFactory.getGateway(PaymentProvider.DOOR_PAYMENT)).thenReturn(mockGateway);
        when(paymentRepo.save(any())).thenAnswer(inv -> { PaymentTransaction t = inv.getArgument(0); if(t.getId()==null) t.setId(1L); return t; });
        when(orderItemRepo.findByOrderId(order.getId())).thenReturn(List.of());
        when(mockGateway.initializePayment(any())).thenReturn(PaymentInitResult.builder().success(true).rawResponse(Map.of()).build());
        when(orderRepo.save(any())).thenReturn(order);

        paymentService.initializePayment(order.getId(), "DOOR_CASH", 1, "127.0.0.1", idempotencyKey);

        verify(orderRepo).save(argThat(o -> o.getStatus() == OrderStatus.PREPARING));
        verify(statusHistoryRepo).save(any(OrderStatusHistory.class));
    }

    @Test
    void should_return_existing_result_for_idempotent_success_key() {
        String key = "existing-key";
        PaymentTransaction existing = new PaymentTransaction();
        existing.setId(1L);
        existing.setStatus(PaymentStatus.SUCCESS);
        existing.setToken("old-token");

        when(paymentRepo.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        PaymentInitResult result = paymentService.initializePayment(1L, "CREDIT_CARD", 1, "127.0.0.1", key);

        assertThat(result.isSuccess()).isTrue();
        verify(orderRepo, never()).findById(any());
    }

    @Test
    void should_throw_idempotency_conflict_for_failed_key() {
        String key = "failed-key";
        PaymentTransaction existing = new PaymentTransaction();
        existing.setStatus(PaymentStatus.FAILED);

        when(paymentRepo.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentService.initializePayment(1L, "CREDIT_CARD", 1, "127.0.0.1", key))
            .isInstanceOf(WarehouseManagementException.class)
            .satisfies(e -> assertThat(((WarehouseManagementException)e).getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT));
    }

    @Test
    void should_throw_when_order_not_pending_payment() {
        Customer customer = TestDataFactory.createCustomer();
        Order order = TestDataFactory.createOrder(customer);
        order.setStatus(OrderStatus.PAID);
        String key = UUID.randomUUID().toString();

        when(paymentRepo.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.initializePayment(order.getId(), "CREDIT_CARD", 1, "127.0.0.1", key))
            .isInstanceOf(WarehouseManagementException.class)
            .satisfies(e -> assertThat(((WarehouseManagementException)e).getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_PROCESSED));
    }

    @Test
    void should_handle_successful_callback() {
        Customer customer = TestDataFactory.createCustomer();
        Order order = TestDataFactory.createOrder(customer);
        PaymentTransaction tx = TestDataFactory.createPaymentTransaction(order);
        tx.setStatus(PaymentStatus.PROCESSING);
        tx.setToken("callback-token");
        tx.setPaymentProvider(PaymentProvider.IYZICO);

        when(paymentRepo.findByToken("callback-token")).thenReturn(Optional.of(tx));
        when(gatewayFactory.getGateway(PaymentProvider.IYZICO)).thenReturn(mockGateway);
        when(mockGateway.handleCallback(any())).thenReturn(
            PaymentCallbackResult.builder().success(true).token("callback-token").paymentId("pay-123")
                .paidPrice(new BigDecimal("269.99")).installmentCount(1).cardLastFour("4321")
                .cardType("VISA").threeDSecure(true).rawResponse(Map.of()).build()
        );
        when(paymentRepo.save(any())).thenReturn(tx);
        when(orderRepo.save(any())).thenReturn(order);

        PaymentCallbackResult result = paymentService.handlePaymentCallback(Map.of("token", "callback-token"));

        assertThat(result.isSuccess()).isTrue();
        verify(orderRepo).save(argThat(o -> o.getStatus() == OrderStatus.PAID));
        verify(statusHistoryRepo).save(any());
    }

    @Test
    void should_handle_failed_callback_and_release_stock() {
        Customer customer = TestDataFactory.createCustomer();
        Order order = TestDataFactory.createOrder(customer);
        Product product = TestDataFactory.createProduct();
        OrderItem oi = TestDataFactory.createOrderItem(order, product, 2);
        Stock stock = TestDataFactory.createStock(product, 10);
        stock.setReservedQuantity(2);
        oi.setStockId(stock.getId());

        PaymentTransaction tx = TestDataFactory.createPaymentTransaction(order);
        tx.setStatus(PaymentStatus.PROCESSING);
        tx.setToken("fail-token");
        tx.setPaymentProvider(PaymentProvider.IYZICO);

        when(paymentRepo.findByToken("fail-token")).thenReturn(Optional.of(tx));
        when(gatewayFactory.getGateway(PaymentProvider.IYZICO)).thenReturn(mockGateway);
        when(mockGateway.handleCallback(any())).thenReturn(
            PaymentCallbackResult.builder().success(false).errorMessage("Card declined").rawResponse(Map.of()).build()
        );
        when(orderItemRepo.findByOrderId(order.getId())).thenReturn(List.of(oi));
        when(stockRepo.findByIdForUpdate(stock.getId())).thenReturn(Optional.of(stock));
        when(paymentRepo.save(any())).thenReturn(tx);
        when(orderRepo.save(any())).thenReturn(order);

        paymentService.handlePaymentCallback(Map.of("token", "fail-token"));

        verify(stockRepo).save(argThat(s -> s.getReservedQuantity() == 0));
        verify(orderRepo).save(argThat(o -> o.getStatus() == OrderStatus.CANCELLED));
    }

    @Test
    void should_confirm_bank_transfer() {
        Customer customer = TestDataFactory.createCustomer();
        Order order = TestDataFactory.createOrder(customer);
        order.setPaymentMethod("BANK_TRANSFER");
        PaymentTransaction tx = TestDataFactory.createPaymentTransaction(order);
        tx.setPaymentProvider(PaymentProvider.BANK_TRANSFER);
        tx.setStatus(PaymentStatus.INITIATED);

        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepo.findByOrderIdAndStatus(order.getId(), PaymentStatus.INITIATED)).thenReturn(Optional.of(tx));
        when(paymentRepo.save(any())).thenReturn(tx);
        when(orderRepo.save(any())).thenReturn(order);

        paymentService.confirmBankTransfer(order.getId(), "admin");

        verify(paymentRepo).save(argThat(t -> t.getStatus() == PaymentStatus.SUCCESS));
        verify(orderRepo).save(argThat(o -> o.getStatus() == OrderStatus.PAID));
    }

    @Test
    void should_throw_when_confirming_non_bank_transfer_order() {
        Customer customer = TestDataFactory.createCustomer();
        Order order = TestDataFactory.createOrder(customer);
        order.setPaymentMethod("CREDIT_CARD");

        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.confirmBankTransfer(order.getId(), "admin"))
            .isInstanceOf(WarehouseManagementException.class);
    }

    @Test
    void should_initiate_full_refund() {
        Customer customer = TestDataFactory.createCustomer();
        Order order = TestDataFactory.createOrder(customer);
        order.setStatus(OrderStatus.PAID);
        PaymentTransaction tx = TestDataFactory.createPaymentTransaction(order);
        tx.setStatus(PaymentStatus.SUCCESS);
        tx.setPaidAmount(new BigDecimal("269.99"));
        tx.setProviderPaymentId("pay-123");

        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepo.findByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS)).thenReturn(Optional.of(tx));
        when(gatewayFactory.getGateway(PaymentProvider.IYZICO)).thenReturn(mockGateway);
        when(mockGateway.refund(any())).thenReturn(RefundResult.builder().success(true).refundedAmount(new BigDecimal("269.99")).build());
        when(paymentRepo.save(any())).thenReturn(tx);
        when(orderRepo.save(any())).thenReturn(order);

        RefundResult result = paymentService.initiateRefund(order.getId(), new BigDecimal("269.99"), "Customer request", "127.0.0.1");

        assertThat(result.isSuccess()).isTrue();
        verify(paymentRepo).save(argThat(t -> t.getStatus() == PaymentStatus.REFUNDED));
    }

    @Test
    void should_initiate_partial_refund() {
        Customer customer = TestDataFactory.createCustomer();
        Order order = TestDataFactory.createOrder(customer);
        order.setStatus(OrderStatus.PAID);
        PaymentTransaction tx = TestDataFactory.createPaymentTransaction(order);
        tx.setStatus(PaymentStatus.SUCCESS);
        tx.setPaidAmount(new BigDecimal("269.99"));

        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepo.findByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS)).thenReturn(Optional.of(tx));
        when(gatewayFactory.getGateway(PaymentProvider.IYZICO)).thenReturn(mockGateway);
        when(mockGateway.refund(any())).thenReturn(RefundResult.builder().success(true).refundedAmount(new BigDecimal("50.00")).build());
        when(paymentRepo.save(any())).thenReturn(tx);

        RefundResult result = paymentService.initiateRefund(order.getId(), new BigDecimal("50.00"), "Partial", "127.0.0.1");

        assertThat(result.isSuccess()).isTrue();
        verify(paymentRepo).save(argThat(t -> t.getStatus() == PaymentStatus.PARTIAL_REFUNDED));
    }

    @Test
    void should_throw_when_refund_fails() {
        Customer customer = TestDataFactory.createCustomer();
        Order order = TestDataFactory.createOrder(customer);
        PaymentTransaction tx = TestDataFactory.createPaymentTransaction(order);
        tx.setStatus(PaymentStatus.SUCCESS);
        tx.setPaidAmount(new BigDecimal("269.99"));

        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepo.findByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS)).thenReturn(Optional.of(tx));
        when(gatewayFactory.getGateway(PaymentProvider.IYZICO)).thenReturn(mockGateway);
        when(mockGateway.refund(any())).thenReturn(RefundResult.builder().success(false).errorMessage("Gateway error").build());

        assertThatThrownBy(() -> paymentService.initiateRefund(order.getId(), new BigDecimal("269.99"), "Test", "127.0.0.1"))
            .isInstanceOf(WarehouseManagementException.class)
            .satisfies(e -> assertThat(((WarehouseManagementException)e).getErrorCode()).isEqualTo(ErrorCode.PAYMENT_REFUND_FAILED));
    }

    @Test
    void should_get_payment_status() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(1L);
        tx.setStatus(PaymentStatus.SUCCESS);
        tx.setAmount(new BigDecimal("100.00"));
        tx.setPaidAmount(new BigDecimal("100.00"));
        Order order = new Order();
        order.setOrderNumber("ORD-001");
        tx.setOrder(order);

        when(paymentRepo.findById(1L)).thenReturn(Optional.of(tx));

        PaymentStatusResult result = paymentService.getPaymentStatus(1L);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.getOrderNumber()).isEqualTo("ORD-001");
    }
}
