package com.warehouse.repository;

import com.warehouse.entity.CmsPage;
import com.warehouse.enums.CmsPageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CmsPageRepository extends JpaRepository<CmsPage, Long> {
    Optional<CmsPage> findBySlugAndActiveTrue(String slug);
    List<CmsPage> findByPageTypeAndActiveTrue(CmsPageType pageType);

    /** For sitemap generation: all active pages (CONTENT/LEGAL types; excluding BANNER). */
    @Query("SELECT c FROM CmsPage c WHERE c.active = true AND c.pageType <> 'BANNER'")
    List<CmsPage> findByIsPublishedTrue();

    @Query("SELECT c FROM CmsPage c WHERE c.pageType = 'BANNER' AND c.active = true " +
           "AND (c.bannerPosition = :position OR :position IS NULL) " +
           "AND (c.bannerStart IS NULL OR c.bannerStart <= :now) " +
           "AND (c.bannerEnd IS NULL OR c.bannerEnd >= :now) " +
           "ORDER BY c.sortOrder")
    List<CmsPage> findActiveBanners(@Param("position") String position, @Param("now") LocalDateTime now);
}
