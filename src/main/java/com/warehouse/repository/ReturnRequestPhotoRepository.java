package com.warehouse.repository;

import com.warehouse.entity.ReturnRequestPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReturnRequestPhotoRepository extends JpaRepository<ReturnRequestPhoto, Long> {
    List<ReturnRequestPhoto> findByReturnRequestIdOrderByIdAsc(Long returnRequestId);
    long countByReturnRequestId(Long returnRequestId);
}
