package com.warehouse.controller;

import com.warehouse.entity.SupportTicket;
import com.warehouse.repository.SupportTicketRepository;
import com.warehouse.util.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/support-tickets")
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class AdminSupportTicketController {

    private final SupportTicketRepository ticketRepo;

    public AdminSupportTicketController(SupportTicketRepository ticketRepo) {
        this.ticketRepo = ticketRepo;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        String statusParam = (status != null && !status.isBlank()) ? status : null;
        String searchParam = (search != null && !search.isBlank()) ? search : null;

        Page<SupportTicket> result = ticketRepo.findByFilters(statusParam, searchParam,
            PageRequest.of(page, size, Sort.by("createdAt").descending()));

        List<Map<String, Object>> dtos = result.getContent().stream().map(t -> {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", t.getId());
            dto.put("orderNumber", t.getOrderNumber());
            dto.put("topic", t.getTopic());
            dto.put("message", t.getMessage());
            dto.put("status", t.getStatus());
            dto.put("adminReply", t.getAdminReply());
            dto.put("repliedBy", t.getRepliedBy());
            dto.put("repliedAt", t.getRepliedAt());
            dto.put("createdAt", t.getCreatedAt());
            try {
                dto.put("customerName", t.getCustomer().getFirstName() + " " + t.getCustomer().getLastName());
                dto.put("customerEmail", t.getCustomer().getEmail());
                dto.put("customerPhone", t.getCustomer().getPhone());
            } catch (Exception e) {
                dto.put("customerName", "");
                dto.put("customerEmail", "");
                dto.put("customerPhone", "");
            }
            return dto;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", dtos);
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("number", result.getNumber());
        response.put("size", result.getSize());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/counts")
    public ResponseEntity<Map<String, Long>> counts() {
        return ResponseEntity.ok(Map.of(
            "OPEN", ticketRepo.countByStatus("OPEN"),
            "IN_PROGRESS", ticketRepo.countByStatus("IN_PROGRESS"),
            "RESOLVED", ticketRepo.countByStatus("RESOLVED"),
            "CLOSED", ticketRepo.countByStatus("CLOSED")
        ));
    }

    @PutMapping("/{id}/reply")
    @Transactional
    public ResponseEntity<Map<String, String>> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ticketRepo.findById(id).map(ticket -> {
            String reply = body.getOrDefault("reply", "").trim();
            if (reply.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "Yanıt boş olamaz."));
            ticket.setAdminReply(reply);
            ticket.setRepliedBy(CurrentUser.usernameOrSystem());
            ticket.setRepliedAt(LocalDateTime.now());
            ticket.setStatus("RESOLVED");
            ticketRepo.save(ticket);
            return ResponseEntity.ok(Map.of("message", "Yanıt gönderildi."));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<Map<String, String>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ticketRepo.findById(id).map(ticket -> {
            String newStatus = body.getOrDefault("status", "");
            if (!List.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED").contains(newStatus)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Geçersiz durum."));
            }
            ticket.setStatus(newStatus);
            ticketRepo.save(ticket);
            return ResponseEntity.ok(Map.of("message", "Durum güncellendi."));
        }).orElse(ResponseEntity.notFound().build());
    }
}
