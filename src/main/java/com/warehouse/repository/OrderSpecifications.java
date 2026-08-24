package com.warehouse.repository;

import com.warehouse.entity.Order;
import com.warehouse.enums.CargoCompany;
import com.warehouse.enums.OrderStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OrderSpecifications {

    public static Specification<Order> withFilters(
            String status, String paymentMethod, String cargoCompany, String channel,
            LocalDateTime startDate, LocalDateTime endDate, String search) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Status filter
            if (status != null && !status.isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("status"), OrderStatus.valueOf(status)));
                } catch (Exception ignored) {}
            }

            // Payment method filter
            if (paymentMethod != null && !paymentMethod.isBlank()) {
                predicates.add(cb.equal(root.get("paymentMethod"), paymentMethod));
            }

            if (channel != null && !channel.isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("orderChannel"), com.warehouse.enums.OrderChannel.valueOf(channel)));
                } catch (Exception ignored) {}
            }

            // Cargo company filter
            if (cargoCompany != null && !cargoCompany.isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("cargoCompany"), CargoCompany.valueOf(cargoCompany)));
                } catch (Exception ignored) {}
            }

            // Date range
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            // Search — across multiple fields
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                Join<?, ?> customer = root.join("customer", JoinType.LEFT);

                Predicate searchPredicate = cb.or(
                    cb.like(cb.lower(root.get("orderNumber")), pattern),
                    cb.like(cb.lower(root.get("cargoTrackingNo")), pattern),
                    cb.like(cb.lower(customer.get("firstName")), pattern),
                    cb.like(cb.lower(customer.get("lastName")), pattern),
                    cb.like(cb.lower(customer.get("email")), pattern),
                    cb.like(cb.lower(customer.get("phone")), pattern)
                );
                predicates.add(searchPredicate);
            }

            // Fetch customer eagerly (avoid N+1)
            if (query != null && query.getResultType() == Order.class) {
                root.fetch("customer", JoinType.LEFT);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
