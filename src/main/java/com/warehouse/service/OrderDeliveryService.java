package com.warehouse.service;

import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;
import com.warehouse.entity.StockEvent;
import com.warehouse.enums.PaymentStatus;
import com.warehouse.enums.StockEventSource;
import com.warehouse.enums.StockEventType;
import com.warehouse.repository.OrderItemRepository;
import com.warehouse.repository.OrderStatusHistoryRepository;
import com.warehouse.repository.PaymentTransactionRepository;
import com.warehouse.repository.StockEventRepository;
import com.warehouse.repository.StockRepository;
import com.warehouse.util.OrderStatusHistoryFactory;
import com.warehouse.util.OrderStatusMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Side effects that must happen exactly once when an order reaches DELIVERED,
 * regardless of who moved it there.
 *
 * <p>Previously this logic lived inline in {@code AdminOrderController} only, so an
 * order marked delivered by the cargo tracking job (and, from now on, by a Kargonomi
 * webhook) never had its reserved stock converted into an actual sale — the stock
 * stayed reserved forever and no StockEvent was written. Both paths now call here.
 *
 * <p>Does NOT change {@code order.status} or write the status-history row for the
 * status change itself; the caller owns that, because the caller also owns the
 * transition validation.
 */
@Service
public class OrderDeliveryService {

    private static final Logger logger = LoggerFactory.getLogger(OrderDeliveryService.class);

    private final OrderItemRepository orderItemRepository;
    private final StockRepository stockRepository;
    private final StockEventRepository stockEventRepository;
    private final PaymentTransactionRepository paymentRepo;
    private final OrderStatusHistoryRepository statusHistoryRepository;

    public OrderDeliveryService(OrderItemRepository orderItemRepository,
                                 StockRepository stockRepository,
                                 StockEventRepository stockEventRepository,
                                 PaymentTransactionRepository paymentRepo,
                                 OrderStatusHistoryRepository statusHistoryRepository) {
        this.orderItemRepository = orderItemRepository;
        this.stockRepository = stockRepository;
        this.stockEventRepository = stockEventRepository;
        this.paymentRepo = paymentRepo;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    /**
     * Converts the order's stock reservations into an actual sale and, for door payments,
     * completes the pending payment transaction.
     *
     * @param order  the order that has just reached DELIVERED
     * @param actor  who triggered it ("system", an admin username, …) — for the audit trail
     * @param source change source label ("ADMIN", "KARGONOMI_WEBHOOK", "CARGO_TRACKING_JOB")
     */
    @Transactional
    public void applyDeliveredEffects(Order order, String actor, String source) {
        deductReservedStock(order);
        completeDoorPayment(order, actor, source);
    }

    /** Reserved quantity → actual deduction, with a StockEvent per line for traceability. */
    private void deductReservedStock(Order order) {
        for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
            Long productId = item.getProduct() != null ? item.getProduct().getId() : null;

            if (item.getStockId() != null) {
                stockRepository.findById(item.getStockId()).ifPresent(stock -> {
                    int qty = item.getQuantity();
                    int oldQty = stock.getQuantity();
                    stock.setQuantity(Math.max(0, oldQty - qty));
                    stock.setReservedQuantity(Math.max(0, stock.getReservedQuantity() - qty));
                    stockRepository.save(stock);

                    StockEvent event = new StockEvent();
                    event.setStockId(stock.getId());
                    event.setProductId(productId);
                    event.setEventType(StockEventType.QUANTITY_CHANGED);
                    event.setOldValue(oldQty);
                    event.setNewValue(stock.getQuantity());
                    event.setSource(StockEventSource.ORDER);
                    event.setSourceDetail("Sipariş #" + order.getOrderNumber() + " teslim edildi (" + qty + " adet)");
                    event.setOrderNumber(order.getOrderNumber());
                    stockEventRepository.save(event);
                });
            } else {
                // StockId is null but still log the event (traceability)
                StockEvent event = new StockEvent();
                event.setProductId(productId);
                event.setEventType(StockEventType.QUANTITY_CHANGED);
                event.setOldValue(item.getQuantity());
                event.setNewValue(0);
                event.setSource(StockEventSource.ORDER);
                event.setSourceDetail("Sipariş #" + order.getOrderNumber() + " teslim edildi ("
                        + item.getQuantity() + " adet, stok kaydı yok)");
                event.setOrderNumber(order.getOrderNumber());
                stockEventRepository.save(event);
            }
        }
    }

    /** Door payment → auto-complete the initiated payment transaction with an audit trail. */
    private void completeDoorPayment(Order order, String actor, String source) {
        if (!OrderStatusMachine.isDoorPayment(order.getPaymentMethod())) return;

        paymentRepo.findByOrderIdAndStatus(order.getId(), PaymentStatus.INITIATED).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            payment.setPaidAmount(order.getGrandTotal());
            paymentRepo.save(payment);

            statusHistoryRepository.save(OrderStatusHistoryFactory.create(
                    order, "PAYMENT_INITIATED", "PAYMENT_SUCCESS",
                    actor, source, "Kapıda ödeme tahsil edildi"));

            logger.info("Door payment auto-completed on delivery: order={}, source={}",
                    order.getOrderNumber(), source);
        });
    }
}
