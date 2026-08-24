package com.warehouse.repository;

import com.warehouse.entity.Product;
import com.warehouse.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductOrderBySortOrderAscIdAsc(Product product);

    List<ProductImage> findByProductAndAiRole(Product product, String aiRole);

    List<ProductImage> findByProductAndAiRoleAndMemberProductId(Product product, String aiRole, Long memberProductId);
    @Query("SELECT pi.product.id, COUNT(pi) FROM ProductImage pi WHERE pi.product.id IN :productIds " +
           "AND pi.slot IS NULL AND (pi.aiRole IS NULL OR pi.aiRole <> 'COVER_INPUT') GROUP BY pi.product.id")
    List<Object[]> countDisplayableByProductIds(@Param("productIds") List<Long> productIds);
}


