package com.warehouse.controller.store;

import com.warehouse.repository.SupportTicketRepository;
import com.warehouse.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Customer-facing listing of the current user's own support tickets.
 * Split out of {@code StoreOrderController} so the URL lives under the correct namespace
 * (<code>/api/store/support-tickets</code>) instead of under the unrelated orders namespace.
 */
@RestController
@RequestMapping("/api/store/support-tickets")
public class StoreSupportTicketController {

    private final SupportTicketRepository supportTicketRepo;
    private final JwtService jwtService;

    public StoreSupportTicketController(SupportTicketRepository supportTicketRepo, JwtService jwtService) {
        this.supportTicketRepo = supportTicketRepo;
        this.jwtService = jwtService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> mySupportTickets(HttpServletRequest request) {
        Long customerId = extractCustomerId(request);
        if (customerId == null) return ResponseEntity.ok(List.of());

        var tickets = supportTicketRepo.findByCustomerId(customerId,
                PageRequest.of(0, 50, Sort.by("createdAt").descending()));

        List<Map<String, Object>> dtos = tickets.getContent().stream().map(t -> {
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
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    private Long extractCustomerId(HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
            else {
                if (request.getCookies() != null) {
                    for (var c : request.getCookies()) {
                        if ("access_token".equals(c.getName())) { token = c.getValue(); break; }
                    }
                }
            }
            if (token == null) return null;
            return jwtService.extractCustomerId(token);
        } catch (Exception e) {
            return null;
        }
    }
}
