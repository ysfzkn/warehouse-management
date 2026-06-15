package com.warehouse.repository;

import com.warehouse.entity.OrderItem;
import com.warehouse.enums.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    /**
     * "Customers also bought": for orders that contained {@code productId}, the
     * other products in those same orders, ranked by how often they co-occur.
     * Only counts orders in a real (non-cancelled) state. Returns rows of
     * {@code [otherProductId, coOccurrenceCount]} ordered by count desc.
     */
    @Query("SELECT oi2.product.id, COUNT(oi2) " +
           "FROM OrderItem oi1, OrderItem oi2 " +
           "WHERE oi1.order.id = oi2.order.id " +
           "AND oi1.product.id = :productId " +
           "AND oi2.product.id <> :productId " +
           "AND oi1.order.status IN :statuses " +
           "AND oi2.product.id IS NOT NULL " +
           "GROUP BY oi2.product.id " +
           "ORDER BY COUNT(oi2) DESC")
    List<Object[]> findFrequentlyBoughtTogether(@Param("productId") Long productId,
                                                @Param("statuses") Collection<OrderStatus> statuses,
                                                Pageable pageable);
}
