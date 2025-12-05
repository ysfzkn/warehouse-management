package com.warehouse.repository.specification;

import com.warehouse.dto.AuditLogFilter;
import com.warehouse.entity.AuditLog;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {}

    public static Specification<AuditLog> withFilter(AuditLogFilter filter) {
        return (root, query, cb) -> {
            if (query != null && query.getOrderList().isEmpty()) {
                query.orderBy(cb.desc(root.get("createdAt")));
            }
            if (filter == null) {
                return cb.conjunction();
            }
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getWarehouseId() != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("warehouseId"), filter.getWarehouseId()),
                        cb.equal(root.get("sourceWarehouseId"), filter.getWarehouseId()),
                        cb.equal(root.get("destinationWarehouseId"), filter.getWarehouseId())
                ));
            }

            if (filter.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getStartDate()));
            }
            if (filter.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getEndDate()));
            }

            if (StringUtils.hasText(filter.getSearch())) {
                String like = "%" + filter.getSearch().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("details")), like),
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("productName")), like),
                        cb.like(cb.lower(root.get("productSku")), like),
                        cb.like(cb.lower(root.get("warehouseName")), like),
                        cb.like(cb.lower(root.get("sourceWarehouseName")), like),
                        cb.like(cb.lower(root.get("destinationWarehouseName")), like)
                ));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

