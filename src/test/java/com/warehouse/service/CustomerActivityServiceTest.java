package com.warehouse.service;

import com.warehouse.dto.CustomerActivityDto;
import com.warehouse.entity.AuditLog;
import com.warehouse.entity.Customer;
import com.warehouse.entity.Order;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.AuditAction;
import com.warehouse.enums.OrderStatus;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.repository.AuditLogRepository;
import com.warehouse.repository.OrderRepository;
import com.warehouse.repository.StockTransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerActivityServiceTest {

    @Mock private StockTransferRepository transfers;
    @Mock private AuditLogRepository auditLogs;
    @Mock private OrderRepository orders;

    private CustomerActivityService service;

    @BeforeEach
    void setUp() {
        service = new CustomerActivityService(transfers, auditLogs, orders);
        when(transfers.findRecentCustomerDeliveries(any(), any())).thenReturn(List.of());
        when(auditLogs.findRecentByAction(eq(AuditAction.STOCK_REMOVE), any(), any())).thenReturn(List.of());
        when(orders.findRecentDispatched(any(), any(), any())).thenReturn(List.of());
    }

    private Order order(Long id, String number, OrderStatus status,
                        String firstName, String lastName, String phone) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setPhone(phone);

        Order o = new Order();
        o.setId(id);
        o.setOrderNumber(number);
        o.setStatus(status);
        o.setCustomer(customer);
        o.setUpdatedAt(LocalDateTime.now().minusDays(2));
        // Map.of rejects nulls, and a snapshot without a phone is a perfectly normal case.
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        if (firstName != null) snapshot.put("firstName", firstName);
        if (lastName != null) snapshot.put("lastName", lastName);
        if (phone != null) snapshot.put("phone", phone);
        o.setShippingAddressSnapshot(snapshot);
        return o;
    }

    private StockTransfer delivery(Long id, String name, String phone, String note) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setName("Merkez Depo");
        StockTransfer t = new StockTransfer();
        t.setId(id);
        t.setTransferType(TransferType.CUSTOMER_DELIVERY);
        t.setStatus(TransferStatus.COMPLETED);
        t.setTransferDate(LocalDateTime.now().minusDays(3));
        t.setCustomerFullName(name);
        t.setCustomerPhone(phone);
        t.setNotes(note);
        t.setQuantity(2);
        t.setSourceWarehouse(warehouse);
        return t;
    }

    private AuditLog removal(Long id, String customerName, String note) {
        AuditLog log = new AuditLog();
        log.setId(id);
        log.setAction(AuditAction.STOCK_REMOVE);
        log.setCreatedAt(LocalDateTime.now().minusDays(5));
        log.setCustomerName(customerName);
        log.setNote(note);
        log.setProductName("Buzdolabı");
        log.setQuantity(-1);
        log.setWarehouseName("Merkez Depo");
        return log;
    }

    @Test
    void should_return_nothing_when_there_is_nothing_to_search_on() {
        assertThat(service.findRecentActivity("  ", "", "", 30, null)).isEmpty();
    }

    @Test
    void should_flag_an_earlier_delivery_to_the_same_name() {
        when(transfers.findRecentCustomerDeliveries(any(), any()))
            .thenReturn(List.of(delivery(7L, "Ayşe Yılmaz", null, null)));

        List<CustomerActivityDto> matches = service.findRecentActivity("AYSE YILMAZ", null, null, 30, null);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getType()).isEqualTo(CustomerActivityDto.ActivityType.TRANSFER);
        assertThat(matches.get(0).getConfidence()).isEqualTo(CustomerActivityDto.Confidence.HIGH);
        assertThat(matches.get(0).getMatchedOn()).isEqualTo("müşteri adı");
        assertThat(matches.get(0).getReferenceLabel()).isEqualTo("Transfer #7");
    }

    @Test
    void phone_match_should_win_even_when_the_name_was_typed_differently() {
        when(transfers.findRecentCustomerDeliveries(any(), any()))
            .thenReturn(List.of(delivery(8L, "A. Yılmaz", "0532 111 22 33", null)));

        List<CustomerActivityDto> matches =
            service.findRecentActivity("Ayşe Yilmaz Kaya", "+90 532 111 22 33", null, 30, null);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getConfidence()).isEqualTo(CustomerActivityDto.Confidence.HIGH);
        assertThat(matches.get(0).getMatchedOn()).isEqualTo("telefon");
    }

    @Test
    void should_flag_an_earlier_customer_named_inside_the_note_being_typed() {
        // Stock removal flow: operator types a note, an earlier delivery's customer is in it.
        when(transfers.findRecentCustomerDeliveries(any(), any()))
            .thenReturn(List.of(delivery(9L, "Mehmet Demir", null, null)));

        List<CustomerActivityDto> matches = service.findRecentActivity(
            null, null, "Mehmet Demir'in kalan 1 adedi elden teslim edildi", 30, null);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getConfidence()).isEqualTo(CustomerActivityDto.Confidence.MEDIUM);
        assertThat(matches.get(0).getMatchedOn()).isEqualTo("not");
    }

    @Test
    void should_flag_an_earlier_manual_removal_for_the_same_customer() {
        when(auditLogs.findRecentByAction(eq(AuditAction.STOCK_REMOVE), any(), any()))
            .thenReturn(List.of(removal(31L, null, "Ayşe Yılmaz'a teslim edildi")));

        List<CustomerActivityDto> matches = service.findRecentActivity("Ayşe Yılmaz", null, null, 30, null);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getType()).isEqualTo(CustomerActivityDto.ActivityType.STOCK_REMOVAL);
        assertThat(matches.get(0).getQuantity()).isEqualTo(1); // sign dropped for display
        assertThat(matches.get(0).getReferenceLabel()).isEqualTo("Stok çıkışı #31");
    }

    @Test
    void should_not_flag_a_different_customer() {
        when(transfers.findRecentCustomerDeliveries(any(), any()))
            .thenReturn(List.of(delivery(10L, "Ayşe Kaya", "0555 000 00 00", null)));
        when(auditLogs.findRecentByAction(eq(AuditAction.STOCK_REMOVE), any(), any()))
            .thenReturn(List.of(removal(32L, "Fatma Yılmaz", null)));

        assertThat(service.findRecentActivity("Ayşe Yılmaz", "0532 111 22 33", null, 30, null)).isEmpty();
    }

    @Test
    void should_skip_the_transfer_being_edited() {
        when(transfers.findRecentCustomerDeliveries(any(), any()))
            .thenReturn(List.of(delivery(11L, "Ayşe Yılmaz", null, null)));

        assertThat(service.findRecentActivity("Ayşe Yılmaz", null, null, 30, 11L)).isEmpty();
    }

    @Test
    void should_flag_an_order_already_delivered_to_the_same_customer() {
        when(orders.findRecentDispatched(any(), any(), any()))
            .thenReturn(List.of(order(40L, "ORD20260801AB12", OrderStatus.DELIVERED, "Ayşe", "Yılmaz", "05321112233")));

        List<CustomerActivityDto> matches = service.findRecentActivity("Ayse Yilmaz", null, null, 30, null);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getType()).isEqualTo(CustomerActivityDto.ActivityType.ORDER);
        assertThat(matches.get(0).getReferenceLabel()).isEqualTo("Sipariş ORD20260801AB12");
        assertThat(matches.get(0).getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void order_delivery_date_should_win_over_the_status_timestamp() {
        Order delivered = order(41L, "ORD1", OrderStatus.DELIVERED, "Ayşe", "Yılmaz", null);
        delivered.setActualDeliveryDate(LocalDate.now().minusDays(4));
        when(orders.findRecentDispatched(any(), any(), any())).thenReturn(List.of(delivered));

        List<CustomerActivityDto> matches = service.findRecentActivity("Ayşe Yılmaz", null, null, 30, null);

        assertThat(matches.get(0).getOccurredAt().toLocalDate()).isEqualTo(LocalDate.now().minusDays(4));
    }

    @Test
    void should_match_the_shipping_recipient_when_it_differs_from_the_account() {
        // Manual orders are often placed under one account for a different recipient.
        Order o = order(42L, "ORD2", OrderStatus.SHIPPED, "Ali", "Veli", "05550000000");
        o.setShippingAddressSnapshot(Map.of("firstName", "Ayşe", "lastName", "Yılmaz", "phone", "05321112233"));
        when(orders.findRecentDispatched(any(), any(), any())).thenReturn(List.of(o));

        List<CustomerActivityDto> matches = service.findRecentActivity("Ayşe Yılmaz", null, null, 30, null);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getCustomerName()).isEqualTo("Ayşe Yılmaz");
    }

    @Test
    void should_return_newest_first_across_both_sources() {
        StockTransfer older = delivery(12L, "Ayşe Yılmaz", null, null);
        older.setTransferDate(LocalDateTime.now().minusDays(20));
        when(transfers.findRecentCustomerDeliveries(any(), any())).thenReturn(List.of(older));

        AuditLog newer = removal(33L, "Ayşe Yılmaz", null);
        newer.setCreatedAt(LocalDateTime.now().minusDays(1));
        when(auditLogs.findRecentByAction(eq(AuditAction.STOCK_REMOVE), any(), any())).thenReturn(List.of(newer));

        List<CustomerActivityDto> matches = service.findRecentActivity("Ayşe Yılmaz", null, null, 30, null);

        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).getReferenceLabel()).isEqualTo("Stok çıkışı #33");
        assertThat(matches.get(1).getReferenceLabel()).isEqualTo("Transfer #12");
    }
}
