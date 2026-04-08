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
class PaymentTimeoutJobTest {

    @Mock private PaymentTransactionRepository paymentRepo;
    @Mock private OrderRepository orderRepo;
    @Mock private OrderItemRepository orderItemRepo;
    @Mock private OrderStatusHistoryRepository statusHistoryRepo;
    @Mock private StockRepository stockRepo;

    private PaymentTimeoutJob job;

    @BeforeEach
    void setUp() {
        job = new PaymentTimeoutJob(paymentRepo, orderRepo, orderItemRepo, statusHistoryRepo, stockRepo);
    }

    @Test
    void should_timeout_expired_processing_transactions() {
        Customer customer = TestDataFactory.createCustomer();
        Order order = TestDataFactory.createOrder(customer);
        PaymentTransaction tx = TestDataFactory.createPaymentTransaction(order);
        tx.setStatus(PaymentStatus.PROCESSING);
        tx.setExpiresAt(LocalDateTime.now().minusMinutes(5));

        when(paymentRepo.findExpiredTransactions(eq(List.of(PaymentStatus.PROCESSING)), any()))
            .thenReturn(List.of(tx));
        when(orderItemRepo.findByOrderId(order.getId())).thenReturn(List.of());
        when(paymentRepo.save(any())).thenReturn(tx);
        when(orderRepo.save(any())).thenReturn(order);

        job.cleanupExpiredPayments();

        verify(paymentRepo).save(argThat(t -> t.getStatus() == PaymentStatus.TIMEOUT));
        verify(orderRepo).save(argThat(o -> o.getStatus() == OrderStatus.CANCELLED));
        verify(statusHistoryRepo).save(any());
    }

    @Test
    void should_release_stock_on_timeout() {
        Customer customer = TestDataFactory.createCustomer();
        Order order = TestDataFactory.createOrder(customer);
        Product product = TestDataFactory.createProduct();
        OrderItem oi = TestDataFactory.createOrderItem(order, product, 3);
        Stock stock = TestDataFactory.createStock(product, 10);
        stock.setReservedQuantity(3);
        oi.setStockId(stock.getId());

        PaymentTransaction tx = TestDataFactory.createPaymentTransaction(order);
        tx.setStatus(PaymentStatus.PROCESSING);

        when(paymentRepo.findExpiredTransactions(any(), any())).thenReturn(List.of(tx));
        when(orderItemRepo.findByOrderId(order.getId())).thenReturn(List.of(oi));
        when(stockRepo.findById(stock.getId())).thenReturn(Optional.of(stock));
        when(paymentRepo.save(any())).thenReturn(tx);
        when(orderRepo.save(any())).thenReturn(order);

        job.cleanupExpiredPayments();

        verify(stockRepo).save(argThat(s -> s.getReservedQuantity() == 0));
    }

    @Test
    void should_not_process_when_no_expired_transactions() {
        when(paymentRepo.findExpiredTransactions(any(), any())).thenReturn(List.of());

        job.cleanupExpiredPayments();

        verify(orderRepo, never()).save(any());
    }
}
