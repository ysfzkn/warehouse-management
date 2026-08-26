package com.warehouse.job;

import com.warehouse.entity.*;
import com.warehouse.enums.*;
import com.warehouse.repository.*;
import com.warehouse.testutil.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankTransferExpiryJobTest {

    @Mock private OrderRepository orderRepo;
    @Mock private OrderItemRepository orderItemRepo;
    @Mock private PaymentTransactionRepository paymentRepo;
    @Mock private OrderStatusHistoryRepository statusHistoryRepo;
    @Mock private StockRepository stockRepo;
    @Mock private com.warehouse.service.CouponService couponService;

    private BankTransferExpiryJob job;

    @BeforeEach
    void setUp() {
        job = new BankTransferExpiryJob(orderRepo, orderItemRepo, paymentRepo, statusHistoryRepo, stockRepo, couponService);
    }

    @Test
    void should_cancel_expired_bank_transfer_orders() {
        Customer customer = TestDataFactory.createCustomer();
        Order order = TestDataFactory.createOrder(customer);
        order.setPaymentMethod("BANK_TRANSFER");
        order.setBankTransferDeadline(LocalDateTime.now().minusHours(1));

        PaymentTransaction tx = TestDataFactory.createPaymentTransaction(order);
        tx.setStatus(PaymentStatus.INITIATED);
        tx.setPaymentProvider(PaymentProvider.BANK_TRANSFER);

        when(orderRepo.findExpiredBankTransferOrders(eq(OrderStatus.PENDING_PAYMENT), eq("BANK_TRANSFER"), any()))
            .thenReturn(List.of(order));
        // Job her order'ı pessimistic lock ile yeniden çekiyor (findByIdForUpdate)
        when(orderRepo.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepo.findByOrderId(order.getId())).thenReturn(List.of());
        when(paymentRepo.findByOrderIdAndStatus(order.getId(), PaymentStatus.INITIATED)).thenReturn(Optional.of(tx));
        when(orderRepo.save(any())).thenReturn(order);

        job.cleanupExpiredBankTransfers();

        verify(orderRepo).save(argThat(o -> o.getStatus() == OrderStatus.CANCELLED));
        verify(paymentRepo).save(argThat(t -> t.getStatus() == PaymentStatus.TIMEOUT));
    }

    @Test
    void should_not_process_when_no_expired_orders() {
        when(orderRepo.findExpiredBankTransferOrders(any(), any(), any())).thenReturn(List.of());

        job.cleanupExpiredBankTransfers();

        verify(orderRepo, never()).save(any());
    }
}
