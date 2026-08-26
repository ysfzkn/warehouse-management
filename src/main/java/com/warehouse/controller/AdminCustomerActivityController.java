package com.warehouse.controller;

import com.warehouse.dto.CustomerActivityDto;
import com.warehouse.service.CustomerActivityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-save check for "this customer already received something recently".
 *
 * <p>Read-only and advisory: the stock removal and transfer screens call it right before saving
 * and show whatever comes back as a warning. Available to the warehouse roles because those are
 * the people performing the movements.</p>
 */
@RestController
@RequestMapping("/api/admin/customer-activity")
@PreAuthorize("hasAnyRole('ADMIN', 'STOCK_IN', 'STOCK_OUT')")
public class AdminCustomerActivityController {

    private final CustomerActivityService customerActivityService;

    public AdminCustomerActivityController(CustomerActivityService customerActivityService) {
        this.customerActivityService = customerActivityService;
    }

    /**
     * @param name     customer being delivered to now (transfer flow)
     * @param phone    their phone number, when known
     * @param note     the note being typed now (stock removal flow)
     * @param days     look-back window, defaults to 30
     * @param excludeTransferId transfer to skip so an edit does not match itself
     */
    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> recent(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Long excludeTransferId) {

        List<CustomerActivityDto> matches =
            customerActivityService.findRecentActivity(name, phone, note, days, excludeTransferId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("days", days == null ? CustomerActivityService.DEFAULT_DAYS : days);
        body.put("count", matches.size());
        body.put("matches", matches);
        return ResponseEntity.ok(body);
    }
}
