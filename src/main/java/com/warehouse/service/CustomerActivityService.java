package com.warehouse.service;

import com.warehouse.dto.CustomerActivityDto;
import com.warehouse.entity.AuditLog;
import com.warehouse.entity.Customer;
import com.warehouse.entity.Order;
import com.warehouse.entity.StockTransfer;
import com.warehouse.enums.AuditAction;
import com.warehouse.enums.OrderStatus;
import com.warehouse.repository.AuditLogRepository;
import com.warehouse.repository.OrderRepository;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.util.TurkishText;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds movements that already went out to the same customer recently.
 *
 * <p>Warehouse staff routinely hand the same order over twice: once as a manual stock reduction
 * with the customer's name in the note, and once as a customer-delivery transfer (or the other
 * way round, days apart, by two different people). Neither path knew about the other, so the
 * second one went through silently and the stock was double-counted against one sale.</p>
 *
 * <p>The lookup runs before saving and only warns — it never blocks. False positives are
 * possible with common names, which is why every hit carries what matched and how confident
 * the match is, and the operator decides.</p>
 */
@Service
public class CustomerActivityService {

    /** Candidate pool ceiling per source. A month of deliveries stays well inside this. */
    private static final int CANDIDATE_LIMIT = 500;
    /** Matches returned to the UI; more than this is noise in a warning dialog. */
    private static final int MAX_RESULTS = 20;
    public static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 365;

    /**
     * Order states in which the goods have left the warehouse. Everything earlier is still on
     * the shelf, and a cancelled order never went anywhere — neither can be a duplicate
     * hand-over. Returned states stay in: the customer did receive the goods once.
     */
    private static final Set<OrderStatus> DISPATCHED = EnumSet.of(
        OrderStatus.SHIPPED, OrderStatus.DELIVERED,
        OrderStatus.RETURN_REQUESTED, OrderStatus.RETURNED);

    private final StockTransferRepository transfers;
    private final AuditLogRepository auditLogs;
    private final OrderRepository orders;

    public CustomerActivityService(StockTransferRepository transfers, AuditLogRepository auditLogs,
                                   OrderRepository orders) {
        this.transfers = transfers;
        this.auditLogs = auditLogs;
        this.orders = orders;
    }

    /**
     * @param name              customer name being delivered to now (transfer flow)
     * @param phone             their phone, when known — the strongest signal
     * @param noteText          free-text note being written now (stock removal flow); any earlier
     *                          customer whose name appears inside it counts as a hit
     * @param days              look-back window
     * @param excludeTransferId a transfer to skip, so editing one does not match itself
     */
    @Transactional(readOnly = true)
    public List<CustomerActivityDto> findRecentActivity(String name, String phone, String noteText,
                                                        Integer days, Long excludeTransferId) {
        boolean hasName = TurkishText.isSearchable(name);
        boolean hasPhone = TurkishText.normalizePhone(phone).length() == 10;
        boolean hasNote = TurkishText.isSearchable(noteText);
        if (!hasName && !hasPhone && !hasNote) return List.of();

        LocalDateTime since = LocalDateTime.now().minusDays(clampDays(days));
        List<CustomerActivityDto> matches = new ArrayList<>();
        matches.addAll(fromTransfers(name, phone, noteText, since, excludeTransferId));
        matches.addAll(fromStockRemovals(name, phone, noteText, since));
        matches.addAll(fromOrders(name, phone, noteText, since));

        matches.sort(Comparator.comparing(CustomerActivityDto::getOccurredAt,
            Comparator.nullsLast(Comparator.reverseOrder())));
        return matches.size() > MAX_RESULTS ? matches.subList(0, MAX_RESULTS) : matches;
    }

    private List<CustomerActivityDto> fromTransfers(String name, String phone, String noteText,
                                                    LocalDateTime since, Long excludeTransferId) {
        List<CustomerActivityDto> result = new ArrayList<>();
        for (StockTransfer transfer : transfers.findRecentCustomerDeliveries(since, PageRequest.of(0, CANDIDATE_LIMIT))) {
            if (excludeTransferId != null && excludeTransferId.equals(transfer.getId())) continue;

            Match match = match(name, phone, noteText, transfer.getCustomerFullName(),
                transfer.getCustomerPhone(), transfer.getNotes());
            if (match == null) continue;

            result.add(CustomerActivityDto.builder()
                .type(CustomerActivityDto.ActivityType.TRANSFER)
                .confidence(match.confidence())
                .occurredAt(transfer.getTransferDate())
                .customerName(transfer.getCustomerFullName())
                .customerPhone(transfer.getCustomerPhone())
                .productName(describeProducts(transfer))
                .quantity(transfer.getQuantity())
                .warehouseName(transfer.getSourceWarehouse() != null ? transfer.getSourceWarehouse().getName() : null)
                .referenceId(transfer.getId())
                .referenceLabel("Transfer #" + transfer.getId())
                .status(transfer.getStatus() != null ? transfer.getStatus().name() : null)
                .note(transfer.getNotes())
                .matchedTokens(match.tokens())
                .matchedOn(match.matchedOn())
                .build());
        }
        return result;
    }

    private List<CustomerActivityDto> fromStockRemovals(String name, String phone, String noteText,
                                                        LocalDateTime since) {
        List<CustomerActivityDto> result = new ArrayList<>();
        List<AuditLog> candidates = auditLogs.findRecentByAction(
            AuditAction.STOCK_REMOVE, since, PageRequest.of(0, CANDIDATE_LIMIT));

        for (AuditLog log : candidates) {
            Match match = match(name, phone, noteText, log.getCustomerName(), log.getCustomerPhone(), log.getNote());
            if (match == null) continue;

            result.add(CustomerActivityDto.builder()
                .type(CustomerActivityDto.ActivityType.STOCK_REMOVAL)
                .confidence(match.confidence())
                .occurredAt(log.getCreatedAt())
                .customerName(log.getCustomerName())
                .customerPhone(log.getCustomerPhone())
                .productName(log.getProductName())
                .quantity(log.getQuantity() == null ? null : Math.abs(log.getQuantity()))
                .warehouseName(log.getWarehouseName())
                .referenceId(log.getId())
                .referenceLabel("Stok çıkışı #" + log.getId())
                .note(log.getNote())
                .matchedTokens(match.tokens())
                .matchedOn(match.matchedOn())
                .build());
        }
        return result;
    }

    private List<CustomerActivityDto> fromOrders(String name, String phone, String noteText,
                                                 LocalDateTime since) {
        List<CustomerActivityDto> result = new ArrayList<>();
        for (Order order : orders.findRecentDispatched(DISPATCHED, since, PageRequest.of(0, CANDIDATE_LIMIT))) {
            // The address snapshot is what the courier actually used, so it is checked as well as
            // the account name — a manual order often carries a different recipient.
            String accountName = customerName(order.getCustomer());
            String shippingName = snapshotName(order.getShippingAddressSnapshot());
            String accountPhone = order.getCustomer() != null ? order.getCustomer().getPhone() : null;
            String shippingPhone = snapshotValue(order.getShippingAddressSnapshot(), "phone");
            String orderNote = joinNotes(order.getCustomerNote(), order.getAdminNote());

            Match match = match(name, phone, noteText, accountName, accountPhone, orderNote);
            if (match == null) {
                match = match(name, phone, noteText, shippingName, shippingPhone, orderNote);
            }
            if (match == null) continue;

            result.add(CustomerActivityDto.builder()
                .type(CustomerActivityDto.ActivityType.ORDER)
                .confidence(match.confidence())
                .occurredAt(dispatchedAt(order))
                .customerName(shippingName != null && !shippingName.isBlank() ? shippingName : accountName)
                .customerPhone(shippingPhone != null && !shippingPhone.isBlank() ? shippingPhone : accountPhone)
                .productName(describeOrderItems(order))
                .quantity(null)
                .warehouseName(null)
                .referenceId(order.getId())
                .referenceLabel("Sipariş " + order.getOrderNumber())
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .note(orderNote)
                .matchedTokens(match.tokens())
                .matchedOn(match.matchedOn())
                .build());
        }
        return result;
    }

    /** Delivery date when we have one, otherwise the last status change. */
    private LocalDateTime dispatchedAt(Order order) {
        if (order.getActualDeliveryDate() != null) return order.getActualDeliveryDate().atStartOfDay();
        return order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt();
    }

    private String customerName(Customer customer) {
        if (customer == null) return null;
        return ((customer.getFirstName() == null ? "" : customer.getFirstName()) + " "
              + (customer.getLastName() == null ? "" : customer.getLastName())).trim();
    }

    private String snapshotName(Map<String, Object> snapshot) {
        String first = snapshotValue(snapshot, "firstName");
        String last = snapshotValue(snapshot, "lastName");
        String joined = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return joined.isEmpty() ? null : joined;
    }

    private String snapshotValue(Map<String, Object> snapshot, String key) {
        if (snapshot == null) return null;
        Object value = snapshot.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String joinNotes(String customerNote, String adminNote) {
        StringBuilder sb = new StringBuilder();
        if (customerNote != null && !customerNote.isBlank()) sb.append(customerNote.trim());
        if (adminNote != null && !adminNote.isBlank()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(adminNote.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private String describeOrderItems(Order order) {
        int count = order.getItems() == null ? 0 : order.getItems().size();
        return count > 0 ? count + " kalem ürün" : null;
    }

    /**
     * Decides whether an earlier record belongs to the customer at hand, and how sure we are.
     *
     * <p>Order matters: a phone match settles it outright; a name found in a dedicated customer
     * field is strong; the same name found only inside a note is a hint, not a fact.</p>
     */
    private Match match(String name, String phone, String noteText,
                        String candidateName, String candidatePhone, String candidateNote) {
        if (TurkishText.phonesMatch(phone, candidatePhone)) {
            return new Match(CustomerActivityDto.Confidence.HIGH, "telefon", List.of());
        }
        if (TurkishText.isSearchable(name) && TurkishText.isSearchable(candidateName)
                && TurkishText.nameOccursIn(name, candidateName)) {
            return new Match(CustomerActivityDto.Confidence.HIGH, "müşteri adı",
                TurkishText.matchedTokens(name, candidateName));
        }
        // Stock-removal flow: the operator is typing a note, and an earlier customer's name
        // turns up inside it ("Ayşe Yılmaz'a kalan 2 adet teslim edildi").
        if (TurkishText.isSearchable(noteText) && TurkishText.isSearchable(candidateName)
                && TurkishText.nameOccursIn(candidateName, noteText)) {
            return new Match(CustomerActivityDto.Confidence.MEDIUM, "not",
                TurkishText.matchedTokens(candidateName, noteText));
        }
        // Transfer flow: this customer's name turns up in an earlier record's note.
        if (TurkishText.isSearchable(name) && TurkishText.isSearchable(candidateNote)
                && TurkishText.nameOccursIn(name, candidateNote)) {
            return new Match(CustomerActivityDto.Confidence.MEDIUM, "not",
                TurkishText.matchedTokens(name, candidateNote));
        }
        return null;
    }

    private String describeProducts(StockTransfer transfer) {
        if (transfer.getProduct() != null && transfer.getProduct().getName() != null) {
            return transfer.getProduct().getName();
        }
        int count = transfer.getItems() == null ? 0 : transfer.getItems().size();
        return count > 0 ? count + " kalem ürün" : null;
    }

    private static int clampDays(Integer days) {
        if (days == null) return DEFAULT_DAYS;
        return Math.max(1, Math.min(days, MAX_DAYS));
    }

    private record Match(CustomerActivityDto.Confidence confidence, String matchedOn, List<String> tokens) {
        Match {
            tokens = tokens == null ? List.of() : List.copyOf(tokens);
        }
    }
}
