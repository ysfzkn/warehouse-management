package com.warehouse.job;

import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;
import com.warehouse.entity.PaymentTransaction;
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
public class BankTransferExpiryJob {

    private static final Logger logger = LoggerFactory.getLogger(BankTransferExpiryJob.class);

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final PaymentTransactionRepository paymentRepo;
    private final OrderStatusHistoryRepository statusHistoryRepo;
    private final StockRepository stockRepo;

    public BankTransferExpiryJob(OrderRepository orderRepo,
                                  OrderItemRepository orderItemRepo,
                                  PaymentTransactionRepository paymentRepo,
                                  OrderStatusHistoryRepository statusHistoryRepo,
                                  StockRepository stockRepo) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.paymentRepo = paymentRepo;
        this.statusHistoryRepo = statusHistoryRepo;
        this.stockRepo = stockRepo;
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @Transactional
    public void cleanupExpiredBankTransfers() {
        List<Order> expired = orderRepo.findExpiredBankTransferOrders(
            OrderStatus.PENDING_PAYMENT, "BANK_TRANSFER", LocalDateTime.now());

        for (Order order : expired) {
            try {
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

                // Update payment transaction
                paymentRepo.findByOrderIdAndStatus(order.getId(), PaymentStatus.INITIATED)
                    .ifPresent(tx -> {
                        tx.setStatus(PaymentStatus.TIMEOUT);
                        paymentRepo.save(tx);
                    });

                // Cancel order
                order.setStatus(OrderStatus.CANCELLED);
                orderRepo.save(order);

                statusHistoryRepo.save(OrderStatusHistoryFactory.create(
                    order, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED,
                    "system", "BANK_TRANSFER_EXPIRY_JOB",
                    "Havale/EFT süresi doldu (" + order.getBankTransferDeadline() + ")"));

                logger.info("Bank transfer expired: orderId={}, orderNumber={}", order.getId(), order.getOrderNumber());
            } catch (Exception e) {
                logger.error("Error processing bank transfer expiry for orderId={}: {}", order.getId(), e.getMessage());
            }
        }
        if (!expired.isEmpty()) {
            logger.info("Bank transfer expiry job: processed {} expired orders", expired.size());
        }
    }
}
