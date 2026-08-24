package com.warehouse.job;

import com.warehouse.entity.Order;
import com.warehouse.repository.OrderRepository;
import com.warehouse.service.NotificationService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class ManualPaymentReminderJob {
    private final OrderRepository orders;
    private final NotificationService notifications;

    public ManualPaymentReminderJob(OrderRepository orders, NotificationService notifications) {
        this.orders = orders;
        this.notifications = notifications;
    }

    @Scheduled(fixedRate = 60000)
    @SchedulerLock(name = "manualPaymentReminder", lockAtMostFor = "PT50S", lockAtLeastFor = "PT5S")
    @Transactional
    public void remind() {
        for (Order order : orders.findPaymentRemindersDue(LocalDateTime.now())) {
            String customer = order.getCustomer() == null ? "Müşteri" : order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName();
            notifications.create("Ödeme hatırlatması",
                order.getOrderNumber() + " — " + customer + " için ödeme bekleniyor. Vade: " +
                    (order.getPaymentDueAt() == null ? "belirtilmedi" : order.getPaymentDueAt()),
                "Order", order.getId());
            order.setPaymentReminderSentAt(LocalDateTime.now());
            orders.save(order);
        }
    }
}
