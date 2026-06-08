package com.warehouse.repository;

import com.warehouse.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findByProductIdAndApprovedTrue(Long productId, Pageable pageable);
    Page<Review> findByProductIdAndApprovedTrueAndVisibleTrue(Long productId, Pageable pageable);
    Page<Review> findByCustomerId(Long customerId, Pageable pageable);
    java.util.List<Review> findAllByCustomerId(Long customerId);
    Page<Review> findByApproved(boolean approved, Pageable pageable);
    boolean existsByCustomerIdAndProductIdAndOrderId(Long customerId, Long productId, Long orderId);

    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);
    java.util.Optional<Review> findFirstByCustomerIdAndProductIdOrderByCreatedAtDesc(Long customerId, Long productId);
    long countByApproved(boolean approved);

    /** Star distribution for a product's approved reviews → rows of [rating, count]. */
    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.product.id = :productId AND r.approved = true GROUP BY r.rating")
    java.util.List<Object[]> ratingDistribution(@Param("productId") Long productId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.approved = true")
    Double overallAverage();

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId AND r.approved = true")
    Double getAverageRatingByProductId(Long productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.product.id = :productId AND r.approved = true")
    long countApprovedByProductId(Long productId);

    /**
     * Batch — average rating + approved review count for multiple products in a single query.
     * Prevents N+1 (on the product list page). Returned Object[]: [productId, avgRating, count].
     */
    @Query("SELECT r.product.id, AVG(r.rating), COUNT(r) " +
           "FROM Review r WHERE r.product.id IN :productIds AND r.approved = true " +
           "GROUP BY r.product.id")
    java.util.List<Object[]> getRatingStatsForProducts(@Param("productIds") java.util.List<Long> productIds);
}
