package com.warehouse.repository;

import com.warehouse.entity.BundleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BundleItemRepository extends JpaRepository<BundleItem, Long> {

    /** Member lines of a single set, ordered for display. */
    List<BundleItem> findByBundleIdOrderBySortOrderAsc(Long bundleId);

    /** Member counts for a batch of bundle ids → rows of [bundleId, count]. */
    @Query("SELECT bi.bundle.id, COUNT(bi) FROM BundleItem bi WHERE bi.bundle.id IN :ids GROUP BY bi.bundle.id")
    List<Object[]> countByBundleIds(@Param("ids") List<Long> ids);

    /** True if the given product is a member of at least one set (for delete guards). */
    boolean existsByProductId(Long productId);
}
