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
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
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
    @SchedulerLock(name = "bankTransferExpiry", lockAtMostFor = "PT4M", lockAtLeastFor = "PT2M")
    public void cleanupExpiredBankTransfers() {
        List<Order> expired = orderRepo.findExpiredBankTransferOrders(
            OrderStatus.PENDING_PAYMENT, "BANK_TRANSFER", LocalDateTime.now());

        int processed = 0;
        for (Order order : expired) {
            try {
                // Each order in its own transaction — if one fails the others are not affected
                expireOne(order.getId());
                processed++;
            } catch (Exception e) {
                logger.error("Error processing bank transfer expiry for orderId={}: {}",
                        order.getId(), e.getMessage());
            }
        }
        if (processed > 0) {
            logger.info("Bank transfer expiry job: processed {} expired orders", processed);
        }
    }

    /**
     * Performs the expiry for a single order in a separate transaction.
     * PESSIMISTIC_WRITE lock — prevents a race when the admin clicks "Approve".
     * If the order became PAID/CANCELLED in the meantime, skip idempotently.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOne(Long orderId) {
        Order order = orderRepo.findByIdForUpdate(orderId).orElse(null);
        if (order == null) {
            logger.debug("Expire skip: order {} no longer exists", orderId);
            return;
        }
        // Idempotent — the admin may have confirmed/rejected in the meantime
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            logger.info("Expire skip: order {} status changed to {} (admin action veya başka job)",
                    orderId, order.getStatus());
            return;
        }
        // Has the deadline really passed? A clock skew between the job and the DB could cause a false positive
        if (order.getBankTransferDeadline() != null
                && order.getBankTransferDeadline().isAfter(LocalDateTime.now())) {
            logger.warn("Expire skip: order {} deadline ({}) henüz geçmemiş",
                    orderId, order.getBankTransferDeadline());
            return;
        }

        // Release the stock
        List<OrderItem> items = orderItemRepo.findByOrderId(order.getId());
        for (OrderItem item : items) {
            if (item.getStockId() != null) {
                stockRepo.findById(item.getStockId()).ifPresent(stock -> {
                    stock.setReservedQuantity(Math.max(0, stock.getReservedQuantity() - item.getQuantity()));
                    stockRepo.save(stock);
                });
            }
        }

        // Tx → TIMEOUT
        paymentRepo.findByOrderIdAndStatus(order.getId(), PaymentStatus.INITIATED)
            .ifPresent(tx -> {
                tx.setStatus(PaymentStatus.TIMEOUT);
                paymentRepo.save(tx);
            });

        order.setStatus(OrderStatus.CANCELLED);
        orderRepo.save(order);

        statusHistoryRepo.save(OrderStatusHistoryFactory.create(
            order, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED,
            "system", "BANK_TRANSFER_EXPIRY_JOB",
            "Havale/EFT süresi doldu (" + order.getBankTransferDeadline() + ")"));

        logger.info("Bank transfer expired: orderId={}, orderNumber={}",
                order.getId(), order.getOrderNumber());
    }
}
