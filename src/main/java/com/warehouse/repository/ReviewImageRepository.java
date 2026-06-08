package com.warehouse.repository;

import com.warehouse.entity.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {

    List<ReviewImage> findByReviewIdOrderBySortOrderAscIdAsc(Long reviewId);

    List<ReviewImage> findByReviewIdInOrderBySortOrderAscIdAsc(List<Long> reviewIds);

    long countByReviewId(Long reviewId);
}
