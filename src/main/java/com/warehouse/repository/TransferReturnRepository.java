package com.warehouse.repository;

import com.warehouse.entity.TransferReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransferReturnRepository extends JpaRepository<TransferReturn, Long> {

    /**
     * Newest first — the panel shows the latest return at the top, and the items are fetched
     * along with it so rendering the history does not fan out into a query per return.
     */
    @Query("""
        SELECT DISTINCT r FROM TransferReturn r
          LEFT JOIN FETCH r.items i
          LEFT JOIN FETCH i.transferItem ti
          LEFT JOIN FETCH ti.product
         WHERE r.transfer.id = :transferId
         ORDER BY r.returnedAt DESC, r.id DESC
    """)
    List<TransferReturn> findByTransferId(Long transferId);
}
