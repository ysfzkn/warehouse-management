package com.warehouse.repository;

import com.warehouse.entity.StockEvent;
import com.warehouse.enums.StockEventSource;
import com.warehouse.enums.StockEventType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class StockEventSpecifications {

    public static Specification<StockEvent> withFilters(
            StockEventSource source, StockEventType eventType,
            Long productId, LocalDateTime startDate, LocalDateTime endDate, String search) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (source != null) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (eventType != null) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            if (productId != null) {
                predicates.add(cb.equal(root.get("productId"), productId));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("sourceDetail")), "%" + search.toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
