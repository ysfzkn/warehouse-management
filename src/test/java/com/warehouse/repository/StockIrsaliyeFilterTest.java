package com.warehouse.repository;

import com.warehouse.entity.Category;
import com.warehouse.entity.Product;
import com.warehouse.entity.Stock;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.WarehouseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The waybill filter on the stock screen.
 *
 * <p>Two things can only fail at runtime and so are pinned here: that the JPQL with the new
 * parameters actually parses and runs, and that a number is found however the operator punctuated
 * it — the whole point of storing a folded key next to the number as typed.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class StockIrsaliyeFilterTest {

    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Warehouse warehouse;
    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Beyaz Eşya");
        category.setSlug("beyaz-esya");
        category = categoryRepository.save(category);

        warehouse = new Warehouse();
        warehouse.setName("Merkez");
        warehouse.setLocation("İzmir");
        warehouse.setWarehouseType(WarehouseType.STANDART);
        warehouse = warehouseRepository.save(warehouse);
    }

    private Stock stockWith(String sku, String irsaliyeNo, LocalDate irsaliyeDate) {
        Product product = new Product();
        product.setName("Ürün " + sku);
        product.setSku(sku);
        product.setSlug("urun-" + sku.toLowerCase());
        product.setCategory(category);
        product = productRepository.save(product);

        Stock stock = new Stock(product, warehouse, 10);
        stock.setIrsaliyeNo(irsaliyeNo);
        stock.setIrsaliyeDate(irsaliyeDate);
        return stockRepository.save(stock);
    }

    /** Runs the query with every waybill parameter set, which is what catches a JPQL mistake. */
    private Page<Stock> search(String irsaliyeKeyPattern, LocalDate from, LocalDate to) {
        return stockRepository.findByFilters(
                null, null, null, List.of(0L), false, null, null,
                false, "%", "%", "%",
                irsaliyeKeyPattern, from, to,
                false, false, false, "ALL",
                LocalDateTime.of(1970, 1, 1, 0, 0), LocalDateTime.of(2099, 12, 31, 23, 59),
                PAGE);
    }

    @Test
    void storesTheNumberAsTypedAndTheKeyFolded() {
        Stock saved = stockWith("SKU-1", "abc 2026-14", LocalDate.of(2026, 3, 1));
        assertThat(saved.getIrsaliyeNo()).isEqualTo("abc 2026-14");
        assertThat(saved.getIrsaliyeKey()).isEqualTo("ABC202614");
    }

    @Test
    void findsAWaybillHoweverItWasPunctuated() {
        stockWith("SKU-1", "ABC-2026-14", LocalDate.of(2026, 3, 1));
        stockWith("SKU-2", "XYZ/2026/99", LocalDate.of(2026, 3, 2));

        assertThat(search("%ABC202614%", null, null).getContent())
                .extracting(s -> s.getProduct().getSku())
                .containsExactly("SKU-1");
    }

    @Test
    void noWaybillFilterReturnsEverything() {
        stockWith("SKU-1", "ABC-2026-14", LocalDate.of(2026, 3, 1));
        stockWith("SKU-2", null, null);

        assertThat(search(null, null, null).getTotalElements()).isEqualTo(2);
    }

    @Test
    void datesBoundTheWaybillDateInclusively() {
        stockWith("SKU-1", "A1", LocalDate.of(2026, 3, 1));
        stockWith("SKU-2", "A2", LocalDate.of(2026, 3, 5));
        stockWith("SKU-3", "A3", LocalDate.of(2026, 3, 9));

        assertThat(search(null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5)).getContent())
                .extracting(s -> s.getProduct().getSku())
                .containsExactlyInAnyOrder("SKU-1", "SKU-2");
    }

    /** A row with no waybill cannot satisfy a date bound, and must not leak into the results. */
    @Test
    void rowsWithoutAWaybillAreExcludedByADateFilter() {
        stockWith("SKU-1", "A1", LocalDate.of(2026, 3, 1));
        stockWith("SKU-2", null, null);

        assertThat(search(null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).getContent())
                .extracting(s -> s.getProduct().getSku())
                .containsExactly("SKU-1");
    }

    /** Re-saving with a new number has to move the key too, or the old one keeps answering. */
    @Test
    void correctingTheNumberMovesTheSearchKeyWithIt() {
        Stock saved = stockWith("SKU-1", "ABC-2026-14", LocalDate.of(2026, 3, 1));

        saved.setIrsaliyeNo("XYZ/2026/99");
        stockRepository.saveAndFlush(saved);

        assertThat(search("%ABC202614%", null, null).getContent()).isEmpty();
        assertThat(search("%XYZ202699%", null, null).getContent())
                .extracting(s -> s.getProduct().getSku())
                .containsExactly("SKU-1");
    }

    // ─── Waybill summary (type-ahead) ────────────────────────────────────────

    private List<StockRepository.IrsaliyeSummary> summarize(String pattern) {
        return stockRepository.summarizeIrsaliye(pattern, PAGE);
    }

    /**
     * The waybill date must reach the database as the date that was typed.
     *
     * <p>It did not, on any JVM running outside Europe/Istanbul: the day came back one
     * earlier. Reading the entity back applied the same shift in reverse, so nothing
     * looked wrong until an aggregate ({@code MAX}) returned the raw column.</p>
     *
     * <p>The reason it only bit CI: this is a slice test, and {@code @DataJpaTest} never
     * creates the application bean, so the {@code TimeZone.setDefault(Europe/Istanbul)}
     * in {@code WarehouseManagementApplication} does not run and the JVM keeps the
     * machine's zone — Istanbul on the developer's box, UTC on the runner. The fix pins
     * the zone for the test JVM in the Surefire configuration; this assertion reads the
     * stored value directly so a drift fails here rather than in a distant aggregate.</p>
     */
    @Test
    void theWaybillDateIsStoredAsTheDayItWasEntered() {
        Stock saved = stockWith("SKU-TZ", "TZ-2026-1", LocalDate.of(2026, 3, 1));
        stockRepository.flush();

        Object raw = entityManager
                .createNativeQuery("SELECT irsaliye_date FROM stocks WHERE id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();

        assertThat(raw.toString())
                .as("veritabanindaki ham deger, girilen gunle ayni olmali "
                        + "(JVM zonu ile hibernate.jdbc.time_zone ayrisirsa kayar)")
                .startsWith("2026-03-01");
    }

    @Test
    void summaryAddsUpTheRowsOfOneDelivery() {
        stockWith("SKU-1", "ABC-2026-14", LocalDate.of(2026, 3, 1)).setQuantity(10);
        stockWith("SKU-2", "ABC 2026 14", LocalDate.of(2026, 3, 1));
        stockWith("SKU-3", "XYZ/2026/99", LocalDate.of(2026, 3, 2));

        List<StockRepository.IrsaliyeSummary> all = summarize(null);

        assertThat(all).hasSize(2);
        StockRepository.IrsaliyeSummary abc = all.stream()
                .filter(r -> "ABC202614".equals(Stock.toIrsaliyeKey(r.getIrsaliyeNo())))
                .findFirst()
                .orElseThrow();
        // Two rows entered with different punctuation are one delivery, not two.
        assertThat(abc.getStockCount()).isEqualTo(2);
        assertThat(abc.getTotalQuantity()).isEqualTo(20);
        assertThat(abc.getIrsaliyeDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void summaryMatchesRegardlessOfHowTheQueryIsPunctuated() {
        stockWith("SKU-1", "ABC-2026-14", LocalDate.of(2026, 3, 1));
        stockWith("SKU-2", "XYZ/2026/99", LocalDate.of(2026, 3, 2));

        assertThat(summarize("%ABC2026%"))
                .singleElement()
                .satisfies(r -> assertThat(Stock.toIrsaliyeKey(r.getIrsaliyeNo())).isEqualTo("ABC202614"));
    }

    @Test
    void summarySkipsRowsWithoutAWaybill() {
        stockWith("SKU-1", null, null);

        assertThat(summarize(null)).isEmpty();
    }

    /** Clearing the number must clear the key, otherwise a deleted waybill still matches. */
    @Test
    void clearingTheNumberClearsTheSearchKey() {
        Stock saved = stockWith("SKU-1", "ABC-2026-14", LocalDate.of(2026, 3, 1));

        saved.setIrsaliyeNo(null);
        stockRepository.saveAndFlush(saved);

        assertThat(saved.getIrsaliyeKey()).isNull();
        assertThat(search("%ABC202614%", null, null).getContent()).isEmpty();
    }
}
