package com.warehouse.repository.specification;

import com.warehouse.dto.admin.CustomerFilterRequest;
import com.warehouse.entity.Customer;
import com.warehouse.enums.CustomerStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class CustomerSpecifications {

    private CustomerSpecifications() {}

    public static Specification<Customer> withFilter(CustomerFilterRequest filter) {
        return Specification.where(withSearch(filter.getSearch()))
            .and(withStatus(filter.getStatus()))
            .and(withEmailVerified(filter.getEmailVerified()))
            .and(withCreatedFrom(filter.getCreatedFrom()))
            .and(withCreatedTo(filter.getCreatedTo()));
    }

    private static Specification<Customer> withSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
            cb.like(cb.lower(root.get("email")), pattern),
            cb.like(cb.lower(root.get("firstName")), pattern),
            cb.like(cb.lower(root.get("lastName")), pattern),
            cb.like(cb.lower(root.get("phone")), pattern)
        );
    }

    private static Specification<Customer> withStatus(CustomerStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Specification<Customer> withEmailVerified(Boolean emailVerified) {
        if (emailVerified == null) return null;
        return (root, query, cb) -> cb.equal(root.get("emailVerified"), emailVerified);
    }

    private static Specification<Customer> withCreatedFrom(LocalDateTime from) {
        if (from == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    private static Specification<Customer> withCreatedTo(LocalDateTime to) {
        if (to == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
