package com.warehouse.controller;

import com.warehouse.dto.PagedResponse;
import com.warehouse.entity.PaymentTransaction;
import com.warehouse.enums.PaymentStatus;
import com.warehouse.repository.PaymentTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final PaymentTransactionRepository paymentRepo;

    public AdminPaymentController(PaymentTransactionRepository paymentRepo) {
        this.paymentRepo = paymentRepo;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<PagedResponse<Map<String, Object>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String status) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Page<PaymentTransaction> result;

        if (status != null && !status.isEmpty()) {
            try {
                PaymentStatus ps = PaymentStatus.valueOf(status);
                result = paymentRepo.findByStatus(ps, PageRequest.of(page, size, sort));
            } catch (IllegalArgumentException e) {
                result = paymentRepo.findAll(PageRequest.of(page, size, sort));
            }
        } else {
            result = paymentRepo.findAll(PageRequest.of(page, size, sort));
        }

        List<Map<String, Object>> dtos = result.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PagedResponse<>(
            dtos, result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast()));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long id) {
        return paymentRepo.findById(id)
            .map(p -> ResponseEntity.ok(toDetailDto(p)))
            .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toDto(PaymentTransaction p) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", p.getId());
        dto.put("orderId", p.getOrder() != null ? p.getOrder().getId() : null);
        dto.put("orderNumber", p.getOrder() != null ? p.getOrder().getOrderNumber() : null);
        dto.put("customerName", p.getOrder() != null && p.getOrder().getCustomer() != null
                ? p.getOrder().getCustomer().getFirstName() + " " + p.getOrder().getCustomer().getLastName() : null);
        dto.put("customerEmail", p.getOrder() != null && p.getOrder().getCustomer() != null
                ? p.getOrder().getCustomer().getEmail() : null);
        dto.put("paymentProvider", p.getPaymentProvider() != null ? p.getPaymentProvider().name() : null);
        dto.put("status", p.getStatus() != null ? p.getStatus().name() : null);
        dto.put("amount", p.getAmount());
        dto.put("paidAmount", p.getPaidAmount());
        dto.put("currency", p.getCurrency());
        dto.put("cardLastFour", p.getCardLastFour());
        dto.put("cardType", p.getCardType());
        dto.put("cardAssociation", p.getCardAssociation());
        dto.put("installmentCount", p.getInstallmentCount());
        dto.put("bankTransferRef", p.getBankTransferRef());
        dto.put("createdAt", p.getCreatedAt());
        dto.put("paidAt", p.getPaidAt());
        return dto;
    }

    private Map<String, Object> toDetailDto(PaymentTransaction p) {
        Map<String, Object> dto = toDto(p);
        dto.put("providerPaymentId", p.getProviderPaymentId());
        dto.put("conversationId", p.getConversationId());
        dto.put("basketId", p.getBasketId());
        dto.put("installmentPrice", p.getInstallmentPrice());
        dto.put("cardFamily", p.getCardFamily());
        dto.put("cardBankName", p.getCardBankName());
        dto.put("threeDSecure", p.isThreeDSecure());
        dto.put("errorCode", p.getErrorCode());
        dto.put("errorMessage", p.getErrorMessage());
        dto.put("refundAmount", p.getRefundAmount());
        dto.put("refundedAt", p.getRefundedAt());
        dto.put("expiresAt", p.getExpiresAt());
        dto.put("ipAddress", p.getIpAddress());
        dto.put("updatedAt", p.getUpdatedAt());
        return dto;
    }
}
