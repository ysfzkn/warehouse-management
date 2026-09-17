package com.warehouse.repository;

import com.warehouse.entity.Product;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.enums.TransferApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    @Override
    @EntityGraph(value = StockTransfer.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    Page<StockTransfer> findAll(Pageable pageable);

    /**
     * Distinct customers seen on past customer deliveries, most recent first.
     * {@code [customerFullName, customerPhone, customerAddress, lastUsedAt, deliveryCount]}
     *
     * <p>Grouped on the raw fields so the operator gets the spelling that was actually used,
     * but filtered on the normalised column so "Ballı" finds "Balli".</p>
     */
    @Query("SELECT st.customerFullName, st.customerPhone, MAX(st.customerAddress), " +
           "MAX(st.transferDate), COUNT(st) " +
           "FROM StockTransfer st " +
           "WHERE st.transferType = com.warehouse.enums.TransferType.CUSTOMER_DELIVERY " +
           "AND st.status <> com.warehouse.enums.TransferStatus.CANCELLED " +
           "AND st.customerFullName IS NOT NULL AND st.customerFullName <> '' " +
           "AND (:pattern IS NULL OR COALESCE(st.customerSearch, '') LIKE :pattern) " +
           "GROUP BY st.customerFullName, st.customerPhone " +
           "ORDER BY MAX(st.transferDate) DESC")
    List<Object[]> findDistinctCustomers(@Param("pattern") String pattern, Pageable pageable);

    /**
     * Recent customer deliveries — the candidate pool for the duplicate-delivery check.
     * Cancelled shipments are excluded (they never reached anyone); name matching happens in
     * Java, see {@link com.warehouse.util.TurkishText}.
     */
    @Query("SELECT st FROM StockTransfer st LEFT JOIN FETCH st.sourceWarehouse " +
           "WHERE st.transferType = com.warehouse.enums.TransferType.CUSTOMER_DELIVERY " +
           "AND st.status <> com.warehouse.enums.TransferStatus.CANCELLED " +
           "AND st.transferDate >= :since " +
           "ORDER BY st.transferDate DESC")
    List<StockTransfer> findRecentCustomerDeliveries(@Param("since") LocalDateTime since,
                                                     Pageable pageable);

    long countByDriverId(Long driverId);

    /**
     * Moves every transfer that pointed at a duplicate driver onto the surviving record.
     * Only the link moves — each transfer keeps its own driver name, TC, phone and plate, so
     * the history still shows what was actually written at the time.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE StockTransfer st SET st.driverId = :targetId WHERE st.driverId IN :sourceIds")
    int repointDriver(@Param("targetId") Long targetId, @Param("sourceIds") List<Long> sourceIds);

    /** Shipments created for a specific order (customer chose our own delivery). */
    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.orderId = :orderId ORDER BY st.createdAt DESC")
    List<StockTransfer> findByOrderId(@Param("orderId") Long orderId);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.sourceWarehouse = :sourceWarehouse")
    List<StockTransfer> findBySourceWarehouse(@Param("sourceWarehouse") Warehouse sourceWarehouse);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.destinationWarehouse = :destinationWarehouse")
    List<StockTransfer> findByDestinationWarehouse(@Param("destinationWarehouse") Warehouse destinationWarehouse);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product productEntity " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product itemProduct " +
           "WHERE productEntity = :product OR itemProduct = :product")
    List<StockTransfer> findByProduct(@Param("product") Product product);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.status = :status")
    List<StockTransfer> findByStatus(@Param("status") TransferStatus status);

    @EntityGraph(value = StockTransfer.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    List<StockTransfer> findByApprovalStatusOrderByTransferDateDesc(TransferApprovalStatus status);

    long countByApprovalStatus(TransferApprovalStatus status);

    /**
     * Bir depoya giren veya çıkan sevkiyatların kimlikleri, en yenisi başta.
     *
     * <p>Eski hâli koleksiyonu da fetch ederek tüm sevkiyatları tek listede döndürüyordu.
     * Otuz bin sevkiyatlık veritabanında bir depo için 3.750 kayıt, kalemleriyle birlikte
     * 13 MB gövde ve 16 saniye demekti — üstelik istek boyunca bir sunucu iş parçacığı
     * bloke oluyordu. Çağıran taraf artık kimlikleri sınırlı sayıda alıp
     * {@link #findAllWithRelationsByIdIn} ile yüklüyor.</p>
     */
    @Query("SELECT st.id FROM StockTransfer st " +
           "WHERE st.sourceWarehouse = :warehouse OR st.destinationWarehouse = :warehouse " +
           "ORDER BY st.transferDate DESC")
    List<Long> findIdsByWarehouse(@Param("warehouse") Warehouse warehouse, Pageable pageable);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.transferDate BETWEEN :startDate AND :endDate " +
           "ORDER BY st.transferDate DESC")
    List<StockTransfer> findByTransferDateBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product productEntity " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product itemProduct " +
           "WHERE (productEntity = :product OR itemProduct = :product) AND st.status IN ('PENDING', 'IN_TRANSIT')")
    List<StockTransfer> findActiveTransfersByProduct(@Param("product") Product product);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "ORDER BY st.transferDate DESC")
    List<StockTransfer> findAllOrderByTransferDateDesc();

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.sourceWarehouse = :sourceWarehouse AND st.status = :status")
    List<StockTransfer> findBySourceWarehouseAndStatus(@Param("sourceWarehouse") Warehouse sourceWarehouse,
                                                        @Param("status") TransferStatus status);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.destinationWarehouse = :destinationWarehouse AND st.status = :status")
    List<StockTransfer> findByDestinationWarehouseAndStatus(@Param("destinationWarehouse") Warehouse destinationWarehouse,
                                                             @Param("status") TransferStatus status);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.id = :id")
    java.util.Optional<StockTransfer> findByIdWithRelations(@Param("id") Long id);

    @Query("SELECT DISTINCT st FROM StockTransfer st " +
           "LEFT JOIN FETCH st.sourceWarehouse " +
           "LEFT JOIN FETCH st.destinationWarehouse " +
           "LEFT JOIN FETCH st.product " +
           "LEFT JOIN FETCH st.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE st.createdBy = :createdBy " +
           "ORDER BY st.transferDate DESC")
    List<StockTransfer> findAllByCreatedByOrderByTransferDateDesc(@Param("createdBy") String createdBy);

    /**
     * Filtrelere uyan sevkiyatların yalnızca kimlikleri, sayfalanmış.
     *
     * <p>Burada bilerek koleksiyon çekilmiyor. Sorgu {@code items} koleksiyonunu da fetch
     * ettiğinde Hibernate LIMIT/OFFSET kullanamıyor: bir satır JOIN sonucunda birden çok
     * satıra dönüştüğü için SQL'de "20 kayıt" demek "20 sevkiyat" demek değil. Hibernate bu
     * durumda tüm sonucu belleğe çekip sayfalamayı Java'da yapıyor ve bunu yalnızca bir
     * uyarı satırıyla bildiriyor (HHH90003004). Otuz bin sevkiyatlık bir veritabanında tek
     * sayfa isteği 68 saniye sürüyordu; kayıt sayısı arttıkça doğrusal olarak kötüleşiyor.</p>
     *
     * <p>İki aşamalı çözüm: burada gerçek LIMIT/OFFSET ile sayfanın kimlikleri alınıyor,
     * ardından {@link #findAllWithRelationsByIdIn} o yirmi kimliği ilişkileriyle birlikte
     * tek sorguda yüklüyor. Sonuç aynı, N+1 yok, bellekte sayfalama yok.</p>
     */
    @Query(value = """
        SELECT st.id FROM StockTransfer st
        LEFT JOIN st.product directProduct
        WHERE (:createdBy IS NULL OR st.createdBy = :createdBy)
          AND (:status IS NULL OR st.status = :status)
          AND (
                :scheduledOnly = false
                OR (st.scheduledDeliveryAt IS NOT NULL
                    AND st.status IN (com.warehouse.enums.TransferStatus.PENDING,
                                     com.warehouse.enums.TransferStatus.IN_TRANSIT))
          )
          AND (:transferType IS NULL OR st.transferType = :transferType)
          AND (:sourceWarehouseId IS NULL OR st.sourceWarehouse.id = :sourceWarehouseId)
          AND (:destinationWarehouseId IS NULL OR st.destinationWarehouse.id = :destinationWarehouseId)
          AND (st.transferDate >= COALESCE(:startDate, st.transferDate))
          AND (st.transferDate <= COALESCE(:endDate, st.transferDate))
          AND (:driverProvided = false OR COALESCE(st.driverSearch, '') LIKE :driverPattern)
          AND (
                :productNameProvided = false
                OR LOWER(COALESCE(directProduct.name, '')) LIKE :productNamePattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem item
                    WHERE item.transfer = st
                      AND LOWER(COALESCE(item.product.name, '')) LIKE :productNamePattern
                )
          )
          AND (
                :skuProvided = false
                OR LOWER(COALESCE(st.product.sku, '')) LIKE :skuPattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem itemSku
                    WHERE itemSku.transfer = st
                      AND LOWER(COALESCE(itemSku.product.sku, '')) LIKE :skuPattern
                )
          )
          AND (
                :notesProvided = false
                OR LOWER(COALESCE(st.notes, '')) LIKE :notesPattern
          )
          AND (
                :customerProvided = false
                OR COALESCE(st.customerSearch, '') LIKE :customerNamePattern
                OR COALESCE(st.customerSearch, '') LIKE :customerPhonePattern
                OR COALESCE(st.driverSearch, '') LIKE :customerNamePattern
                OR LOWER(COALESCE(st.createdBy, '')) LIKE :customerNamePattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem itemCustomer
                    WHERE itemCustomer.transfer = st
                      AND itemCustomer.stockId IS NOT NULL
                      AND EXISTS (
                          SELECT 1 FROM Stock s
                          WHERE s.id = itemCustomer.stockId
                            AND (
                                COALESCE(s.customerSearch, '') LIKE :customerNamePattern
                                OR COALESCE(s.customerSearch, '') LIKE :customerPhonePattern
                            )
                      )
                )
          )
          AND st.transferDate >= :transferDateFrom
          AND st.transferDate <= :transferDateTo
          AND st.createdAt >= :createdAtFrom
          AND st.createdAt <= :createdAtTo
        ORDER BY st.transferDate DESC
    """,
           countQuery = """
        SELECT COUNT(st.id) FROM StockTransfer st
        LEFT JOIN st.product directProduct
        WHERE (:createdBy IS NULL OR st.createdBy = :createdBy)
          AND (:status IS NULL OR st.status = :status)
          AND (
                :scheduledOnly = false
                OR (st.scheduledDeliveryAt IS NOT NULL
                    AND st.status IN (com.warehouse.enums.TransferStatus.PENDING,
                                     com.warehouse.enums.TransferStatus.IN_TRANSIT))
          )
          AND (:transferType IS NULL OR st.transferType = :transferType)
          AND (:sourceWarehouseId IS NULL OR st.sourceWarehouse.id = :sourceWarehouseId)
          AND (:destinationWarehouseId IS NULL OR st.destinationWarehouse.id = :destinationWarehouseId)
          AND (st.transferDate >= COALESCE(:startDate, st.transferDate))
          AND (st.transferDate <= COALESCE(:endDate, st.transferDate))
          AND (:driverProvided = false OR COALESCE(st.driverSearch, '') LIKE :driverPattern)
          AND (
                :productNameProvided = false
                OR LOWER(COALESCE(directProduct.name, '')) LIKE :productNamePattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem item
                    WHERE item.transfer = st
                      AND LOWER(COALESCE(item.product.name, '')) LIKE :productNamePattern
                )
          )
          AND (
                :skuProvided = false
                OR LOWER(COALESCE(st.product.sku, '')) LIKE :skuPattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem itemSku
                    WHERE itemSku.transfer = st
                      AND LOWER(COALESCE(itemSku.product.sku, '')) LIKE :skuPattern
                )
          )
          AND (
                :notesProvided = false
                OR LOWER(COALESCE(st.notes, '')) LIKE :notesPattern
          )
          AND (
                :customerProvided = false
                OR COALESCE(st.customerSearch, '') LIKE :customerNamePattern
                OR COALESCE(st.customerSearch, '') LIKE :customerPhonePattern
                OR COALESCE(st.driverSearch, '') LIKE :customerNamePattern
                OR LOWER(COALESCE(st.createdBy, '')) LIKE :customerNamePattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem itemCustomer
                    WHERE itemCustomer.transfer = st
                      AND itemCustomer.stockId IS NOT NULL
                      AND EXISTS (
                          SELECT 1 FROM Stock s
                          WHERE s.id = itemCustomer.stockId
                            AND (
                                COALESCE(s.customerSearch, '') LIKE :customerNamePattern
                                OR COALESCE(s.customerSearch, '') LIKE :customerPhonePattern
                            )
                      )
                )
          )
          AND st.transferDate >= :transferDateFrom
          AND st.transferDate <= :transferDateTo
          AND st.createdAt >= :createdAtFrom
          AND st.createdAt <= :createdAtTo
    """)

    Page<Long> findIdsByFilters(@Param("createdBy") String createdBy,
                                      @Param("status") TransferStatus status,
                                      @Param("scheduledOnly") boolean scheduledOnly,
                                      @Param("transferType") TransferType transferType,
                                      @Param("sourceWarehouseId") Long sourceWarehouseId,
                                      @Param("destinationWarehouseId") Long destinationWarehouseId,
                                      @Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate,
                                      @Param("driverProvided") boolean driverProvided,
                                      @Param("driverPattern") String driverPattern,
                                      @Param("productNameProvided") boolean productNameProvided,
                                      @Param("productNamePattern") String productNamePattern,
                                      @Param("skuProvided") boolean skuProvided,
                                      @Param("skuPattern") String skuPattern,
                                      @Param("notesProvided") boolean notesProvided,
                                      @Param("notesPattern") String notesPattern,
                                      @Param("customerProvided") boolean customerProvided,
                                      @Param("customerNamePattern") String customerNamePattern,
                                      @Param("customerPhonePattern") String customerPhonePattern,
                                      @Param("transferDateFrom") java.time.LocalDateTime transferDateFrom,
                                      @Param("transferDateTo") java.time.LocalDateTime transferDateTo,
                                      @Param("createdAtFrom") java.time.LocalDateTime createdAtFrom,
                                      @Param("createdAtTo") java.time.LocalDateTime createdAtTo,
                                      Pageable pageable);

    /**
     * Verilen kimliklerin ilişkileriyle birlikte yüklenmesi.
     *
     * <p>{@code IN} listesi sıralamayı korumuyor; çağıran taraf kimlik sırasına göre yeniden
     * diziyor. Sıralama sorgunun kendisine bırakılsaydı, sayfa içindeki sıra sessizce
     * değişirdi.</p>
     */
    @EntityGraph(value = StockTransfer.GRAPH_WITH_RELATIONS, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT DISTINCT st FROM StockTransfer st WHERE st.id IN :ids")
    List<StockTransfer> findAllWithRelationsByIdIn(@Param("ids") List<Long> ids);

    @Query("""
        SELECT st.status AS status,
               CASE WHEN st.scheduledDeliveryAt IS NOT NULL
                         AND st.status IN (com.warehouse.enums.TransferStatus.PENDING,
                                           com.warehouse.enums.TransferStatus.IN_TRANSIT)
                    THEN 1 ELSE 0 END AS scheduled,
               COUNT(DISTINCT st.id) AS count
        FROM StockTransfer st
        LEFT JOIN st.product directProduct
        WHERE (:createdBy IS NULL OR st.createdBy = :createdBy)
          AND (:status IS NULL OR st.status = :status)
          AND (
                :scheduledOnly = false
                OR (st.scheduledDeliveryAt IS NOT NULL
                    AND st.status IN (com.warehouse.enums.TransferStatus.PENDING,
                                     com.warehouse.enums.TransferStatus.IN_TRANSIT))
          )
          AND (:transferType IS NULL OR st.transferType = :transferType)
          AND (:sourceWarehouseId IS NULL OR st.sourceWarehouse.id = :sourceWarehouseId)
          AND (:destinationWarehouseId IS NULL OR st.destinationWarehouse.id = :destinationWarehouseId)
          AND (st.transferDate >= COALESCE(:startDate, st.transferDate))
          AND (st.transferDate <= COALESCE(:endDate, st.transferDate))
          AND (:driverProvided = false OR COALESCE(st.driverSearch, '') LIKE :driverPattern)
          AND (
                :productNameProvided = false
                OR LOWER(COALESCE(directProduct.name, '')) LIKE :productNamePattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem item
                    WHERE item.transfer = st
                      AND LOWER(COALESCE(item.product.name, '')) LIKE :productNamePattern
                )
          )
          AND (
                :skuProvided = false
                OR LOWER(COALESCE(directProduct.sku, '')) LIKE :skuPattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem itemSku
                    WHERE itemSku.transfer = st
                      AND LOWER(COALESCE(itemSku.product.sku, '')) LIKE :skuPattern
                )
          )
          AND (
                :customerProvided = false
                OR COALESCE(st.customerSearch, '') LIKE :customerNamePattern
                OR COALESCE(st.customerSearch, '') LIKE :customerPhonePattern
                OR COALESCE(st.driverSearch, '') LIKE :customerNamePattern
                OR LOWER(COALESCE(st.createdBy, '')) LIKE :customerNamePattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem itemCustomer
                    WHERE itemCustomer.transfer = st
                      AND itemCustomer.stockId IS NOT NULL
                      AND EXISTS (
                          SELECT 1 FROM Stock s
                          WHERE s.id = itemCustomer.stockId
                            AND (
                                COALESCE(s.customerSearch, '') LIKE :customerNamePattern
                                OR COALESCE(s.customerSearch, '') LIKE :customerPhonePattern
                            )
                      )
                )
          )
        GROUP BY st.status,
                 CASE WHEN st.scheduledDeliveryAt IS NOT NULL
                           AND st.status IN (com.warehouse.enums.TransferStatus.PENDING,
                                             com.warehouse.enums.TransferStatus.IN_TRANSIT)
                      THEN 1 ELSE 0 END
    """)
    List<StatusCountProjection> countByFiltersGroupedStatus(@Param("createdBy") String createdBy,
                                                            @Param("status") TransferStatus status,
                                                            @Param("scheduledOnly") boolean scheduledOnly,
                                                            @Param("transferType") TransferType transferType,
                                                            @Param("sourceWarehouseId") Long sourceWarehouseId,
                                                            @Param("destinationWarehouseId") Long destinationWarehouseId,
                                                            @Param("startDate") LocalDateTime startDate,
                                                            @Param("endDate") LocalDateTime endDate,
                                                            @Param("driverProvided") boolean driverProvided,
                                                            @Param("driverPattern") String driverPattern,
                                                            @Param("productNameProvided") boolean productNameProvided,
                                                            @Param("productNamePattern") String productNamePattern,
                                                            @Param("skuProvided") boolean skuProvided,
                                                            @Param("skuPattern") String skuPattern,
                                                            @Param("customerProvided") boolean customerProvided,
                                                            @Param("customerNamePattern") String customerNamePattern,
                                                            @Param("customerPhonePattern") String customerPhonePattern);

    @Query("""
        SELECT st.transferType AS transferType, COUNT(DISTINCT st.id) AS count FROM StockTransfer st
        LEFT JOIN st.product directProduct
        WHERE (:createdBy IS NULL OR st.createdBy = :createdBy)
          AND (:status IS NULL OR st.status = :status)
          AND (
                :scheduledOnly = false
                OR (st.scheduledDeliveryAt IS NOT NULL
                    AND st.status IN (com.warehouse.enums.TransferStatus.PENDING,
                                     com.warehouse.enums.TransferStatus.IN_TRANSIT))
          )
          AND (:transferType IS NULL OR st.transferType = :transferType)
          AND (:sourceWarehouseId IS NULL OR st.sourceWarehouse.id = :sourceWarehouseId)
          AND (:destinationWarehouseId IS NULL OR st.destinationWarehouse.id = :destinationWarehouseId)
          AND (st.transferDate >= COALESCE(:startDate, st.transferDate))
          AND (st.transferDate <= COALESCE(:endDate, st.transferDate))
          AND (:driverProvided = false OR COALESCE(st.driverSearch, '') LIKE :driverPattern)
          AND (
                :productNameProvided = false
                OR LOWER(COALESCE(directProduct.name, '')) LIKE :productNamePattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem item
                    WHERE item.transfer = st
                      AND LOWER(COALESCE(item.product.name, '')) LIKE :productNamePattern
                )
          )
          AND (
                :skuProvided = false
                OR LOWER(COALESCE(directProduct.sku, '')) LIKE :skuPattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem itemSku
                    WHERE itemSku.transfer = st
                      AND LOWER(COALESCE(itemSku.product.sku, '')) LIKE :skuPattern
                )
          )
          AND (
                :customerProvided = false
                OR COALESCE(st.customerSearch, '') LIKE :customerNamePattern
                OR COALESCE(st.customerSearch, '') LIKE :customerPhonePattern
                OR COALESCE(st.driverSearch, '') LIKE :customerNamePattern
                OR LOWER(COALESCE(st.createdBy, '')) LIKE :customerNamePattern
                OR EXISTS (
                    SELECT 1 FROM StockTransferItem itemCustomer
                    WHERE itemCustomer.transfer = st
                      AND itemCustomer.stockId IS NOT NULL
                      AND EXISTS (
                          SELECT 1 FROM Stock s
                          WHERE s.id = itemCustomer.stockId
                            AND (
                                COALESCE(s.customerSearch, '') LIKE :customerNamePattern
                                OR COALESCE(s.customerSearch, '') LIKE :customerPhonePattern
                            )
                      )
                )
          )
        GROUP BY st.transferType
    """)
    List<TransferTypeCountProjection> countByFiltersGroupedTransferType(@Param("createdBy") String createdBy,
                                                                        @Param("status") TransferStatus status,
                                                                        @Param("scheduledOnly") boolean scheduledOnly,
                                                                        @Param("transferType") TransferType transferType,
                                                                        @Param("sourceWarehouseId") Long sourceWarehouseId,
                                                                        @Param("destinationWarehouseId") Long destinationWarehouseId,
                                                                        @Param("startDate") LocalDateTime startDate,
                                                                        @Param("endDate") LocalDateTime endDate,
                                                                        @Param("driverProvided") boolean driverProvided,
                                                                        @Param("driverPattern") String driverPattern,
                                                                        @Param("productNameProvided") boolean productNameProvided,
                                                                        @Param("productNamePattern") String productNamePattern,
                                                                        @Param("skuProvided") boolean skuProvided,
                                                                        @Param("skuPattern") String skuPattern,
                                                                        @Param("customerProvided") boolean customerProvided,
                                                                        @Param("customerNamePattern") String customerNamePattern,
                                                                        @Param("customerPhonePattern") String customerPhonePattern);

    /**
     * Planı olan ve hâlâ kapanmamış sevkiyatlar — hatırlatma job'ının çalışma kümesi.
     *
     * <p>Tarih filtresi bilerek yok: job hangi aşamanın geldiğini kendisi hesaplıyor ve
     * kaçırılmış aşamaları da kapatması gerekiyor. "Yarını sorgula" biçiminde bir filtre,
     * uygulamanın bir gün kapalı kaldığı her durumda o günün hatırlatmalarını sessizce
     * kaybederdi. Küme küçük kalıyor: tamamlanan ve iptal edilen kayıtlar dışarıda ve
     * kısmi indeks tam da bunu karşılıyor.</p>
     *
     * <p>Kalemler ve depo eagerly yükleniyor: hatırlatma metni ürün dökümünü yazıyor ve
     * job bir işlem dışında çalıştığı için lazy koleksiyon orada açılamaz.</p>
     */
    @Query("SELECT DISTINCT st FROM StockTransfer st "
            + "LEFT JOIN FETCH st.sourceWarehouse "
            + "LEFT JOIN FETCH st.items i "
            + "LEFT JOIN FETCH i.product "
            + "WHERE st.scheduledDeliveryAt IS NOT NULL "
            + "AND st.status IN (com.warehouse.enums.TransferStatus.PENDING, "
            + "                  com.warehouse.enums.TransferStatus.IN_TRANSIT) "
            + "ORDER BY st.scheduledDeliveryAt ASC")
    List<StockTransfer> findOpenScheduledDeliveries();

    /**
     * Durum sayacı, planlı teslimatlar ayrı bir kova olacak şekilde.
     *
     * <p>Planlı bir sevkiyatın durumu IN_TRANSIT ("rezerve tutuluyor") ama mal depoda.
     * Ekrandaki "Yolda" sayacı onları da sayarsa, aynı kayıt listede "Planlandı" rozetiyle
     * görünürken sayaçta "Yolda" olarak sayılır — iki ekran aynı kaydı iki farklı şey
     * sanardı.</p>
     *
     * <p>Ayrı bir sayım sorgusu yerine aynı sorguya ikinci bir gruplama ekseni eklendi:
     * bu dosyadaki WHERE koşulunun zaten üç kopyası var ve dördüncüsü zamanla
     * diğerlerinden sessizce ayrışırdı.</p>
     */
    interface StatusCountProjection {
        TransferStatus getStatus();
        /** 1 = planı olan ve hâlâ açık sevkiyat; 0 = diğerleri. */
        int getScheduled();
        long getCount();
    }

    interface TransferTypeCountProjection {
        TransferType getTransferType();
        long getCount();
    }
}
