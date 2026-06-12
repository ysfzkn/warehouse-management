package com.warehouse.repository;

import com.warehouse.entity.Product;
import com.warehouse.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductOrderBySortOrderAscIdAsc(Product product);

    List<ProductImage> findByProductAndAiRole(Product product, String aiRole);

    List<ProductImage> findByProductAndAiRoleAndMemberProductId(Product product, String aiRole, Long memberProductId);
}


