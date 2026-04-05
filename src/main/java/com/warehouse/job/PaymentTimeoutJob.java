package com.warehouse.job;

import com.warehouse.entity.OrderItem;
import com.warehouse.entity.PaymentTransaction;
import com.warehouse.entity.Stock;
import com.warehouse.enums.OrderStatus;
import com.warehouse.enums.PaymentStatus;
import com.warehouse.util.OrderStatusHistoryFactory;
import com.warehouse.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class PaymentTimeoutJob {

    private static final Logger logger = LoggerFactory.getLogger(PaymentTimeoutJob.class);

    private final PaymentTransactionRepository paymentRepo;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final OrderStatusHistoryRepository statusHistoryRepo;
    private final StockRepository stockRepo;

    public PaymentTimeoutJob(PaymentTransactionRepository paymentRepo,
                              OrderRepository orderRepo,
                              OrderItemRepository orderItemRepo,
                              OrderStatusHistoryRepository statusHistoryRepo,
                              StockRepository stockRepo) {
        this.paymentRepo = paymentRepo;
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.statusHistoryRepo = statusHistoryRepo;
        this.stockRepo = stockRepo;
    }

    @Scheduled(fixedRate = 60000) // Every 60 seconds
    @Transactional
    public void cleanupExpiredPayments() {
        List<PaymentTransaction> expired = paymentRepo.findExpiredTransactions(
            List.of(PaymentStatus.PROCESSING), LocalDateTime.now());

        for (PaymentTransaction tx : expired) {
            try {
                tx.setStatus(PaymentStatus.TIMEOUT);
                paymentRepo.save(tx);

                var order = tx.getOrder();
                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    // Release reserved stock
                    List<OrderItem> items = orderItemRepo.findByOrderId(order.getId());
                    for (OrderItem item : items) {
                        if (item.getStockId() != null) {
                            stockRepo.findById(item.getStockId()).ifPresent(stock -> {
                                stock.setReservedQuantity(Math.max(0, stock.getReservedQuantity() - item.getQuantity()));
                                stockRepo.save(stock);
                            });
                        }
                    }

                    order.setStatus(OrderStatus.CANCELLED);
                    orderRepo.save(order);

                    statusHistoryRepo.save(OrderStatusHistoryFactory.create(
                        order, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED,
                        "system", "TIMEOUT_JOB", "Ödeme süresi doldu (3D Secure timeout)"));

                    logger.info("Payment timeout: txId={}, orderId={} cancelled, stock released", tx.getId(), order.getId());
                }
            } catch (Exception e) {
                logger.error("Error processing timeout for txId={}: {}", tx.getId(), e.getMessage());
            }
        }
        if (!expired.isEmpty()) {
            logger.info("Payment timeout job: processed {} expired transactions", expired.size());
        }
    }
}
