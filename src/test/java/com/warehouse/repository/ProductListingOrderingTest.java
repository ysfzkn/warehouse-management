package com.warehouse.repository;

import com.warehouse.entity.ProductType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the JPA layer against H2 (PostgreSQL mode) and executes the storefront
 * listing queries. Its job is to catch RUNTIME failures that compilation can't:
 * that the priority {@code ORDER BY} (image-present + in-stock, via correlated
 * subqueries and an enum literal) parses, that Spring Data's derived count query
 * still works, and that the shopper's {@code Pageable} sort appends cleanly.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProductListingOrderingTest {

    @Autowired
    private ProductRepository productRepository;

    private static final Pageable PAGE =
            PageRequest.of(0, 24, Sort.by("createdAt").descending());

    @Test
    void findActiveByFilters_withPriorityOrdering_executes() {
        Page<?> page = productRepository.findActiveByFilters(null, null, null, null, null, PAGE);
        assertThat(page).isNotNull();
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void findActiveByFilters_withAllFiltersAndType_executes() {
        Page<?> page = productRepository.findActiveByFilters(
                "firin", 1L, 2L, 3L, ProductType.SIMPLE, PAGE);
        assertThat(page).isNotNull();
    }

    @Test
    void findActiveByMultiFilters_withPriorityOrdering_executes() {
        Page<?> page = productRepository.findActiveByMultiFilters(
                null, null, List.of(1L, 2L), List.of(3L, 4L), PAGE);
        assertThat(page).isNotNull();
        assertThat(page.getContent()).isEmpty();
    }
}
