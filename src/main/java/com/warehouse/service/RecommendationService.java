package com.warehouse.service;

import com.warehouse.entity.Product;
import com.warehouse.enums.OrderStatus;
import com.warehouse.repository.OrderItemRepository;
import com.warehouse.repository.ProductRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Product recommendations.
 *
 * <p><b>"Customers also bought"</b> is derived from real co-purchase data: for the
 * orders that contained product X (in a paid/fulfilled state), which other products
 * appeared in those same orders, ranked by frequency. When a product is new or
 * rarely purchased and has too few co-purchases, the list is topped up with popular
 * products from the same category so the rail is never empty.
 */
@Service
public class RecommendationService {

    /** Order states that represent a genuine purchase (exclude pending/cancelled/returned). */
    private static final List<OrderStatus> PURCHASED_STATES = List.of(
            OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    public RecommendationService(OrderItemRepository orderItemRepository,
                                 ProductRepository productRepository) {
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
    }

    /**
     * Ordered list of recommended product ids for the given product. Co-purchase
     * matches first (most frequent first), then same-category popularity fallback.
     * Cached briefly — the underlying signals (orders, view counts) move slowly.
     *
     * @param productId the product being viewed
     * @param limit     max ids to return
     */
    @Cacheable(value = "alsoBought", key = "#productId + '_' + #limit")
    @Transactional(readOnly = true)
    public List<Long> alsoBoughtProductIds(Long productId, int limit) {
        if (productId == null || limit <= 0) return List.of();

        Set<Long> result = new LinkedHashSet<>();

        // 1) Genuine co-purchases, ranked by frequency.
        for (Object[] row : orderItemRepository.findFrequentlyBoughtTogether(
                productId, PURCHASED_STATES, PageRequest.of(0, limit))) {
            if (row[0] != null) result.add(((Number) row[0]).longValue());
        }

        // 2) Top up from the same category by popularity if we're short.
        if (result.size() < limit) {
            Product product = productRepository.findById(productId).orElse(null);
            Long categoryId = (product != null && product.getCategory() != null)
                    ? product.getCategory().getId() : null;
            if (categoryId != null) {
                List<Long> popular = productRepository.findPopularIdsByCategory(
                        categoryId, productId, PageRequest.of(0, limit * 2));
                for (Long id : popular) {
                    if (result.size() >= limit) break;
                    result.add(id);
                }
            }
        }

        return new ArrayList<>(result).subList(0, Math.min(result.size(), limit));
    }
}
